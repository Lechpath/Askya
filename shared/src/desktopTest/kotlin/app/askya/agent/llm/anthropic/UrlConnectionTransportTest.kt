package app.askya.agent.llm.anthropic

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Настоящий транспорт на `HttpURLConnection` против сервера на этом же
 * компьютере: что уходит, что приходит и обрывается ли запрос при отмене.
 */
class UrlConnectionTransportTest {

    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val url get() = "http://127.0.0.1:${server.address.port}/v1/messages"

    @AfterTest
    fun stop() = server.stop(0)

    private fun respond(code: Int, text: String, seen: (headers: Map<String, String>, body: String) -> Unit = { _, _ -> }) {
        server.createContext("/v1/messages") { exchange ->
            val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            seen(exchange.requestHeaders.mapValues { it.value.first() }.mapKeys { it.key.lowercase() }, body)
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @Test
    fun `тело и заголовки уходят, ответ приходит`() = runBlocking {
        var headers = emptyMap<String, String>()
        var body = ""
        respond(200, """{"ok":"да"}""") { h, b -> headers = h; body = b }

        val answer = UrlConnectionTransport().post(url, mapOf("x-api-key" to "k", "content-type" to "application/json"), """{"text":"горы"}""")

        assertEquals(200, answer.status)
        assertEquals("""{"ok":"да"}""", answer.body)
        assertEquals("""{"text":"горы"}""", body)
        assertEquals("k", headers["x-api-key"])
    }

    @Test
    fun `тело ошибки читается, но в строку ответа не выводится`() = runBlocking {
        respond(500, "секрет из тела ошибки")
        val answer = UrlConnectionTransport().post(url, emptyMap(), "{}")
        assertEquals(500, answer.status)
        assertEquals("секрет из тела ошибки", answer.body)
        assertEquals("HttpAnswer(status=500)", answer.toString())
    }

    @Test
    fun `отмена обрывает висящий запрос сразу, а не по таймауту`() = runBlocking {
        val received = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/v1/messages") { exchange ->
            exchange.requestBody.readBytes()
            received.countDown()
            release.await(60, TimeUnit.SECONDS) // сервер «думает» дольше таймаута
            runCatching { exchange.sendResponseHeaders(200, -1); exchange.close() }
        }
        server.executor = java.util.concurrent.Executors.newCachedThreadPool()
        server.start()

        val transport = UrlConnectionTransport(readTimeoutMs = 30_000)
        val call = launch(Dispatchers.Default) { transport.post(url, emptyMap(), "{}") }
        withContext(Dispatchers.IO) { assertTrue(received.await(10, TimeUnit.SECONDS), "запрос дошёл") }

        val started = System.nanoTime()
        call.cancelAndJoin()
        val tookMs = (System.nanoTime() - started) / 1_000_000
        release.countDown()

        assertTrue(call.isCancelled)
        assertTrue(tookMs < 5_000, "отмена заняла $tookMs мс — запрос не оборвали")
    }
}
