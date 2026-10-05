package pl.wojczal.ryciny.frame

import java.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import pl.wojczal.ryciny.data.json
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class FrameError(message: String) : IOException(message)

/**
 * Samsung The Frame's local Art Mode API, the one Home Assistant's Frame integrations use (via samsungtvws):
 * a WebSocket on the art-app channel for requests, and a plain TCP "D2D" socket the TV opens for the image
 * bytes. Port 8002 is TLS with the TV's self-signed certificate, so certificates are not checked; this talks
 * only to the address typed into the settings, on the home network.
 *
 * The first connection makes the TV ask, on screen, whether to allow "Ryciny": accept it with the remote.
 * The TV then hands out a token, which [onToken] stores so it does not ask again.
 */
class SamsungFrame(
    private val host: String,
    private val token: String?,
    private val port: Int = 8002,
    private val onToken: (String) -> Unit,
) {
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    private val tls: SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
    }.socketFactory
    private val client = OkHttpClient.Builder()
        .sslSocketFactory(tls, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** Uploads [jpeg], shows it in Art Mode, deletes [replace] (the previous upload) and returns the new content id. */
    suspend fun show(jpeg: ByteArray, replace: String?): String = withContext(Dispatchers.IO) {
        Session().use { s ->
            s.open()
            val id = s.upload(jpeg)
            s.request("select_image", "content_id" to JsonPrimitive(id), "show" to JsonPrimitive(true))
            if (!replace.isNullOrBlank() && replace != id) {
                runCatching {
                    s.request(
                        "delete_image_list",
                        "content_id_list" to kotlinx.serialization.json.buildJsonArray { addJsonObject { put("content_id", replace) } },
                    )
                }.onFailure { Log.w(TAG, "could not delete $replace", it) }
            }
            id
        }
    }

    private inner class Session : AutoCloseable {
        private val incoming = Channel<JsonObject>(Channel.UNLIMITED)
        private var socket: WebSocket? = null

        suspend fun open() {
            val name = Base64.getEncoder().encodeToString("Ryciny".toByteArray())
            val url = "wss://$host:$port/api/v2/channels/com.samsung.art-app?name=$name" + (token?.let { "&token=$it" } ?: "")
            socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { incoming.trySend(json.parseToJsonElement(text).jsonObject) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    incoming.close(FrameError("telewizor nie odpowiada pod $host (${t.message ?: t.javaClass.simpleName})"))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    incoming.close(FrameError("telewizor zamknął połączenie ($code $reason)"))
                }
            })
            // Pairing waits on a person with a remote, so the first event gets a minute.
            val connect = next(60_000) { it.event == "ms.channel.connect" || it.event == "ms.channel.unauthorized" }
            if (connect.event == "ms.channel.unauthorized") throw FrameError("telewizor odmówił dostępu: zezwól na „Ryciny” w ustawieniach urządzeń zewnętrznych")
            (connect["data"] as? JsonObject)?.get("token")?.jsonPrimitive?.contentOrNull?.let { if (it != token) onToken(it) }
            next(15_000) { it.event == "ms.channel.ready" }
        }

        /** Sends one art request and waits for the TV's answer to it (or for [waitFor] among its answers). */
        suspend fun request(request: String, vararg params: Pair<String, JsonElement>, waitFor: String? = null, id: String = UUID.randomUUID().toString()): JsonObject {
            val data = buildJsonObject {
                put("request", request)
                params.forEach { (k, v) -> put(k, v) }
                put("id", id)
                put("request_id", id)
            }
            val message = buildJsonObject {
                put("method", "ms.channel.emit")
                putJsonObject("params") {
                    put("event", "art_app_request")
                    put("to", "host")
                    put("data", data.toString())
                }
            }
            socket?.send(message.toString()) ?: throw FrameError("brak połączenia")
            return answer(id, waitFor)
        }

        /** Image upload: ask for a D2D slot, push header and bytes over the socket, wait for "image_added". */
        suspend fun upload(jpeg: ByteArray): String {
            val id = UUID.randomUUID().toString()
            val ready = request(
                "send_image",
                "file_type" to JsonPrimitive("jpg"),
                "file_size" to JsonPrimitive(jpeg.size),
                "image_date" to JsonPrimitive(SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date())),
                // No mat: the plate carries its own paper and border.
                "matte_id" to JsonPrimitive("none"),
                "portrait_matte_id" to JsonPrimitive("none"),
                "conn_info" to buildJsonObject {
                    put("d2d_mode", "socket")
                    put("connection_id", (Math.random() * 4.0 * 1024 * 1024 * 1024).toLong())
                    put("id", id)
                },
                waitFor = "ready_to_use",
                id = id,
            )
            val info = ready["conn_info"].let { c ->
                (c as? JsonObject) ?: json.parseToJsonElement(c?.jsonPrimitive?.content ?: error("brak conn_info")).jsonObject
            }
            val header = buildJsonObject {
                put("num", 0)
                put("total", 1)
                put("fileLength", jpeg.size)
                put("fileName", "ryciny")
                put("fileType", "jpg")
                put("secKey", info["key"]!!.jsonPrimitive.content)
                put("version", "0.0.1")
            }.toString().toByteArray(Charsets.US_ASCII)
            val ip = info["ip"]!!.jsonPrimitive.content
            val port = info["port"]!!.jsonPrimitive.content.toInt()
            val secured = info["secured"]?.jsonPrimitive?.booleanOrNull ?: false
            val raw = Socket().apply { connect(InetSocketAddress(ip, port), 8_000); soTimeout = 15_000 }
            val sock = if (secured) tls.createSocket(raw, ip, port, true) else raw
            sock.use {
                val out = it.getOutputStream()
                out.write(java.nio.ByteBuffer.allocate(4).putInt(header.size).array())
                out.write(header)
                out.write(jpeg)
                out.flush()
            }
            val added = answer(null, "image_added")
            return added["content_id"]?.jsonPrimitive?.content ?: throw FrameError("telewizor nie podał id obrazu")
        }

        /** The decoded d2d_service_message for request [id] (any request when null), optionally of sub-event [event]. */
        private suspend fun answer(id: String?, event: String?): JsonObject {
            while (true) {
                val frame = next(30_000) { it.event == "d2d_service_message" }
                val payload = (frame["data"] as? JsonPrimitive)?.contentOrNull?.let { json.parseToJsonElement(it).jsonObject } ?: continue
                val msgId = payload["request_id"]?.jsonPrimitive?.contentOrNull ?: payload["id"]?.jsonPrimitive?.contentOrNull
                if (id != null && msgId != id) continue
                val sub = payload["event"]?.jsonPrimitive?.contentOrNull
                if (sub == "error") throw FrameError("telewizor zwrócił błąd ${payload["error_code"]?.jsonPrimitive?.contentOrNull ?: "?"}")
                if (event == null || sub == event) return payload
            }
        }

        private suspend fun next(timeoutMs: Long, match: (JsonObject) -> Boolean): JsonObject = withTimeout(timeoutMs) {
            while (true) {
                val frame = incoming.receive()
                Log.d(TAG, "event ${frame.event}")
                if (frame.event == "ms.error") throw FrameError("telewizor: ${(frame["data"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull}")
                if (match(frame)) return@withTimeout frame
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }

        override fun close() {
            socket?.close(1000, null)
            incoming.close()
        }
    }

    private val JsonObject.event: String? get() = this["event"]?.jsonPrimitive?.contentOrNull

    companion object {
        private const val TAG = "SamsungFrame"
    }
}
