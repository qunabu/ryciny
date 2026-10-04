package pl.wojczal.ryciny.rails

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.hypot

/** Pure timetable maths over a GTFS zip, kept free of Android so it runs in a plain JVM test. */
object Timetable {
    const val MAX_KM = 1.5

    /** Streams the day's stop times once, keeping each trip's closest pass by the house. */
    fun passes(feed: File, date: String, homeLat: Double, homeLon: Double): List<TrainPass> = ZipFile(feed).use { zip ->
        fun rows(name: String): Sequence<List<String>> {
            val reader = BufferedReader(InputStreamReader(zip.getInputStream(zip.getEntry(name)), Charsets.UTF_8), 1 shl 16)
            // GTFS files end their lines in CRLF; a stray \r would turn "1" into "1\r" and match nothing.
            val header = parseCsv(reader.readLine().trimEnd('\r').removePrefix("\uFEFF"))
            return generateSequence { reader.readLine()?.trimEnd('\r') }.map { line -> parseCsv(line).let { cols -> header.indices.map { cols.getOrElse(it) { "" } } } }
                .let { seq -> sequenceOf(header) + seq }
        }
        fun table(name: String) = rows(name).iterator().let { it.next() to it }

        val kx = 111.32 * cos(Math.toRadians(homeLat))
        val ky = 110.57
        class Stop(val station: String, val x: Double, val y: Double, val name: String)
        val stops = HashMap<String, Stop>()
        table("stops.txt").let { (h, it) ->
            val id = h.indexOf("stop_id"); val parent = h.indexOf("parent_station"); val lat = h.indexOf("stop_lat")
            val lon = h.indexOf("stop_lon"); val nm = h.indexOf("stop_name")
            it.forEach { r ->
                stops[r[id]] = Stop(r[parent].ifBlank { r[id] }, (r[lon].toDouble() - homeLon) * kx, (r[lat].toDouble() - homeLat) * ky, r[nm])
            }
        }
        val services = HashSet<String>()
        table("calendar_dates.txt").let { (h, it) ->
            val d = h.indexOf("date"); val s = h.indexOf("service_id"); val ex = h.indexOf("exception_type")
            it.forEach { r -> if (r[d] == date && r[ex] == "1") services += r[s] }
        }
        val agencies = HashMap<String, String>()
        table("routes.txt").let { (h, it) ->
            val id = h.indexOf("route_id"); val name = h.indexOf("route_long_name")
            it.forEach { r -> agencies[r[id]] = r[name] }
        }
        class Trip(val route: String, val number: String, val name: String)
        val trips = HashMap<String, Trip>()
        table("trips.txt").let { (h, it) ->
            val id = h.indexOf("trip_id"); val route = h.indexOf("route_id"); val svc = h.indexOf("service_id")
            val num = h.indexOf("trip_short_name"); val nm = h.indexOf("plk_train_name")
            it.forEach { r -> if (r[svc] in services) trips[r[id]] = Trip(r[route], r[num], r[nm]) }
        }

        class Best(val dist: Double, val sec: Double, val between: String)
        val best = HashMap<String, Best>()
        val first = HashMap<String, String>()
        val last = HashMap<String, String>()
        var prevTrip = ""
        var prev: Triple<Stop, Int, String>? = null // stop, departure, trip
        table("stop_times.txt").let { (h, it) ->
            val tid = h.indexOf("trip_id"); val sid = h.indexOf("stop_id"); val arr = h.indexOf("arrival_time"); val dep = h.indexOf("departure_time")
            it.forEach { r ->
                val trip = r[tid]
                if (trip !in trips) return@forEach
                val stop = stops[r[sid]] ?: return@forEach
                if (trip != prevTrip) {
                    first[trip] = stop.name
                    prev = null
                    prevTrip = trip
                }
                last[trip] = stop.name
                val p = prev
                if (p != null && p.third == trip && minOf(hypot(p.first.x, p.first.y), hypot(stop.x, stop.y)) < 8) {
                    val dx = stop.x - p.first.x
                    val dy = stop.y - p.first.y
                    val len = dx * dx + dy * dy
                    val f = if (len == 0.0) 0.0 else ((-p.first.x * dx - p.first.y * dy) / len).coerceIn(0.0, 1.0)
                    val dist = hypot(p.first.x + f * dx, p.first.y + f * dy)
                    if (dist < MAX_KM && (best[trip]?.dist ?: Double.MAX_VALUE) > dist) {
                        val sec = p.second + f * (secs(r[arr]) - p.second)
                        best[trip] = Best(dist, sec, "${p.first.name} – ${stop.name}")
                    }
                }
                prev = Triple(stop, secs(r[dep]), trip)
            }
        }
        best.map { (id, b) ->
            val t = trips.getValue(id)
            TrainPass(
                passSec = b.sec.toInt() % 86_400,
                agency = agencies[t.route].orEmpty(),
                number = t.number,
                name = t.name,
                from = first[id].orEmpty(),
                to = last[id].orEmpty(),
                between = b.between,
                distKm = b.dist.toFloat(),
            )
        }.sortedBy { it.passSec }
    }


    private fun secs(t: String): Int {
        val (h, m, s) = t.split(':').map(String::toInt)
        return h * 3600 + m * 60 + s
    }

    /** One CSV line, honouring quoted fields (train names may hold commas). */
    fun parseCsv(line: String): List<String> {
        if ('"' !in line) return line.split(',')
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
