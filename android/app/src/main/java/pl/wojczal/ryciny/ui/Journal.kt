package pl.wojczal.ryciny.ui

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.wojczal.ryciny.Graph
import pl.wojczal.ryciny.art.ArtRequest
import pl.wojczal.ryciny.art.Kind
import pl.wojczal.ryciny.data.Bark
import pl.wojczal.ryciny.data.Sound
import pl.wojczal.ryciny.data.PlanePass
import pl.wojczal.ryciny.planes.planeArt
import java.io.File

private sealed interface Entry { val at: Long }
private data class BirdEntry(val name: String, val sci: String, val conf: Float, val count: Int, override val at: Long) : Entry
private data class SoundEntry(val sound: Sound, val from: Long, override val at: Long) : Entry
private data class BarkEntry(val bark: Bark, val dog: String?, override val at: Long) : Entry
private data class PlaneEntry(val pass: PlanePass, val title: String, val detail: String, val heard: Boolean, override val at: Long) : Entry

/** Everything heard and seen, newest first; unknown barks can be tagged here. */
@Composable
fun JournalScreen(g: Graph) {
    val journal by g.store.flow.collectAsState()
    var filter by remember { mutableStateOf("all") }
    var tagging by remember { mutableStateOf<Bark?>(null) }
    var viewing by remember { mutableStateOf<PlanePass?>(null) }
    val dogs = journal.dogs.associateBy { it.id }
    val entries = buildList<Entry> {
        if (filter in setOf("all", "birds")) journal.birds.forEach { add(BirdEntry(it.name, it.sci, it.conf, it.count, it.lastAt)) }
        if (filter in setOf("all", "dogs")) journal.barks.forEach { add(BarkEntry(it, it.dogId?.let { id -> dogs[id]?.let { d -> "${d.name} (${d.breed})" } }, it.lastAt)) }
        if (filter in setOf("all", "sounds")) journal.sounds.forEach { e ->
            g.catalog.sound(e.key)?.let { add(SoundEntry(it, e.at, e.lastAt)) }
        }
        if (filter in setOf("all", "planes")) journal.planes.forEach {
            add(PlaneEntry(it, "${it.title} · ${it.callsign}", listOf(it.airline, it.route, "${km(it.minDistKm.toDouble())}" + (it.minAltM?.let { a -> ", $a m" } ?: "")).filter { s -> s.isNotBlank() }.joinToString(" · "), it.heard, it.lastAt))
        }
    }.sortedByDescending { it.at }.take(400)

    Column(Modifier.fillMaxSize().paper().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Caption("Dziennik", size = 24, italic = false, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "wszystko", "birds" to "ptaki", "dogs" to "psy", "planes" to "samoloty", "sounds" to "dźwięki").forEach { (k, label) ->
                FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(label) })
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (filter == "dogs") item { KnownDogs(g) }
            items(entries) { e ->
                when (e) {
                    is SoundEntry -> Line(
                        hhmm(e.at), e.sound.past, "${hhmm(e.from)}–${hhmm(e.at)}",
                        ArtRequest(Kind.SCENE, e.sound.key, e.sound.now, prompt = e.sound.prompt), fetch = false,
                    )
                    is BirdEntry -> Line(
                        hhmm(e.at), e.name.replaceFirstChar { it.uppercase() },
                        "${e.sci} · ${(e.conf * 100).toInt()}%" + if (e.count > 1) " · ${e.count}×" else "",
                        ArtRequest(Kind.BIRD, e.sci, e.sci), fetch = true,
                    )
                    // A plane's engraving is paid for, so it is made on a tap, not by scrolling past.
                    is PlaneEntry -> Line(
                        hhmm(e.at), e.title, e.detail + if (e.heard) " · słyszany" else "",
                        passArt(e.pass), fetch = false, onClick = { viewing = e.pass },
                    )
                    is BarkEntry -> BarkLine(g, e.bark, e.dog) { tagging = e.bark }
                }
                HorizontalDivider(color = InkSoft.copy(alpha = 0.2f))
            }
        }
    }
    tagging?.let { bark -> TagDialog(g, bark) { tagging = null } }
    viewing?.let { pass -> PlaneDialog(pass) { viewing = null } }
}

@Composable
private fun Line(
    time: String,
    title: String,
    detail: String,
    art: ArtRequest,
    fetch: Boolean,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(art, fetch, Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            Text("$time · $detail", style = Italic.copy(fontSize = 14.sp), color = InkSoft)
        }
        trailing()
    }
}

