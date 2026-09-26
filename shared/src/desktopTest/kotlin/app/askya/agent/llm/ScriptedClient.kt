package app.askya.agent.llm

/**
 * Модель по сценарию — только для тестов.
 *
 * Шаги отвечают по очереди, по одному на запрос. Каждый шаг видит запрос
 * целиком: так тест проверяет, что именно ушло модели, и может в середине
 * хода что-то поменять (например, выключить облако). Все запросы
 * запоминаются в [requests].
 */
class ScriptedClient(
    override val online: Boolean,
    private vararg val steps: suspend (LlmRequest) -> LlmReply,
) : LlmClient {

    override val id = if (online) "scripted:cloud" else "scripted:local"

    val requests = mutableListOf<LlmRequest>()

    override suspend fun next(request: LlmRequest): LlmReply {
        requests += request
        val step = steps.getOrNull(requests.size - 1) ?: error("сценарий кончился на запросе ${requests.size}")
        return step(request)
    }

    companion object {
        fun text(text: String): LlmReply = LlmReply.Turn(listOf(LlmPart.Text(text)))

        fun call(id: String, name: String, vararg input: Pair<String, Any?>): LlmReply =
            LlmReply.Turn(listOf(LlmPart.ToolCall(id, name, mapOf(*input))))

        fun calls(vararg calls: LlmPart.ToolCall): LlmReply = LlmReply.Turn(calls.toList())
    }
}

/** Результаты инструментов в последнем сообщении запроса. */
val LlmRequest.lastResults: List<LlmPart.ToolResult>
    get() = messages.last().parts.filterIsInstance<LlmPart.ToolResult>()
