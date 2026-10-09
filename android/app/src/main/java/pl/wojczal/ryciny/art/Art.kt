package pl.wojczal.ryciny.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import pl.wojczal.ryciny.data.Http
import pl.wojczal.ryciny.data.HttpError
import pl.wojczal.ryciny.data.SettingsStore
import pl.wojczal.ryciny.data.json
import java.io.File

enum class Kind(val dir: String) { BIRD("birds"), PLANE("planes"), DOG("dogs"), SCENE("scenes") }

/**
 * One engraving to show. [key] names the cached file, so every plane of one type in
 * one airline's colours shares a single picture, and every beagle shares another.
 */
data class ArtRequest(val kind: Kind, val key: String, val subject: String, val airline: String = "", val prompt: String = "")

/**
 * Birds come from fugleramme's hand-cut 1800s plates (CC BY-SA 4.0), downloaded once
 * per species. Aircraft, dogs, the neighbour's mower and birds fugleramme has no plate for are generated in
 * the same style by an image model, cut out of their paper and cached for good.
 */
class Art(private val context: Context, private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val root = File(context.filesDir, "art")
    private val lock = Mutex()
    private val failedAt = HashMap<String, Long>()
    private val noPlate = HashSet<String>()
    private val inflight = HashMap<String, Deferred<File?>>()
    private val style: JsonObject by lazy { json.parseToJsonElement(asset("style.json")).jsonObject }
    private val aliases: Map<String, String> by lazy {
        json.parseToJsonElement(asset("birdnet_aliases.json")).jsonObject.mapValues { it.value.jsonPrimitive.content }
    }
    private val _version = MutableStateFlow(0)
    /** Bumped whenever a new picture lands, so screens waiting on one look again. */
    val version: StateFlow<Int> = _version
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val generatedDirs: List<File> get() = listOf(Kind.PLANE, Kind.DOG, Kind.SCENE).map { File(root, it.dir) } + File(root, "birds-generated")

    fun cached(req: ArtRequest): File? = candidates(req).firstOrNull { it.exists() }

    var server: pl.wojczal.ryciny.data.Remote? = null

    suspend fun load(req: ArtRequest): File? {
        cached(req)?.let { return it }
        server?.takeIf { it.active }?.let { remote ->
            // Home Assistant mode: the add-on fetches or draws it (once), the phone keeps a copy.
            return runCatching { remote.art(root, req.kind.name, req.key, req.subject, req.airline, req.prompt) }.getOrNull()
                ?.also { _version.update { v -> v + 1 } }
        }
        if (req.kind == Kind.BIRD) {
            fugleramme(req)?.let { return it }
            if (req.key !in noPlate) return null // offline: wait for the real plate rather than generate one
        }
        return generate(req)
    }

    private fun candidates(req: ArtRequest): List<File> = when (req.kind) {
        Kind.BIRD -> listOf(File(root, "birds/${birdKey(req.key)}.webp"), File(root, "birds-generated/${birdKey(req.key)}.png"))
        else -> listOf(File(root, "${req.kind.dir}/${slug(req.key)}.png"))
    }

    /** "Turdus merula" -> "turdus-merula", under the name fugleramme files the plate today. */
    private fun birdKey(sci: String) = slug(aliases[sci] ?: sci)

    private suspend fun fugleramme(req: ArtRequest): File? {
        val target = candidates(req).first()
        if (recentlyFailed("fugl:${req.key}")) return null
        return try {
            val bytes = Http.get("$PLATES/${birdKey(req.key)}.webp")
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            _version.update { it + 1 }
            target
        } catch (e: Exception) {
            failedAt["fugl:${req.key}"] = System.currentTimeMillis() + if (e is HttpError) DAY_MS else 0
            if (e is HttpError && e.code == 404) noPlate += req.key
            null
        }
    }

    /**
     * Each picture is paid for once. The request runs in the app's scope, not the screen's, so
     * tapping to the next plate mid-generation cannot throw away an image the API already billed;
     * a second caller for the same picture waits on the first. The image as it came from the API
     * is kept in `source/` before cutting, so a failed cut is redone from it rather than re-bought.
     */
    private suspend fun generate(req: ArtRequest): File? {
        val target = if (req.kind == Kind.BIRD) candidates(req)[1] else candidates(req)[0]
        if (target.exists()) return target
        val job = synchronized(inflight) {
            inflight.getOrPut(target.path) {
                scope.async { lock.withLock { produce(req, target) } }
                    .also { it.invokeOnCompletion { synchronized(inflight) { inflight.remove(target.path) } } }
            }
        }
        return job.await()
    }

    private suspend fun produce(req: ArtRequest, target: File): File? {
        if (target.exists()) return target
        val source = File(root, "source/${target.parentFile!!.name}/${target.name}")
        val s = settings.value
        if (!source.exists() && (s.imageProvider == "none" || recentlyFailed("gen:${req.key}"))) return null
        return try {
            if (!source.exists()) {
                val wide = req.kind == Kind.PLANE
                val bytes = when (s.imageProvider) {
                    "gemini" -> gemini(s.geminiKey, s.geminiModel, prompt(req), wide)
                    "openrouter" -> openRouter(s.openRouterKey, s.openRouterModel, prompt(req), wide)
                    else -> openAi(s.openAiKey, s.openAiModel, prompt(req), wide)
                } ?: return null
                source.parentFile?.mkdirs()
                source.writeBytes(bytes)
            }
            withContext(Dispatchers.Default) {
                val raw = BitmapFactory.decodeFile(source.path) ?: error("Nieczytelny obraz")
                val cut = Cutout.fromPaper(raw)
                target.parentFile?.mkdirs()
                val tmp = File(target.path + ".tmp")
                tmp.outputStream().use { cut.compress(Bitmap.CompressFormat.PNG, 100, it) }
                tmp.renameTo(target)
            }
            _error.value = null
            _version.update { it + 1 }
            target
        } catch (e: Exception) {
            failedAt["gen:${req.key}"] = System.currentTimeMillis()
            _error.value = "Generowanie ryciny (${req.subject}): ${e.message}"
            null
        }
    }

    /** How many pictures are cached on the phone, and how many of them were generated. */
    fun counts(): Pair<Int, Int> {
        val all = listOf("birds", "birds-generated", "planes", "dogs", "scenes").sumOf { File(root, it).list()?.size ?: 0 }
        val generated = generatedDirs.sumOf { it.list()?.size ?: 0 }
        return all to generated
    }

    fun prompt(req: ArtRequest): String {
        fun t(key: String) = style[key]?.jsonPrimitive?.contentOrNull.orEmpty()
        val subject = when (req.kind) {
            Kind.PLANE -> t("plane").replace("{aircraft}", "a ${req.subject}")
                .replace("{livery}", if (req.airline.isBlank()) "" else t("livery").replace("{airline}", req.airline))
            Kind.DOG -> t("dog").replace("{breed}", req.subject)
            Kind.BIRD -> t("bird").replace("{bird}", "the bird species ${req.subject}")
            Kind.SCENE -> req.prompt
        }
        return "${t("base")} $subject"
    }

    private suspend fun openAi(key: String, model: String, prompt: String, wide: Boolean): ByteArray? {
        if (key.isBlank()) return null
        val body = buildJsonObject {
            put("model", model)
            put("prompt", prompt)
            put("n", 1)
            put("size", if (wide) "1536x1024" else "1024x1024")
            if (model.startsWith("dall-e")) put("response_format", "b64_json") else put("quality", "medium")
        }
        val res = Http.post("https://api.openai.com/v1/images/generations", body.toString(), mapOf("Authorization" to "Bearer $key"))
        val b64 = json.parseToJsonElement(res.decodeToString()).jsonObject["data"]!!.jsonArray[0]
            .jsonObject["b64_json"]!!.jsonPrimitive.content
        return Base64.decode(b64, Base64.DEFAULT)
    }

    private suspend fun gemini(key: String, model: String, prompt: String, wide: Boolean): ByteArray? {
        if (key.isBlank()) return null
        val body = buildJsonObject {
            putJsonArray("contents") { add(buildJsonObject { putJsonArray("parts") { add(buildJsonObject { put("text", prompt) }) } }) }
            putJsonObject("generationConfig") {
                putJsonArray("responseModalities") { add("IMAGE") }
                putJsonObject("imageConfig") { put("aspectRatio", if (wide) "3:2" else "1:1") }
            }
        }
        val res = Http.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            body.toString(),
            mapOf("x-goog-api-key" to key),
        )
        val parts = json.parseToJsonElement(res.decodeToString()).jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts") as? JsonArray ?: error("Gemini nie zwrócił obrazu")
        val data = parts.firstNotNullOfOrNull { (it.jsonObject["inlineData"] as? JsonObject)?.get("data")?.jsonPrimitive?.content }
            ?: error("Gemini nie zwrócił obrazu")
        return Base64.decode(data, Base64.DEFAULT)
    }

    /**
     * OpenRouter serves image models (Gemini, GPT-image, FLUX…) through its chat endpoint: the
     * picture comes back in `message.images` as a data URL, or for some models a plain link.
     */
    private suspend fun openRouter(key: String, model: String, prompt: String, wide: Boolean): ByteArray? {
        if (key.isBlank()) return null
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") { add(buildJsonObject { put("role", "user"); put("content", prompt) }) }
            putJsonArray("modalities") { add("image"); add("text") }
            putJsonObject("image_config") { put("aspect_ratio", if (wide) "3:2" else "1:1") }
        }
        val res = Http.post(
            "https://openrouter.ai/api/v1/chat/completions",
            body.toString(),
            mapOf("Authorization" to "Bearer $key", "X-Title" to "Ryciny"),
        )
        val message = json.parseToJsonElement(res.decodeToString()).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject ?: error("OpenRouter nie zwrócił obrazu")
        val url = (message["images"] as? JsonArray)?.firstNotNullOfOrNull {
            ((it as? JsonObject)?.get("image_url") as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull
        } ?: error("OpenRouter nie zwrócił obrazu (czy model $model generuje obrazy?)")
        return if (url.startsWith("data:")) Base64.decode(url.substringAfter(","), Base64.DEFAULT) else Http.get(url)
    }

    private fun recentlyFailed(key: String) = (failedAt[key] ?: 0L) + RETRY_MS > System.currentTimeMillis()

    private fun asset(name: String) = context.assets.open(name).bufferedReader().readText()

    companion object {
        private const val PLATES = "https://raw.githubusercontent.com/arnegiacomo/fugleramme/main/assets/artwork/classic/birds"
        private const val RETRY_MS = 10 * 60_000L
        private const val DAY_MS = 24 * 3600_000L

        fun slug(s: String) = s.lowercase()
            .replace("ą", "a").replace("ć", "c").replace("ę", "e").replace("ł", "l").replace("ń", "n")
            .replace("ó", "o").replace("ś", "s").replace("ź", "z").replace("ż", "z")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
