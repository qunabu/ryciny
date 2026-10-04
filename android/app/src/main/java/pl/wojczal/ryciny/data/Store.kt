package pl.wojczal.ryciny.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/** One bird singing, merged while it keeps singing (gaps under a minute). */
@Serializable
data class BirdHit(
    val sci: String,
    val name: String,
    val conf: Float,
    val at: Long,
    val lastAt: Long = at,
    val count: Int = 1,
)

/** One barking episode: windows of the same dog less than [Store.BARK_GAP_MS] apart. */
@Serializable
data class Bark(
    val id: String,
    val at: Long,
    val lastAt: Long,
    val count: Int,
    val score: Float,
    val pitchHz: Float,
    val size: String,
    val embedding: List<Float>,
    val dogId: String? = null,
    val similarity: Float = 0f,
    val clip: String? = null,
)

/** A dog the user has named; its barks are the YAMNet embeddings they tagged. */
@Serializable
data class Dog(
    val id: String,
    val name: String,
    val breed: String,
    val breedEn: String,
    val samples: List<List<Float>> = emptyList(),
)

@Serializable
data class PlanePass(
    val hex: String,
    val callsign: String,
    val title: String,
    val airline: String,
    val route: String,
    val at: Long,
    val lastAt: Long,
    val minDistKm: Float,
    val minAltM: Int?,
    val heard: Boolean = false,
    val typeCode: String = "",
    val airlineIcao: String = "",
)

/** One stretch of a sound from shared/sounds.json (mowing, a storm, a ship...), until it has been quiet for [Store.SOUND_GAP_MS]. */
@Serializable
data class SoundEvent(
    val key: String,
    val at: Long,
    val lastAt: Long,
    val count: Int = 1,
    val score: Float,
)

/** Written by 0.2.0, read once to carry its mowing over into [SoundEvent]s. */
@Serializable
data class Mowing(
    val at: Long,
    val lastAt: Long,
    val count: Int = 1,
    val score: Float,
)

@Serializable
data class Journal(
    val birds: List<BirdHit> = emptyList(),
    val barks: List<Bark> = emptyList(),
    val dogs: List<Dog> = emptyList(),
    val planes: List<PlanePass> = emptyList(),
    val sounds: List<SoundEvent> = emptyList(),
    val mowing: List<Mowing> = emptyList(),
)

@OptIn(FlowPreview::class)
class Store(private val dir: File, scope: CoroutineScope) {
    private val file = File(dir, "journal.json")
    val clips = File(dir, "clips").apply { mkdirs() }
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Journal> = state
    val value: Journal get() = state.value

    init {
        scope.launch(Dispatchers.IO) {
            state.drop(1).debounce(3_000).collect { journal ->
                val tmp = File(dir, "journal.json.tmp")
                tmp.writeText(json.encodeToString(Journal.serializer(), journal))
                tmp.renameTo(file)
            }
        }
    }

    fun addBird(sci: String, name: String, conf: Float, at: Long) = state.update { j ->
        val last = j.birds.lastOrNull { it.sci == sci }
        val birds = if (last != null && at - last.lastAt < BIRD_GAP_MS) {
            j.birds.map { if (it === last) it.copy(lastAt = at, count = it.count + 1, conf = maxOf(it.conf, conf)) else it }
        } else {
            j.birds + BirdHit(sci, name, conf, at)
        }
        j.copy(birds = birds.filter { at - it.lastAt < KEEP_MS })
    }

    /** Folds a bark into the running episode of the same dog, or opens a new one. */
    fun addBark(bark: Bark) = state.update { j ->
        val last = j.barks.lastOrNull()
        val same = last != null && bark.at - last.lastAt < BARK_GAP_MS &&
            (last.dogId == bark.dogId && (bark.dogId != null || last.size == bark.size))
        val barks = if (same) {
            j.barks.dropLast(1) + last!!.copy(
                lastAt = bark.at,
                count = last.count + 1,
                score = maxOf(last.score, bark.score),
                clip = if (bark.score > last.score && bark.clip != null) bark.clip else last.clip,
            )
        } else {
            j.barks + bark
        }
        j.copy(barks = trimBarks(barks, bark.at))
    }

