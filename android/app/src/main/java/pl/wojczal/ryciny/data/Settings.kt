package pl.wojczal.ryciny.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

/** Gdańsk city centre: where the frame lives when the phone gives no fix. */
const val HOME_LAT = 54.352
const val HOME_LON = 18.646

@Serializable
data class Settings(
    val imageProvider: String = "openai", // openai | gemini | openrouter | none
    val openAiKey: String = "",
    val openAiModel: String = "gpt-image-1",
    val geminiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash-image",
    val openRouterKey: String = "",
    val openRouterModel: String = "google/gemini-2.5-flash-image",
    val birdThreshold: Float = 0.7f,
    val rangeFilter: Boolean = true,
    val barkThreshold: Float = 0.35f,
    val dogMatch: Float = 0.85f,
    val radiusKm: Float = 15f,
    val overheadMaxAltM: Int = 3500,
    val lookbackHours: Int = 12,
    val useGps: Boolean = true,
    val lat: Double = HOME_LAT,
    val lon: Double = HOME_LON,
    val keepScreenOn: Boolean = true,
    val trains: Boolean = true,
    /** Whether the microphone should be on; remembered, so a stop survives reopening the app. */
    val listen: Boolean = true,
    /** Show only planes the microphone heard; the rest are still logged, just not shown. */
    val onlyHeardPlanes: Boolean = true,
    /** Per-sound switches set by the user, over the defaults in sounds.json. */
    val sounds: Map<String, Boolean> = emptyMap(),
)

class SettingsStore(dir: File, private val scope: CoroutineScope) {
    private val file = File(dir, "settings.json")
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Settings> = state
    val value: Settings get() = state.value

    fun update(change: (Settings) -> Settings) {
        state.update(change)
        val snapshot = state.value
        scope.launch(Dispatchers.IO) { file.writeText(json.encodeToString(Settings.serializer(), snapshot)) }
    }

    private fun load(): Settings =
        runCatching { json.decodeFromString(Settings.serializer(), file.readText()) }.getOrDefault(Settings())
}
