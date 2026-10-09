package pl.wojczal.ryciny.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpError(val code: Int, message: String) : IOException("HTTP $code: $message")

object Http {
    private const val AGENT = "Ryciny/0.1 (Android; personal bird, dog and aircraft frame)"

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): ByteArray = request("GET", url, headers, null)

    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): ByteArray =
        request("POST", url, headers + ("Content-Type" to "application/json"), body)

    class Reply(val code: Int, val body: ByteArray, val headers: Map<String, String>)

    /** Any method, any status: the caller decides what 202, 304 or 404 mean (the Home Assistant add-on's API). */
    suspend fun exchange(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap()): Reply =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 8_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("User-Agent", AGENT)
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                val h = conn.headerFields.filterKeys { it != null }.mapValues { it.value.firstOrNull().orEmpty() }.mapKeys { it.key.lowercase() }
                Reply(code, bytes, h)
            } finally {
                conn.disconnect()
            }
        }

    private suspend fun request(method: String, url: String, headers: Map<String, String>, body: String?): ByteArray =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = if (body != null) 180_000 else 20_000
                conn.setRequestProperty("User-Agent", AGENT)
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
                    throw HttpError(code, err.take(400))
                }
                conn.inputStream.use { it.readBytes() }
            } finally {
                conn.disconnect()
            }
        }
}
