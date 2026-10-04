package pl.wojczal.ryciny.rails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class TimetableTest {
    @Test
    fun csvKeepsQuotedCommas() {
        assertEquals(listOf("a", "b,c", "d\"e"), Timetable.parseCsv("a,\"b,c\",\"d\"\"e\""))
    }

    /**
     * Against a real feed: GTFS_ZIP=/path/to/polish_trains.zip GTFS_DATE=20261004 ./gradlew test.
     * Stands on the PKM platform at Gdańsk Brętowo, so trains must pass right by.
     */
    @Test
    fun trainsPassGdanskBretowo() {
        val zip = System.getenv("GTFS_ZIP")?.let(::File)
        assumeTrue(zip != null && zip.exists())
        val passes = Timetable.passes(zip!!, System.getenv("GTFS_DATE") ?: "20261004", 54.3653, 18.5736)
        println("${passes.size} passes; e.g. " + passes.take(3).joinToString { "${it.passSec / 3600}:${it.passSec % 3600 / 60} ${it.title} ${it.route} (${it.between})" })
        assertTrue(passes.size > 20)
        assertTrue(passes.all { it.distKm < Timetable.MAX_KM })
        assertTrue(passes.any { "Brętowo" in it.between })
    }
}
