package pl.wojczal.ryciny.rails

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pl.wojczal.ryciny.data.Http
import pl.wojczal.ryciny.data.LatLon
import pl.wojczal.ryciny.data.Place
import pl.wojczal.ryciny.data.SettingsStore
import pl.wojczal.ryciny.data.json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/** A scheduled train passing the house today, at the point of its route nearest to it. */
@Serializable
data class TrainPass(
    val passSec: Int,
    val agency: String,
    val number: String,
    val name: String,
    val from: String,
    val to: String,
    val between: String,
    val distKm: Float,
) {
    val title: String get() = listOf(agency, number, name).filter { it.isNotBlank() }.joinToString(" ")
    val route: String get() = "$from → $to"
    val hhmm: String get() = "%02d:%02d".format(passSec / 3600, passSec % 3600 / 60)
}

data class RailsState(
    val date: String = "",
    val place: String = "",
    val passes: List<TrainPass> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Which train the microphone just heard, from the timetable of every Polish train (SKM Trójmiasto,
 * PolRegio, Intercity...): mkuran.pl's GTFS feed built from PKP PLK's open data. The 30 MB feed is
 * fetched at most every few days; each day the trains whose route passes within [Timetable.MAX_KM] of the
 * house are worked out once and kept, so a heard train is matched against a list of a hundred.
 * Delays are not applied: the live feed is 23 MB per poll, far too much for a phone.
 */
class Rails(private val context: Context, private val settings: SettingsStore, private val place: Place, scope: CoroutineScope) {
    private val dir = File(context.filesDir, "rails").apply { mkdirs() }
    private val feed = File(dir, "polish_trains.zip")
    private val lock = Mutex()
    private val state = MutableStateFlow(RailsState())
    val flow: StateFlow<RailsState> = state

    init {
        // Right after start the place is the Gdańsk fallback until GPS answers: recount as soon as it moves.
        scope.launch(Dispatchers.IO) {
            combine(place.flow, place.resolved) { p, ready -> if (ready) "${Math.round(p.lat * 100)}-${Math.round(p.lon * 100)}" else null }
                .filterNotNull().distinctUntilChanged()
                .collect { runCatching { ensureToday() } }
        }
        scope.launch(Dispatchers.IO) {
            while (true) {
                delay(30 * 60_000L)
                runCatching { ensureToday() } // a new day
            }
        }
    }

    /** The scheduled train nearest in time to [at], within [WINDOW_SEC] either side. */
    fun match(at: Long): TrainPass? {
        val now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(at), ZONE)
        val sec = now.toLocalTime().toSecondOfDay()
        val best = state.value.passes.minByOrNull { abs(it.passSec - sec) }
        Log.i(TAG, "match $now: ${state.value.passes.size} passes, nearest ${best?.hhmm} ${best?.title}")
        return best?.takeIf { abs(it.passSec - sec) <= WINDOW_SEC }
    }

    suspend fun ensureToday(force: Boolean = false) = lock.withLock {
        if (!settings.value.trains) { Log.i(TAG, "trains off"); return@withLock }
        if (settings.value.source == "server") return@withLock // the add-on matches trains itself
        if (!place.resolved.value) { Log.i(TAG, "waiting for a location"); return@withLock }
        val today = LocalDate.now(ZONE)
        val key = today.format(DateTimeFormatter.BASIC_ISO_DATE)
        val here = place.value
        val spot = "${Math.round(here.lat * 100)}-${Math.round(here.lon * 100)}"
        val index = File(dir, "passes-$key-$spot.json")
        if (!force && state.value.date == key && state.value.place == spot) return@withLock
        Log.i(TAG, "ensureToday $key spot=$spot gps=${here.fromGps} force=$force")
        if (!force && index.exists()) {
            state.value = RailsState(key, spot, json.decodeFromString(ListSerializer(TrainPass.serializer()), index.readText()))
            Log.i(TAG, "loaded ${state.value.passes.size} passes from ${index.name}")
            return@withLock
        }
        val stale = !feed.exists() || System.currentTimeMillis() - feed.lastModified() > FEED_MAX_AGE_MS
        val metered = metered()
        state.value = state.value.copy(busy = true, error = null)
        try {
            // Without any timetable the 30 MB comes over whatever network there is: some phones call their
            // home Wi-Fi metered. Only the refresh every few days waits for an unmetered one.
            if (force || !feed.exists() || (stale && !metered)) download()
            else if (stale) Log.i(TAG, "feed is stale, waiting for an unmetered network")
            val started = System.currentTimeMillis()
            val passes = Timetable.passes(feed, key, here.lat, here.lon)
            Log.i(TAG, "built ${passes.size} passes in ${System.currentTimeMillis() - started} ms")
            index.writeText(json.encodeToString(ListSerializer(TrainPass.serializer()), passes))
            dir.listFiles()?.filter { it.name.startsWith("passes-") && it != index }?.forEach { it.delete() }
            state.value = RailsState(key, spot, passes)
        } catch (e: Exception) {
            Log.e(TAG, "timetable failed", e)
            state.value = state.value.copy(busy = false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun metered(): Boolean = runCatching {
        context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
    }.getOrDefault(true)

    private suspend fun download() {
        val tmp = File(dir, "polish_trains.zip.tmp")
        Log.i(TAG, "downloading $FEED_URL")
        tmp.writeBytes(Http.get(FEED_URL))
        tmp.renameTo(feed)
        Log.i(TAG, "downloaded ${feed.length()} bytes")
    }

    companion object {
        private const val TAG = "Rails"
        const val FEED_URL = "https://mkuran.pl/gtfs/polish_trains.zip"
        private const val FEED_MAX_AGE_MS = 3 * 24 * 3600_000L
        private const val WINDOW_SEC = 5 * 60
        private val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")
    }
}
