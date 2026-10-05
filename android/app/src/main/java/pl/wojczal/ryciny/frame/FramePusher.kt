package pl.wojczal.ryciny.frame

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.wojczal.ryciny.Graph
import pl.wojczal.ryciny.ui.hhmm
import pl.wojczal.ryciny.ui.framePick
import pl.wojczal.ryciny.ui.plateSubjects

data class FrameState(val busy: Boolean = false, val status: String = "", val sentAt: Long = 0)

/**
 * Keeps Samsung The Frame showing the plate: when the newest thing on the plate changes, it is drawn at
 * 3840×2160 and sent to the TV over the home network, at most once per [pl.wojczal.ryciny.data.Settings.frameEveryMin],
 * replacing (and deleting) the previous upload so the TV's storage does not fill up.
 */
class FramePusher(private val g: Graph) {
    private val state = MutableStateFlow(FrameState())
    val flow: StateFlow<FrameState> = state
    private val lock = Mutex()
    private var lastIdentity: String? = null

    init {
        g.scope.launch {
            while (true) {
                delay(60_000)
                val s = g.settings.value
                if (s.frameOn && s.frameHost.isNotBlank()) runCatching { push(force = false) }
            }
        }
    }

    /** The picture that would go to the TV now, for the preview in the settings. */
    suspend fun preview(): Bitmap = withContext(Dispatchers.Default) {
        PlateImage.render(g.context, g.art, current()?.content(g.catalog), 1920, 1080)
    }

    suspend fun push(force: Boolean) = lock.withLock {
        val s = g.settings.value
        if (s.frameHost.isBlank()) {
            state.value = FrameState(status = "wpisz adres IP telewizora")
            return@withLock
        }
        val subject = current()
        val identity = subject?.identity ?: "empty"
        val now = System.currentTimeMillis()
        if (!force && identity == lastIdentity) return@withLock
        if (!force && now - state.value.sentAt < s.frameEveryMin * 60_000L) return@withLock

        state.value = state.value.copy(busy = true, status = if (s.frameToken.isBlank()) "łączę; jeśli telewizor zapyta, zezwól pilotem na „Ryciny”" else "wysyłam…")
        try {
            val content = subject?.content(g.catalog)
            val jpeg = withContext(Dispatchers.Default) { PlateImage.jpeg(PlateImage.render(g.context, g.art, content)) }
            val frame = SamsungFrame(s.frameHost.trim(), s.frameToken.ifBlank { null }) { token: String -> g.settings.update { it.copy(frameToken = token) } }
            val id = frame.show(jpeg, s.frameContentId.ifBlank { null })
            g.settings.update { it.copy(frameContentId = id) }
            lastIdentity = identity
            state.value = FrameState(status = "wysłano o ${hhmm(now)}: ${content?.title ?: "pusta rycina"} (${jpeg.size / 1024} kB)", sentAt = now)
        } catch (e: TimeoutCancellationException) {
            state.value = state.value.copy(busy = false, status = "telewizor nie odpowiedział na czas; czy jest włączony albo w Art Mode?")
        } catch (e: Exception) {
            Log.w(TAG, "push", e)
            state.value = state.value.copy(busy = false, status = "błąd: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun current(): pl.wojczal.ryciny.ui.Subject? {
        val now = System.currentTimeMillis()
        val s = g.settings.value
        // A picture on the wall should not go blank after a quiet night: look back as far as the journal keeps (7 days).
        val week = s.copy(lookbackHours = 7 * 24)
        return framePick(plateSubjects(g, g.store.value, g.sky.flow.value, week, now), s.frameKinds, now)
    }

    companion object {
        private const val TAG = "FramePusher"
    }
}
