package pl.wojczal.ryciny.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.wojczal.ryciny.Graph
import pl.wojczal.ryciny.graph
import pl.wojczal.ryciny.art.ArtRequest
import pl.wojczal.ryciny.art.Kind
import pl.wojczal.ryciny.audio.ListenService
import pl.wojczal.ryciny.data.Bark
import pl.wojczal.ryciny.data.BirdHit
import pl.wojczal.ryciny.data.Dog
import pl.wojczal.ryciny.data.Sound
import pl.wojczal.ryciny.data.SoundEvent
import pl.wojczal.ryciny.planes.Plane
import pl.wojczal.ryciny.planes.art
import pl.wojczal.ryciny.planes.planeArt
import pl.wojczal.ryciny.data.PlanePass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val PL: Locale = Locale.forLanguageTag("pl-PL")
fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", PL).format(Date(t))
fun km(d: Double): String = String.format(PL, "%.1f km", d)

private val COMPASS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
fun compass(deg: Double) = COMPASS[((deg + 22.5) / 45).toInt() % 8]

@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** One thing to draw: the plane overhead, the last bird, the last dog, or the last of each sound in sounds.json. */
private sealed interface Subject { val at: Long }
private data class PlaneSubject(val live: Plane?, val pass: PlanePass?, override val at: Long) : Subject
private data class BirdSubject(val bird: BirdHit, val count: Int, override val at: Long) : Subject
private data class SoundSubject(val sound: Sound, val event: SoundEvent, override val at: Long) : Subject
private data class DogSubject(val dog: Dog?, val barks: List<Bark>, override val at: Long) : Subject

/** The page itself: one engraving, of whatever was seen or heard most recently. Tap for the next. */
@Composable
fun PlateScreen(g: Graph) {
    val journal by g.store.flow.collectAsState()
    val sky by g.sky.flow.collectAsState()
    val settings by g.settings.flow.collectAsState()
    val live by g.live.collectAsState()
    val now = rememberNow()
    val since = now - settings.lookbackHours * 3600_000L

    val subjects = buildList {
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

    var offset by remember { mutableIntStateOf(0) }
    val newest = subjects.firstOrNull()
    // Something new arrived: go back to it.
    LaunchedEffect(newest?.javaClass, newest?.at) { offset = 0 }
    val subject = subjects.getOrNull(if (subjects.isEmpty()) 0 else offset % subjects.size)

    Column(
        Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Caption(SimpleDateFormat("d MMMM yyyy, HH:mm", PL).format(Date(now)), size = 14, color = InkSoft)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().clickable(enabled = subjects.size > 1) { offset++ },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (subject) {
                is PlaneSubject -> if (subject.live != null) PlanePlate(subject.live, now - sky.heardAt < 20_000) else PassPlate(subject.pass!!)
                is BirdSubject -> BirdPlate(subject.bird, subject.count)
                is DogSubject -> DogPlate(g, subject.dog, subject.barks)
                is SoundSubject -> SoundPlate(subject.sound, subject.event, now)
                null -> Caption(
                    if (sky.error != null) "Brak danych ADS-B (${sky.error})" else "Cisza: nic nie przeleciało, nie zaśpiewało ani nie zaszczekało",
                    color = InkSoft, modifier = Modifier.padding(vertical = 80.dp),
                )
            }
            if (subjects.size > 1) {
                Spacer(Modifier.height(10.dp))
                Caption("${offset % subjects.size + 1} z ${subjects.size} · dotknij, by zobaczyć następną", size = 12, color = InkSoft)
            }
        }

        Spacer(Modifier.height(18.dp))
        LiveStrip(live.listening, live.sound, live.error)
        Spacer(Modifier.height(8.dp))
        Caption(
            "Ryciny ptaków: fugleramme (CC BY-SA 4.0), tablice z domeny publicznej · rozpoznawanie: BirdNET, YAMNet · " +
                "samoloty: adsb.lol, adsbdb · ryciny samolotów i psów generowane",
            size = 11, color = InkSoft,
        )
    }
}

@Composable
private fun PlanePlate(p: Plane, heard: Boolean) {
    Engraving(
        planeArt(p.typeCode, p.airlineIcao, p.title, p.airline),
        height = 260.dp,
        modifier = Modifier.fillMaxWidth(),
    )
    Caption(p.title, size = 28)
    Caption(listOf(p.airline, p.callsign, p.registration).filter { it.isNotBlank() }.joinToString(" · "), size = 17)
    if (p.route.isNotBlank()) Caption(p.route, size = 19, italic = false)
    val alt = p.altM?.let { "wys. ${String.format(PL, "%,d", it)} m" }
    val climb = p.climbMs?.let { if (it > 1.5) "wznosi się" else if (it < -1.5) "zniża lot" else null }
    Caption(
        listOfNotNull(km(p.distKm) + " na " + compass(p.bearing), alt, p.speedKmh?.let { "$it km/h" }, climb).joinToString(" · "),
        size = 15, color = InkSoft,
    )
    if (heard) Caption("— słychać go teraz —", size = 15, color = Rubric)
}

