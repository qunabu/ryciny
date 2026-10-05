package pl.wojczal.ryciny.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.wojczal.ryciny.data.BirdHit
import pl.wojczal.ryciny.data.PlanePass
import pl.wojczal.ryciny.data.Sound
import pl.wojczal.ryciny.data.SoundEvent

class FramePickTest {
    private val now = 10 * 3600_000L
    private fun plane(minAgo: Int) = PlaneSubject(null, PlanePass("h", "LOT1", "E175", "LOT", "", now - minAgo * 60_000L, now, 1f, 500), now - minAgo * 60_000L)
    private fun bird(minAgo: Int) = BirdSubject(BirdHit("Turdus merula", "kos", 0.9f, now - minAgo * 60_000L), 1, now - minAgo * 60_000L)
    private fun sound(minAgo: Int) = SoundSubject(Sound("mower", "Kosi", "Kosił", listOf(340), 0.2f, ""), SoundEvent("mower", now - minAgo * 60_000L, now, 1, 0.5f), now - minAgo * 60_000L)
    private val planesAndBirds = setOf("plane", "bird")

    @Test fun planeFromTheHourBeatsNewerBird() = assertEquals("plane", framePick(listOf(bird(2), plane(40)), planesAndBirds, now)?.kind)

    @Test fun soundsAreLeftOutUnlessSwitchedOn() = assertEquals("bird", framePick(listOf(sound(1), bird(30)), planesAndBirds, now)?.kind)

    @Test fun oldPlaneLosesToBirdFromTheHour() = assertEquals("bird", framePick(listOf(plane(120), bird(30)), planesAndBirds, now)?.kind)

    @Test fun quietHourFallsBackToNewestAllowed() = assertEquals("plane", framePick(listOf(bird(300), plane(120), sound(1)), planesAndBirds, now)?.kind)

    @Test fun nothingAllowedShowsNothing() = assertNull(framePick(listOf(sound(1)), planesAndBirds, now))
}