/** The engraving a logged plane shares with every plane of its type in its airline's colours. */
private fun passArt(pass: PlanePass) =
    // Passes logged before the type code was kept fall back to their title as the picture's key.
    planeArt(pass.typeCode.ifBlank { pass.title }, pass.airlineIcao.ifBlank { pass.airline }, pass.title, pass.airline)

@Composable
private fun BarkLine(g: Graph, bark: Bark, dog: String?, onTag: () -> Unit) {
    val pitch = if (bark.pitchHz > 0) " · ok. ${bark.pitchHz.toInt()} Hz" else ""
    val match = if (dog != null && bark.similarity < 1f) " · podobieństwo ${(bark.similarity * 100).toInt()}%" else ""
    val known = bark.dogId?.let { id -> g.store.value.dogs.firstOrNull { it.id == id } }
    val breed = known?.let { it.breed to it.breedEn } ?: g.catalog.mongrel(bark.size).let { it.pl to it.en }
    Line(
        hhmm(bark.at),
        dog ?: "Szczekanie — nieznany pies" + if (bark.size != "?") ", raczej ${bark.size}" else "",
        "${bark.count}× · pewność ${(bark.score * 100).toInt()}%$pitch$match",
        ArtRequest(Kind.DOG, breed.first, breed.second), fetch = false,
    ) {
        bark.clip?.let { name ->
            IconButton(onClick = { play(File(g.store.clips, name)) }) { Icon(Icons.Default.PlayArrow, "Odtwórz") }
        }
        TextButton(onClick = onTag) { Text(if (dog == null) "Kto to?" else "Zmień") }
    }
}

private var player: MediaPlayer? = null

private fun play(file: File) {
    if (!file.exists()) return
    player?.release()
    player = MediaPlayer().apply {
        setDataSource(file.path)
        setOnCompletionListener { it.release(); if (player === it) player = null }
        prepare()
        start()
    }
}

/** Tag a bark: an existing dog, or a new one named with its breed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagDialog(g: Graph, bark: Bark, onDone: () -> Unit) {
    val dogs = g.store.value.dogs
    var name by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf(g.catalog.mongrel(bark.size).pl) }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Kto szczekał?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dogs.isNotEmpty()) {
                    Text("Znane psy:", color = InkSoft)
                    dogs.forEach { d ->
                        TextButton(onClick = { g.store.tagBark(bark.id, d.id); onDone() }) { Text("${d.name} — ${d.breed}") }
                    }
                    HorizontalDivider()
                }
                Text("Nowy pies:", color = InkSoft)
                OutlinedTextField(name, { name = it }, label = { Text("Imię lub opis, np. „Burek zza płotu”") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                    OutlinedTextField(
                        breed, { breed = it },
                        label = { Text("Rasa") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        g.catalog.breeds.filter { breed.isBlank() || it.pl.contains(breed, true) || breed == g.catalog.mongrel(bark.size).pl }
                            .forEach { b -> DropdownMenuItem(text = { Text(b.pl) }, onClick = { breed = b.pl; open = false }) }
                    }
                }
                Text(
                    "Rasy nie da się poznać po samym szczekaniu. Aplikacja zapamięta brzmienie tego psa " +
                        "i następnym razem rozpozna go sama.",
                    style = Italic, color = InkSoft,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = breed.isNotBlank(), onClick = {
                val id = g.store.newDog(name.ifBlank { breed }, breed, g.catalog.breedEn(breed))
                g.store.tagBark(bark.id, id)
                onDone()
            }) { Text("Zapisz nowego psa") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Anuluj") } },
    )
}

/** A past plane's engraving: generated on first look if this type and livery has none yet. */
@Composable
private fun PlaneDialog(pass: PlanePass, onDone: () -> Unit) {
    val art = passArt(pass)
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Paper,
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Engraving(art, height = 200.dp, modifier = Modifier.fillMaxWidth(), placeholder = "rycina w przygotowaniu… (ok. pół minuty)")
                Caption(pass.title, size = 24)
                Caption(listOf(pass.airline, pass.callsign).filter { it.isNotBlank() }.joinToString(" · "), size = 16)
                if (pass.route.isNotBlank()) Caption(pass.route, size = 17, italic = false)
                Caption("przeleciał o ${hhmm(pass.at)}, najbliżej ${km(pass.minDistKm.toDouble())}", size = 14, color = InkSoft)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Zamknij") } },
    )
}
