package pl.wojczal.ryciny.ui

import pl.wojczal.ryciny.Graph
import pl.wojczal.ryciny.data.Bark
import pl.wojczal.ryciny.data.BirdHit
import pl.wojczal.ryciny.data.Dog
import pl.wojczal.ryciny.data.Journal
import pl.wojczal.ryciny.data.PlanePass
import pl.wojczal.ryciny.data.Settings
import pl.wojczal.ryciny.data.Sound
import pl.wojczal.ryciny.data.SoundEvent
import pl.wojczal.ryciny.planes.Plane
import pl.wojczal.ryciny.planes.SkyState

/** One thing to draw: the plane overhead, the last bird, the last dog, or the last of each sound in sounds.json. */
internal sealed interface Subject {
    val at: Long

    /** Stays the same while the plate shows the same thing, so a frame redraws only when it really changes. */
    val identity: String
}

internal data class PlaneSubject(val live: Plane?, val pass: PlanePass?, override val at: Long) : Subject {
    override val identity get() = "plane:${live?.hex ?: pass?.hex}:${pass?.at}"
}

internal data class BirdSubject(val bird: BirdHit, val count: Int, override val at: Long) : Subject {
    override val identity get() = "bird:${bird.sci}"
}

internal data class SoundSubject(val sound: Sound, val event: SoundEvent, override val at: Long) : Subject {
    override val identity get() = "sound:${sound.key}:${event.at}:${event.title}"
}

internal data class DogSubject(val dog: Dog?, val barks: List<Bark>, override val at: Long) : Subject {
    override val identity get() = "dog:${dog?.id ?: barks.firstOrNull()?.size}"
}

/** Everything the plate can show right now, newest first; the plate screen and the TV frame both read this. */
internal fun plateSubjects(g: Graph, journal: Journal, sky: SkyState, settings: Settings, now: Long): List<Subject> {
    val since = now - settings.lookbackHours * 3600_000L
    return buildList {
        if (settings.onlyHeardPlanes) {
            // Only a plane the microphone heard; live details while it is still the one overhead.
            journal.planes.filter { it.heard && it.lastAt >= since }.maxByOrNull { it.lastAt }?.let { pass ->
                add(PlaneSubject(sky.overhead?.takeIf { it.hex == pass.hex }, pass, pass.at))
            }
        } else {
            sky.overhead?.let { p ->
                // A plane counts from when it came overhead, so one that lingers does not hold the page forever.
                val pass = journal.planes.lastOrNull { it.hex == p.hex }
                add(PlaneSubject(p, pass, pass?.at ?: sky.updatedAt))
            }
        }
        journal.birds.filter { it.lastAt >= since }.maxByOrNull { it.lastAt }?.let { b ->
            add(BirdSubject(b, journal.birds.filter { it.sci == b.sci && it.lastAt >= since }.sumOf { it.count }, b.lastAt))
        }
        journal.barks.filter { it.lastAt >= since }.maxByOrNull { it.lastAt }?.let { last ->
            val same = journal.barks.filter { it.lastAt >= since && it.dogId == last.dogId && (last.dogId != null || it.size == last.size) }
            add(DogSubject(journal.dogs.firstOrNull { it.id == last.dogId }, same, last.lastAt))
        }
        // The latest stretch of each sound heard (mowing, a storm, a ship...), each its own plate.
        journal.sounds.filter { it.lastAt >= since }.groupBy { it.key }.values.map { it.maxBy { e -> e.lastAt } }.forEach { e ->
            // Ordered by when it started: traffic or a long chat that keeps going does not keep jumping to the front.
            g.catalog.soundOn(e.key, settings)?.let { add(SoundSubject(it, e, e.at)) }
        }
    }.sortedByDescending { it.at }
}
