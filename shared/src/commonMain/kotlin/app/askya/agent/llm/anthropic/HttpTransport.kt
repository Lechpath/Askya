package app.askya.agent.llm.anthropic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Один POST — всё, что клиенту модели нужно от сети.
 *
 * Отдельно от клиента, чтобы разбор запросов и ответов проверялся без сети.
 * Внутреннее: подставить свой транспорт снаружи нельзя, и чужому коду не
 * через что подсмотреть заголовки с ключом.
 */
internal fun interface HttpTransport {
    /** Ответ сервера — любой код, тело как есть. Сеть и таймаут — [java.io.IOException]. */
    suspend fun post(url: String, headers: Map<String, String>, body: String): HttpAnswer
}

/** Код и тело ответа. Тело в строку объекта не выводится: в нём бывает что угодно. */
internal class HttpAnswer(val status: Int, val body: String) {
    override fun toString(): String = "HttpAnswer(status=$status)"
}

/**
 * Транспорт на `HttpURLConnection` — тем же, чем ходят погода и проверка
 * обновлений, без библиотек.
 *
 * Отмена хода обрывает запрос по-настоящему. Чтение из сокета
 * `HttpURLConnection` на прерывание потока не отзывается, поэтому обмен идёт
 * в отдельной корутине на `Dispatchers.IO`, а при отмене соединение
 * закрывается — заблокированное чтение падает, и ход кончается сразу, а не
 * через таймаут.
 */
internal class UrlConnectionTransport(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) : HttpTransport {

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpAnswer = coroutineScope {
        val connection = URL(url).openConnection() as HttpURLConnection
        val exchange = async(Dispatchers.IO) {
            try {
                exchange(connection, headers, body)
            } catch (broken: IOException) {
                // Соединение закрыли мы сами, отменяя ход, — это отмена, а не
                // сбой сети: иначе ошибка сокета перебила бы отмену.
                if (!isActive) throw CancellationException("запрос оборван отменой")
                throw broken
            }
        }
        try {
            exchange.await()
        } catch (cancel: CancellationException) {
            runCatching { connection.disconnect() }
            throw cancel
        }
    }

    private fun exchange(connection: HttpURLConnection, headers: Map<String, String>, body: String): HttpAnswer {
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.doOutput = true
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpAnswer(status, text)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000

        /** Модель с рассуждением отвечает не сразу — минута бывает мало. */
        const val READ_TIMEOUT_MS = 300_000
    }
}
