package pl.wojczal.ryciny.frame

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A stand-in for The Frame that follows the art-app protocol as samsungtvws implements it: TLS WebSocket,
 * pairing token, ready event, send_image → ready_to_use with a D2D socket, image bytes, image_added.
 */
class SamsungFrameTest {
    private val server = MockWebServer()
    private val d2d = ServerSocket(0)
    private val requests = CopyOnWriteArrayList<String>()
    private var received = ByteArray(0)
    private var deleted = ""

    @After fun stop() {
        server.shutdown()
        d2d.close()
    }

    @Test
    fun pairsUploadsShowsAndReplaces() = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(), false)
        server.enqueue(MockResponse().withWebSocketUpgrade(tv()))
        server.start()

        var token = ""
        val jpeg = ByteArray(150_000) { (it % 251).toByte() }
        val id = SamsungFrame("localhost", null, server.port) { token = it }.show(jpeg, replace = "MY_F0001")

        assertEquals("MY_F0002", id)
        assertEquals("T0KEN", token)
        assertArrayEquals(jpeg, received)
        assertEquals(listOf("send_image", "select_image", "delete_image_list"), requests)
        assertEquals("MY_F0001", deleted)
    }

    private fun tv() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"event":"ms.channel.connect","data":{"token":"T0KEN"}}""")
            webSocket.send("""{"event":"ms.channel.ready","data":{}}""")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val params = Json.parseToJsonElement(text).jsonObject["params"]!!.jsonObject
            val data = Json.parseToJsonElement(params["data"]!!.jsonPrimitive.content).jsonObject
            val request = data["request"]!!.jsonPrimitive.content
            val id = data["request_id"]!!.jsonPrimitive.content
            requests += request
            when (request) {
                "send_image" -> {
                    val size = data["file_size"]!!.jsonPrimitive.content.toInt()
                    thread {
                        d2d.accept().use { s ->
                            val input = DataInputStream(s.getInputStream())
                            val header = ByteArray(input.readInt()).also { input.readFully(it) }
                            val h = Json.parseToJsonElement(String(header)).jsonObject
                            check(h["secKey"]!!.jsonPrimitive.content == "K3Y" && h["fileLength"]!!.jsonPrimitive.content.toInt() == size)
                            received = ByteArray(size).also { input.readFully(it) }
                        }
                        reply(webSocket, """{"event":"image_added","content_id":"MY_F0002"}""")
                    }
                    val info = """{"ip":"127.0.0.1","port":${d2d.localPort},"key":"K3Y","secured":false}"""
                    reply(webSocket, """{"event":"ready_to_use","request_id":"$id","conn_info":${kotlinx.serialization.json.JsonPrimitive(info).toString()}}""")
                }
                "select_image" -> reply(webSocket, """{"event":"select_image","request_id":"$id"}""")
                "delete_image_list" -> {
                    deleted = (data["content_id_list"]!! as kotlinx.serialization.json.JsonArray)[0].jsonObject["content_id"]!!.jsonPrimitive.content
                    reply(webSocket, """{"event":"delete_image_list","request_id":"$id","content_id_list":"[]"}""")
                }
            }
        }
    }

    private fun reply(ws: WebSocket, payload: String) {
        ws.send(JsonObject(mapOf("event" to kotlinx.serialization.json.JsonPrimitive("d2d_service_message"), "data" to kotlinx.serialization.json.JsonPrimitive(payload))).toString())
    }
}
