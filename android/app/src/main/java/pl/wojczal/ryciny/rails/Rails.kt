package pl.wojczal.ryciny.rails

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
            place.flow.map { "${Math.round(it.lat * 100)}-${Math.round(it.lon * 100)}" }.distinctUntilChanged()
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
        return state.value.passes.minByOrNull { abs(it.passSec - sec) }?.takeIf { abs(it.passSec - sec) <= WINDOW_SEC }
    }

    suspend fun ensureToday(force: Boolean = false) = lock.withLock {
        if (!settings.value.trains) return@withLock
        val today = LocalDate.now(ZONE)
        val key = today.format(DateTimeFormatter.BASIC_ISO_DATE)
        val here = place.value
        val spot = "${Math.round(here.lat * 100)}-${Math.round(here.lon * 100)}"
        val index = File(dir, "passes-$key-$spot.json")
        if (!force && state.value.date == key && state.value.place == spot) return@withLock
        if (!force && index.exists()) {
            state.value = RailsState(key, spot, json.decodeFromString(ListSerializer(TrainPass.serializer()), index.readText()))
            return@withLock
        }
        val stale = !feed.exists() || System.currentTimeMillis() - feed.lastModified() > FEED_MAX_AGE_MS
        // 30 MB is for Wi-Fi; on mobile data only a tap on "Odśwież rozkład" fetches it.
        if (stale && !force && metered()) {
            if (!feed.exists()) {
                state.value = state.value.copy(error = "czekam na Wi-Fi, żeby pobrać rozkład (30 MB)")
                return@withLock
            }
        }
        state.value = state.value.copy(busy = true, error = null)
        try {
            if (force || (stale && !metered()) || !feed.exists()) download()
            val passes = Timetable.passes(feed, key, here.lat, here.lon)
            index.writeText(json.encodeToString(ListSerializer(TrainPass.serializer()), passes))
            dir.listFiles()?.filter { it.name.startsWith("passes-") && it != index }?.forEach { it.delete() }
            state.value = RailsState(key, spot, passes)
        } catch (e: Exception) {
            state.value = state.value.copy(busy = false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun metered(): Boolean =
        context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true

    private suspend fun download() {
        val tmp = File(dir, "polish_trains.zip.tmp")
        tmp.writeBytes(Http.get(FEED_URL))
        tmp.renameTo(feed)
    }

    companion object {
        const val FEED_URL = "https://mkuran.pl/gtfs/polish_trains.zip"
        private const val FEED_MAX_AGE_MS = 3 * 24 * 3600_000L
        private const val WINDOW_SEC = 5 * 60
        private val ZONE: ZoneId = ZoneId.of("Europe/Warsaw")
    }
}