/** A plane heard a while ago, no longer overhead: drawn from the journal. */
@Composable
private fun PassPlate(p: PlanePass) {
    Engraving(p.art(), height = 260.dp, modifier = Modifier.fillMaxWidth())
    Caption(p.title, size = 28)
    Caption(listOf(p.airline, p.callsign).filter { it.isNotBlank() }.joinToString(" · "), size = 17)
    if (p.route.isNotBlank()) Caption(p.route, size = 19, italic = false)
    Caption(
        "słychać było o ${hhmm(p.at)} · najbliżej ${km(p.minDistKm.toDouble())}" + (p.minAltM?.let { ", wys. ${String.format(PL, "%,d", it)} m" } ?: ""),
        size = 15, color = InkSoft,
    )
}

@Composable
private fun BirdPlate(bird: BirdHit, count: Int) {
    Engraving(ArtRequest(Kind.BIRD, bird.sci, bird.sci), height = 280.dp, modifier = Modifier.fillMaxWidth())
    Caption(bird.name.replaceFirstChar { it.uppercase() }, size = 28, italic = false)
    Caption(bird.sci, size = 19)
    Caption("ostatnio ${hhmm(bird.lastAt)}" + if (count > 1) " · ${count}×" else "", size = 14, color = InkSoft)
}

@Composable
private fun DogPlate(g: Graph, dog: Dog?, barks: List<Bark>) {
    val count = barks.sumOf { it.count }
    val last = barks.maxOf { it.lastAt }
    if (dog != null) {
        Engraving(ArtRequest(Kind.DOG, dog.breed, dog.breedEn), height = 260.dp, modifier = Modifier.fillMaxWidth())
        Caption(dog.name, size = 28, italic = false)
        Caption(dog.breed, size = 19)
        Caption("szczekał ${count}× · ostatnio ${hhmm(last)}", size = 14, color = InkSoft)
    } else {
        val size = barks.first().size
        val mongrel = g.catalog.mongrel(size)
        Engraving(ArtRequest(Kind.DOG, mongrel.pl, mongrel.en), height = 240.dp, modifier = Modifier.fillMaxWidth())
        Caption(if (size == "?") "Nieznany pies" else "Nieznany pies, raczej $size", size = 26, italic = false)
        Caption("szczekał ${count}× · ostatnio ${hhmm(last)} · oznacz go w Dzienniku", size = 14, color = InkSoft)
    }
}

@Composable
private fun LiveStrip(listening: Boolean, sound: String, error: String?) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Caption(
            when {
                error != null -> "nasłuch przerwany: $error"
                listening -> "● nasłuch trwa" + if (sound.isNotBlank()) " · słychać: $sound" else ""
                else -> "○ nasłuch wyłączony"
            },
            size = 14,
            color = if (listening) Rubric else InkSoft,
        )
        Spacer(Modifier.height(6.dp))
        if (listening) {
            OutlinedButton(onClick = { ListenService.stop(context) }) { Text("Zatrzymaj nasłuch") }
        } else {
            Button(onClick = { ListenService.start(context) }) { Text("Włącz nasłuch") }
        }
    }
}

@Composable
private fun SoundPlate(sound: Sound, e: SoundEvent, now: Long) {
    Engraving(LocalContext.current.graph.catalog.soundArt(sound, e.variant), height = 260.dp, modifier = Modifier.fillMaxWidth())
    val still = now - e.lastAt < 2 * 60_000L
    Caption(if (still) sound.now else sound.past, size = 28, italic = false)
    if (e.title.isNotBlank()) Caption(e.title, size = 20)
    if (e.subtitle.isNotBlank()) Caption(e.subtitle, size = 17, italic = false)
    if (sound.key == "train" && e.title.isBlank()) {
        val rails by LocalContext.current.graph.rails.flow.collectAsState()
        Caption(
            when {
                rails.busy -> "rozkład jeszcze się wczytuje, trasa pojawi się za chwilę"
                rails.error != null -> "brak rozkładu: ${rails.error}"
                rails.passes.isEmpty() -> "rozkład niegotowy albo brak torów w promieniu 1,5 km"
                else -> "żaden pociąg z rozkładu nie mijał domu ±5 min od tej chwili (może opóźniony)"
            },
            size = 14, color = InkSoft,
        )
    }
    val minutes = ((e.lastAt - e.at) / 60_000L).coerceAtLeast(1)
    Caption(if (still) "od ${hhmm(e.at)} · już $minutes min" else "${hhmm(e.at)}–${hhmm(e.lastAt)} · $minutes min", size = 15, color = InkSoft)
}
