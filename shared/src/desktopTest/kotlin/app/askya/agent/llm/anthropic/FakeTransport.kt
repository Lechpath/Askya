package app.askya.agent.llm.anthropic

import app.askya.data.sync.Json

/** Сеть по сценарию: отдаёт заготовленные ответы по очереди и запоминает, что ушло. */
internal class FakeTransport(vararg answers: HttpAnswer) : HttpTransport {

    class Sent(val url: String, val headers: Map<String, String>, val body: String) {
        @Suppress("UNCHECKED_CAST")
        val json: Map<String, Any?> get() = Json.read(body) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val messages: List<Map<String, Any?>> get() = json["messages"] as List<Map<String, Any?>>
    }

    val sent = mutableListOf<Sent>()
    private val queue = ArrayDeque(answers.toList())

    override suspend fun post(url: String, headers: Map<String, String>, body: String): HttpAnswer {
        sent += Sent(url, headers, body)
        return queue.removeFirstOrNull() ?: error("ответы кончились")
    }
}

internal fun ok(json: String) = HttpAnswer(200, json)

@Suppress("UNCHECKED_CAST")
internal fun jsonMap(text: String): Map<String, Any?> = Json.read(text) as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Map<String, Any?>.content(): List<Map<String, Any?>> = this["content"] as List<Map<String, Any?>>
