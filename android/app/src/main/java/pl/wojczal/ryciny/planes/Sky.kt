package pl.wojczal.ryciny.planes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.wojczal.ryciny.data.Http
import pl.wojczal.ryciny.data.HttpError
import pl.wojczal.ryciny.data.LatLon
import pl.wojczal.ryciny.data.Place
import pl.wojczal.ryciny.data.PlanePass
import pl.wojczal.ryciny.data.SettingsStore
import pl.wojczal.ryciny.data.Store
import pl.wojczal.ryciny.data.json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Airport(val iata: String, val city: String)

/** The engraving of one aircraft type in one airline's colours: shared by every plane that matches. */
fun planeArt(typeCode: String, airlineIcao: String, title: String, airline: String) =
    pl.wojczal.ryciny.art.ArtRequest(pl.wojczal.ryciny.art.Kind.PLANE, "$typeCode-${airlineIcao.ifBlank { "plain" }}", title, airline)

data class Plane(
    val hex: String,
    val callsign: String,
    val typeCode: String,
    val registration: String,
    val altM: Int?,
    val onGround: Boolean,
    val distKm: Double,
    val bearing: Double,
    val track: Double?,
    val speedKmh: Int?,
    val climbMs: Double?,
    val model: String = "",
    val airline: String = "",
    val airlineIcao: String = "",
    val origin: Airport? = null,
    val destination: Airport? = null,
) {
    /** "Embraer E175" from the type table, else what adsbdb calls it, else the bare ICAO code. */
    val title: String get() = AircraftTypes.name(typeCode) ?: model.ifBlank { typeCode.ifBlank { "Statek powietrzny" } }
    val route: String
        get() = if (origin == null || destination == null) "" else
            "${origin.city} (${origin.iata}) → ${destination.city} (${destination.iata})"
    val slantKm: Double get() = hypot(distKm, (altM ?: 0) / 1000.0)
}

data class SkyState(
    val planes: List<Plane> = emptyList(),
    val overhead: Plane? = null,
    val updatedAt: Long = 0,
    val error: String? = null,
    val heardAt: Long = 0,
)

/**
 * Live aircraft from adsb.lol (community ADS-B network, no key) and their type,
 * operator and route from adsbdb. Polls only while the plate is on screen or the
 * listening service runs. On the Pi this source can be swapped for a local RTL-SDR.
 */