    private fun trimBarks(barks: List<Bark>, now: Long): List<Bark> {
        val kept = barks.filter { now - it.lastAt < KEEP_MS }.takeLast(MAX_BARKS)
        val keptClips = kept.mapNotNull { it.clip }.toSet()
        clips.listFiles()?.filter { it.name !in keptClips }?.forEach { it.delete() }
        return kept
    }

    /** Tags a bark episode as [dogId]'s; its embedding becomes one more sample of that dog. */
    fun tagBark(barkId: String, dogId: String) = state.update { j ->
        val bark = j.barks.firstOrNull { it.id == barkId } ?: return@update j
        j.copy(
            barks = j.barks.map { if (it.id == barkId) it.copy(dogId = dogId, similarity = 1f) else it },
            dogs = j.dogs.map { if (it.id == dogId) it.copy(samples = (it.samples + listOf(bark.embedding)).takeLast(MAX_SAMPLES)) else it },
        )
    }

    fun newDog(name: String, breed: String, breedEn: String): String {
        val id = UUID.randomUUID().toString().take(8)
        state.update { it.copy(dogs = it.dogs + Dog(id, name, breed, breedEn)) }
        return id
    }

    fun editDog(id: String, name: String, breed: String, breedEn: String) = state.update { j ->
        j.copy(dogs = j.dogs.map { if (it.id == id) it.copy(name = name, breed = breed, breedEn = breedEn) else it })
    }

    fun deleteDog(id: String) = state.update { j ->
        j.copy(
            dogs = j.dogs.filterNot { it.id == id },
            barks = j.barks.map { if (it.dogId == id) it.copy(dogId = null, similarity = 0f) else it },
        )
    }

    fun addSound(key: String, at: Long, score: Float) = state.update { j ->
        val last = j.sounds.lastOrNull { it.key == key }
        val sounds = if (last != null && at - last.lastAt < SOUND_GAP_MS) {
            j.sounds.map { if (it === last) it.copy(lastAt = at, count = it.count + 1, score = maxOf(it.score, score)) else it }
        } else {
            j.sounds + SoundEvent(key, at, at, 1, score)
        }
        j.copy(sounds = sounds.filter { at - it.lastAt < KEEP_MS }.takeLast(MAX_SOUNDS))
    }

    fun addPlane(pass: PlanePass) = state.update { j ->
        val last = j.planes.lastOrNull { it.hex == pass.hex }
        val planes = if (last != null && pass.at - last.lastAt < PLANE_GAP_MS) {
            j.planes.map {
                if (it !== last) it else it.copy(
                    lastAt = pass.at,
                    minDistKm = minOf(it.minDistKm, pass.minDistKm),
                    minAltM = listOfNotNull(it.minAltM, pass.minAltM).minOrNull(),
                    heard = it.heard || pass.heard,
                    route = pass.route.ifBlank { it.route },
                    airline = pass.airline.ifBlank { it.airline },
                    title = pass.title.ifBlank { it.title },
                    typeCode = pass.typeCode.ifBlank { it.typeCode },
                    airlineIcao = pass.airlineIcao.ifBlank { it.airlineIcao },
                )
            }
        } else {
            j.planes + pass
        }
        j.copy(planes = planes.filter { pass.at - it.lastAt < KEEP_MS }.takeLast(MAX_PLANES))
    }

    private fun load(): Journal {
        val j = runCatching { json.decodeFromString(Journal.serializer(), file.readText()) }.getOrDefault(Journal())
        if (j.mowing.isEmpty()) return j
        val carried = j.mowing.map { SoundEvent("mower", it.at, it.lastAt, it.count, it.score) }
        return j.copy(sounds = (carried + j.sounds).sortedBy { it.at }, mowing = emptyList())
    }

    companion object {
        const val BIRD_GAP_MS = 60_000L
        const val BARK_GAP_MS = 30_000L
        const val PLANE_GAP_MS = 10 * 60_000L
        const val SOUND_GAP_MS = 5 * 60_000L
        const val MAX_SOUNDS = 1_000
        const val KEEP_MS = 7 * 24 * 3600_000L
        const val MAX_BARKS = 300
        const val MAX_PLANES = 500
        const val MAX_SAMPLES = 30
    }
}
