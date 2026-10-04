package pl.wojczal.ryciny.planes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    private val gdn = Airport("GDN", "Gdańsk", 54.3776, 18.4662)
    private val waw = Airport("WAW", "Warsaw", 52.1657, 20.9671)
    private val auh = Airport("AUH", "Abu Dhabi", 24.4330, 54.6511)
    private val bud = Airport("BUD", "Budapest", 47.4369, 19.2556)
    private val ber = Airport("BER", "Berlin", 52.3667, 13.5033)
    private val rix = Airport("RIX", "Riga", 56.9236, 23.9711)
    private val overGdansk = 54.36 to 18.57

    @Test
    fun abuDhabiBudapestIsNotOverGdansk() = assertFalse(Sky.onRoute(overGdansk.first, overGdansk.second, auh, bud))

    @Test
    fun departureFromGdnIsOnRoute() = assertTrue(Sky.onRoute(overGdansk.first, overGdansk.second, gdn, waw))

    @Test
    fun overflightOnTheWayIsOnRoute() = assertTrue(Sky.onRoute(overGdansk.first, overGdansk.second, ber, rix))

    @Test
    fun beyondTheDestinationIsNot() = assertFalse(Sky.onRoute(overGdansk.first, overGdansk.second, bud, waw))
}