class Sky(
    private val settings: SettingsStore,
    private val place: Place,
    private val store: Store,
    private val scope: CoroutineScope,
) {
    private val state = MutableStateFlow(SkyState())
    val flow: StateFlow<SkyState> = state
    private val holders = MutableStateFlow(emptySet<String>())
    private val aircraft = HashMap<String, JsonObject?>()
    private val routes = HashMap<String, JsonObject?>()

    init {
        scope.launch {
            while (true) {
                if (holders.value.isNotEmpty()) poll()
                delay(POLL_MS)
            }
        }
    }

    fun setActive(holder: String, on: Boolean) = holders.update { if (on) it + holder else it - holder }

    /** The microphone heard an engine: the plane overhead gets a "słychać" mark. */
    fun heard(at: Long) {
        state.update { it.copy(heardAt = at) }
        state.value.overhead?.let { record(it, at, heard = true) }
    }

    private suspend fun poll() {
        val here = place.value
        val s = settings.value
        val nm = (s.radiusKm / 1.852).roundToInt().coerceIn(1, 250)
        try {
            val body = Http.get("https://api.adsb.lol/v2/point/${here.lat}/${here.lon}/$nm").decodeToString()
            val list = json.parseToJsonElement(body).jsonObject["ac"]?.let { it as? kotlinx.serialization.json.JsonArray }.orEmpty()
            val planes = list.mapNotNull { parse(it.jsonObject, here) }.filter { !it.onGround }.sortedBy { it.slantKm }
            val overhead = (planes.firstOrNull { (it.altM ?: Int.MAX_VALUE) <= s.overheadMaxAltM } ?: planes.firstOrNull())
                ?.let { enrich(it) }
            val now = System.currentTimeMillis()
            state.update {
                SkyState(
                    planes = planes.map { p -> if (p.hex == overhead?.hex) overhead else p },
                    overhead = overhead,
                    updatedAt = now,
                    heardAt = it.heardAt,
                )
            }
            overhead?.let { record(it, now, heard = now - state.value.heardAt < 20_000) }
        } catch (e: Exception) {
            state.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        }
    }

    private fun record(p: Plane, at: Long, heard: Boolean) = store.addPlane(
        PlanePass(
            hex = p.hex,
            callsign = p.callsign,
            title = p.title,
            airline = p.airline,
            route = p.route,
            at = at,
            lastAt = at,
            minDistKm = p.distKm.toFloat(),
            minAltM = p.altM,
            heard = heard,
            typeCode = p.typeCode,
            airlineIcao = p.airlineIcao,
        ),
    )

    private fun parse(o: JsonObject, here: LatLon): Plane? {
        val lat = o.num("lat") ?: return null
        val lon = o.num("lon") ?: return null
        val baro = o["alt_baro"]?.jsonPrimitive
        val ground = baro?.contentOrNull == "ground"
        val altFt = o.num("alt_geom") ?: baro?.doubleOrNull
        return Plane(
            hex = o.str("hex"),
            callsign = o.str("flight").trim(),
            typeCode = o.str("t"),
            registration = o.str("r"),
            altM = altFt?.let { (it * 0.3048).roundToInt() },
            onGround = ground,
            distKm = haversineKm(here.lat, here.lon, lat, lon),
            bearing = bearing(here.lat, here.lon, lat, lon),
            track = o.num("track"),
            speedKmh = o.num("gs")?.let { (it * 1.852).roundToInt() },
            climbMs = (o.num("baro_rate") ?: o.num("geom_rate"))?.let { it * 0.00508 },
        )
    }

    /** Adds type, airline and route; each looked up once per aircraft / callsign. */
    private suspend fun enrich(p: Plane): Plane {
        val a = cached(aircraft, p.hex) { lookup("https://api.adsbdb.com/v0/aircraft/${p.hex}", "aircraft") }
        val r = if (p.callsign.isBlank()) null else cached(routes, p.callsign) {
            lookup("https://api.adsbdb.com/v0/callsign/${p.callsign}", "flightroute")
        }
        val airline = r?.get("airline")?.let { it as? JsonObject }
        return p.copy(
            model = listOfNotNull(a?.str("manufacturer"), a?.str("type")).joinToString(" ").trim(),
            airline = airline?.str("name") ?: a?.str("registered_owner").orEmpty(),
            airlineIcao = airline?.str("icao") ?: a?.str("registered_owner_operator_flag_code").orEmpty(),
            origin = r?.airport("origin"),
            destination = r?.airport("destination"),
        )
    }

    /** A 404 (unknown aircraft or callsign) is remembered as null; a network blip is retried next poll. */
    private suspend fun cached(cache: HashMap<String, JsonObject?>, key: String, fetch: suspend () -> JsonObject?): JsonObject? {
        if (cache.containsKey(key)) return cache[key]
        return try {
            fetch().also { cache[key] = it }
        } catch (e: HttpError) {
            null.also { cache[key] = null }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun lookup(url: String, field: String): JsonObject? =
        json.parseToJsonElement(Http.get(url).decodeToString()).jsonObject["response"]?.let { it as? JsonObject }
            ?.get(field)?.let { it as? JsonObject }

    private fun JsonObject.airport(key: String): Airport? = (get(key) as? JsonObject)?.let {
        Airport(it.str("iata_code").ifBlank { it.str("icao_code") }, it.str("municipality").ifBlank { it.str("name") })
    }

    companion object {
        const val POLL_MS = 10_000L

        private fun JsonObject.str(key: String): String = (get(key) as? JsonElement)?.let {
            runCatching { it.jsonPrimitive.contentOrNull }.getOrNull()
        }.orEmpty()

        private fun JsonObject.num(key: String): Double? =
            runCatching { get(key)?.jsonPrimitive?.doubleOrNull }.getOrNull()

        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
            return 6371.0 * 2 * atan2(sqrt(a), sqrt(1 - a))
        }

        fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
            val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(lon2 - lon1))
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }
    }
}
