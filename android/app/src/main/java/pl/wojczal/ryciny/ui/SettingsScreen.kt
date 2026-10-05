package pl.wojczal.ryciny.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import pl.wojczal.ryciny.Graph
import pl.wojczal.ryciny.data.Export
import pl.wojczal.ryciny.data.HOME_LAT
import pl.wojczal.ryciny.data.HOME_LON
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(g: Graph) {
    val s by g.settings.flow.collectAsState()
    val place by g.place.flow.collectAsState()
    val artError by g.art.error.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().paper().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Caption("Ustawienia", size = 24, italic = false, modifier = Modifier.fillMaxWidth())

        val live by g.live.collectAsState()
        Section("Nasłuch")
        Toggle(if (live.listening) "Mikrofon włączony" else "Mikrofon wyłączony", live.listening) { on ->
            if (on) pl.wojczal.ryciny.audio.ListenService.start(context) else pl.wojczal.ryciny.audio.ListenService.stop(context)
        }

        Section("Generowanie rycin (samoloty, psy)")
        Text(
            "Claude i ElevenLabs nie generują obrazów — potrzebny jest klucz OpenAI, Google Gemini albo OpenRouter. " +
                "Każdy typ samolotu w barwach danej linii i każda rasa psa powstaje raz (ok. 0,04–0,07 USD) i zostaje w pamięci telefonu.",
            style = Italic, color = InkSoft,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("openai" to "OpenAI", "gemini" to "Gemini", "openrouter" to "OpenRouter", "none" to "bez generowania").forEach { (k, label) ->
                FilterChip(selected = s.imageProvider == k, onClick = { g.settings.update { it.copy(imageProvider = k) } }, label = { Text(label) })
            }
        }
        when (s.imageProvider) {
            "openai" -> {
                Secret("Klucz OpenAI (sk-…)", s.openAiKey) { v -> g.settings.update { it.copy(openAiKey = v.trim()) } }
                Field("Model", s.openAiModel) { v -> g.settings.update { it.copy(openAiModel = v.trim()) } }
            }
            "gemini" -> {
                Secret("Klucz Gemini (AIza…)", s.geminiKey) { v -> g.settings.update { it.copy(geminiKey = v.trim()) } }
                Field("Model", s.geminiModel) { v -> g.settings.update { it.copy(geminiModel = v.trim()) } }
            }
            "openrouter" -> {
                Secret("Klucz OpenRouter (sk-or-…)", s.openRouterKey) { v -> g.settings.update { it.copy(openRouterKey = v.trim()) } }
                Field("Model (generujący obrazy)", s.openRouterModel) { v -> g.settings.update { it.copy(openRouterModel = v.trim()) } }
            }
        }
        artError?.let { Text(it, color = Rubric, style = Italic) }
        val artVersion by g.art.version.collectAsState()
        val (cached, generated) = androidx.compose.runtime.remember(artVersion) { g.art.counts() }
        Text("Ryciny w pamięci telefonu: $cached, w tym wygenerowanych: $generated. Żadna nie jest generowana drugi raz.", color = InkSoft)

        Section("Miejsce")
        Toggle("Używaj lokalizacji telefonu", s.useGps) { v -> g.settings.update { it.copy(useGps = v) }; scope.launch { g.place.refresh() } }
        Text(
            String.format(PL, "Teraz: %.4f, %.4f %s", place.lat, place.lon, if (place.fromGps) "(GPS)" else "(ręcznie)"),
            color = InkSoft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Szer. geogr.", s.lat.toString(), Modifier.weight(1f), KeyboardType.Decimal) { v ->
                v.replace(',', '.').toDoubleOrNull()?.let { d -> g.settings.update { it.copy(lat = d) }; scope.launch { g.place.refresh() } }
            }
            Field("Dł. geogr.", s.lon.toString(), Modifier.weight(1f), KeyboardType.Decimal) { v ->
                v.replace(',', '.').toDoubleOrNull()?.let { d -> g.settings.update { it.copy(lon = d) }; scope.launch { g.place.refresh() } }
            }
        }
        Button(onClick = { g.settings.update { it.copy(lat = HOME_LAT, lon = HOME_LON) }; scope.launch { g.place.refresh() } }) { Text("Ustaw Gdańsk") }

        Section("Samoloty")
        Toggle("Pokazuj tylko samoloty, które słychać", s.onlyHeardPlanes) { v -> g.settings.update { it.copy(onlyHeardPlanes = v) } }
        Slide("Promień: ${s.radiusKm.roundToInt()} km", s.radiusKm, 3f..60f) { v -> g.settings.update { it.copy(radiusKm = v) } }
        Slide("„Nad głową” poniżej ${s.overheadMaxAltM} m", s.overheadMaxAltM.toFloat(), 500f..12000f) { v ->
            g.settings.update { it.copy(overheadMaxAltM = (v / 250).roundToInt() * 250) }
        }

        Section("Pociągi")
        val rails by g.rails.flow.collectAsState()
        Toggle("Rozpoznawaj pociągi z rozkładu (PKP PLK, raz na 3 dni pobiera 30 MB)", s.trains) { v ->
            g.settings.update { it.copy(trains = v) }
            if (v) scope.launch(kotlinx.coroutines.Dispatchers.IO) { g.rails.ensureToday() }
        }
        Text(
            when {
                rails.busy -> "Pobieram i przeliczam rozkład…"
                rails.error != null -> "Rozkład: błąd (${rails.error})"
                rails.passes.isEmpty() -> "Brak pociągów w promieniu 1,5 km od domu."
                else -> "Dziś obok domu: ${rails.passes.size} pociągów (${rails.passes.map { it.agency }.distinct().joinToString()}), " +
                    "odcinek ${rails.passes.groupingBy { it.between }.eachCount().maxBy { it.value }.key}."
            },
            color = InkSoft,
        )
        Button(onClick = { scope.launch(kotlinx.coroutines.Dispatchers.IO) { g.rails.ensureToday(force = true) } }) { Text("Odśwież rozkład") }

        Section("Dźwięki okolicy")
        Text("Wyłącz to, czego w okolicy nie ma: zniknie z ryciny i z Dziennika i nie będzie więcej zapisywane.", style = Italic, color = InkSoft)
        g.catalog.sounds.forEach { sound ->
            Toggle(sound.now, g.catalog.isOn(sound.key, s)) { v -> g.settings.update { it.copy(sounds = it.sounds + (sound.key to v)) } }
        }

        Section("Ptaki")
        Slide("Minimalna pewność BirdNET: ${(s.birdThreshold * 100).roundToInt()}%", s.birdThreshold, 0.3f..0.95f) { v -> g.settings.update { it.copy(birdThreshold = v) } }
        Toggle("Tylko gatunki występujące tu o tej porze roku", s.rangeFilter) { v -> g.settings.update { it.copy(rangeFilter = v) } }

        Section("Psy")
        Slide("Próg szczekania: ${(s.barkThreshold * 100).roundToInt()}%", s.barkThreshold, 0.1f..0.9f) { v -> g.settings.update { it.copy(barkThreshold = v) } }
        Slide("Dopasowanie do znanego psa: ${(s.dogMatch * 100).roundToInt()}%", s.dogMatch, 0.6f..0.98f) { v -> g.settings.update { it.copy(dogMatch = v) } }

        Section("Rycina")
        Slide("Pokazuj ostatnie ${s.lookbackHours} godz.", s.lookbackHours.toFloat(), 1f..72f) { v -> g.settings.update { it.copy(lookbackHours = v.roundToInt()) } }
        Toggle("Nie wygaszaj ekranu (tryb ramki)", s.keepScreenOn) { v -> g.settings.update { it.copy(keepScreenOn = v) } }

        Section("Samsung The Frame")
        val frame by g.frame.flow.collectAsState()
        var preview by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        Text(
            "Rycina trafia na telewizor w Art Mode przez sieć domową, przy każdej zmianie, ale nie częściej niż co ${s.frameEveryMin} min. " +
                "Telewizor i telefon muszą być w tej samej sieci Wi-Fi. Przy pierwszym połączeniu telewizor zapyta, czy wpuścić „Ryciny”: zezwól pilotem.",
            style = Italic, color = InkSoft,
        )
        Toggle("Wysyłaj rycinę na The Frame", s.frameOn) { v -> g.settings.update { it.copy(frameOn = v) } }
        Field("Adres IP telewizora (np. 192.168.1.50)", s.frameHost, type = KeyboardType.Uri) { v -> g.settings.update { it.copy(frameHost = v.trim(), frameToken = if (v.trim() != it.frameHost) "" else it.frameToken) } }
        Slide("Nie częściej niż co ${s.frameEveryMin} min", s.frameEveryMin.toFloat(), 1f..60f) { v -> g.settings.update { it.copy(frameEveryMin = v.roundToInt()) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !frame.busy, onClick = { scope.launch { g.frame.push(force = true) } }) { Text("Wyślij teraz") }
            androidx.compose.material3.OutlinedButton(onClick = { scope.launch { preview = g.frame.preview().asImageBitmap() } }) { Text("Podgląd") }
        }
        Text("Na telewizor trafiają tylko zaznaczone ryciny. Z ostatniej godziny wygrywa samolot, potem ptak, potem pies i dźwięk.", style = Italic, color = InkSoft)
        FRAME_KINDS.forEach { (kind, label) ->
            Toggle(label, kind in s.frameKinds) { v -> g.settings.update { it.copy(frameKinds = if (v) it.frameKinds + kind else it.frameKinds - kind) } }
        }
        if (frame.status.isNotBlank()) Text(frame.status, color = if (frame.status.startsWith("błąd")) Rubric else InkSoft, style = Italic)
        if (s.frameToken.isNotBlank()) Text("Sparowano z telewizorem.", color = InkSoft)
        preview?.let { img ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { preview = null },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { preview = null }) { Text("Zamknij") } },
                text = { androidx.compose.foundation.Image(img, contentDescription = "Podgląd ryciny dla telewizora", modifier = Modifier.fillMaxWidth()) },
            )
        }

        Section("Etap 2: Raspberry Pi")
        Text(
            "Paczka z wygenerowanymi rycinami (PNG z przezroczystym tłem, format stylu fugleramme), psami z ich próbkami " +
                "szczekania i dziennikiem. Wersja na Pi wczyta ją bez ponownego generowania i uczenia psów.",
            style = Italic, color = InkSoft,
        )
        Button(onClick = {
            scope.launch {
                val zip = Export.write(g)
                val uri = FileProvider.getUriForFile(context, "pl.wojczal.ryciny.files", zip)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        "Eksport rycin",
                    ),
                )
            }
        }) { Text("Eksportuj (ZIP)") }
    }
}

@Composable
private fun Section(title: String) = Caption(title, size = 19, italic = false, color = Rubric, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Slide(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label)
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range)
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier, keyboardOptions = KeyboardOptions(keyboardType = type))
}

@Composable
private fun Secret(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}
