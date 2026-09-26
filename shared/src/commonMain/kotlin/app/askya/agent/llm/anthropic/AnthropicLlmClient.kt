package app.askya.agent.llm.anthropic

import app.askya.agent.llm.LlmClient
import app.askya.agent.llm.LlmMessage
import app.askya.agent.llm.LlmPart
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.LlmRequest
import app.askya.agent.llm.Role
import app.askya.data.sync.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * Что нужно клиенту Anthropic. Ключ — только здесь: ни в сессии, ни в
 * политике, ни в контексте его нет, и в строку объекта он не выводится.
 */
class AnthropicConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = DEFAULT_BASE_URL,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    init {
        require(apiKey.isNotBlank()) { "нет ключа" }
        require(model.isNotBlank()) { "нет модели" }
        require(maxTokens > 0) { "maxTokens должен быть больше нуля" }
    }

    override fun toString(): String = "AnthropicConfig(model=$model, baseUrl=$baseUrl, maxTokens=$maxTokens, apiKey=***)"

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"

        /** С запасом на рассуждение, которое у модели включено само. */
        const val DEFAULT_MAX_TOKENS = 16_000
    }
}

/**
 * Claude через Messages API (`POST /v1/messages`), без библиотек.
 *
 * Всё, что знает об Anthropic, — здесь: формат сообщений, `tool_use` и
 * `tool_result`, блоки рассуждения, `stop_reason`, коды ответа, ключ.
 * Сессия видит только [LlmRequest] и [LlmReply].
 *
 * **Блоки рассуждения** (`thinking` с подписью, `redacted_thinking`) и любые
 * другие незнакомые блоки ответа становятся [LlmPart.Opaque] целиком и в
 * следующем запросе уходят обратно ровно такими же и на своём месте: провайдер
 * проверяет, что их не меняли.
 *
 * **Причина остановки:**
 * - `end_turn`, `stop_sequence`, `tool_use` — обычный ответ;
 * - `max_tokens` — [LlmReply.Failure]: оборванный вызов инструмента выполнять
 *   нельзя, а оборванный текст выглядел бы готовым ответом;
 * - `refusal` — если есть текст и нет вызовов, это ответ; иначе
 *   [LlmReply.Failure]: вызов, оборванный отказом, выполнять нельзя;
 * - `model_context_window_exceeded` — разговор стал слишком длинным;
 * - остальное (`pause_turn` у серверных инструментов, которых здесь нет, и
 *   незнакомое) — непонятный ответ.
 *
 * **Ошибки** — короткими словами для экрана. Ни тела ответа, ни заголовков, ни
 * ключа в них нет. Отмена проходит насквозь. Журнала нет вовсе: ни запросов,
 * ни ответов, ни блоков рассуждения клиент не пишет.
 */
class AnthropicLlmClient internal constructor(
    private val config: AnthropicConfig,
    private val transport: HttpTransport,
) : LlmClient {

    constructor(config: AnthropicConfig) : this(config, UrlConnectionTransport())

    override val id: String = "anthropic:${config.model}"

    override val online: Boolean = true

    private val _lastCall = MutableStateFlow<CallTrace?>(null)

    /**
     * Последний запрос — для ручной проверки «ввод → API → ответ» на экране.
     * Только клиент, код ответа, длительность и исход: ни запроса, ни ответа,
     * ни ключа. В журнал не пишется.
     */
    val lastCall: StateFlow<CallTrace?> = _lastCall.asStateFlow()

    override suspend fun next(request: LlmRequest): LlmReply {
        val body = try {
            Json.write(AnthropicFormat.body(config, request))
        } catch (_: IllegalArgumentException) {
            return LlmReply.Failure(BAD_REQUEST)
        }
        val started = System.nanoTime()
        fun took() = (System.nanoTime() - started) / 1_000_000
        val answer = try {
            transport.post(config.baseUrl.trimEnd('/') + PATH, headers(), body)
        } catch (cancel: CancellationException) {
            _lastCall.value = CallTrace(id, status = null, durationMs = took(), outcome = CallTrace.Outcome.CANCELLED)
            throw cancel
        } catch (_: IOException) {
            _lastCall.value = CallTrace(id, status = null, durationMs = took(), outcome = CallTrace.Outcome.FAILED)
            return LlmReply.Failure(NO_CONNECTION)
        }
        val reply = when (answer.status) {
            in 200..299 -> AnthropicFormat.reply(answer.body)
            401, 403 -> LlmReply.Failure(AUTH)
            429 -> LlmReply.Failure(BUSY)
            in 500..599 -> LlmReply.Failure(UNAVAILABLE)
            else -> LlmReply.Failure(REJECTED)
        }
        _lastCall.value = CallTrace(
            clientId = id,
            status = answer.status,
            durationMs = took(),
            outcome = if (reply is LlmReply.Turn) CallTrace.Outcome.OK else CallTrace.Outcome.FAILED,
        )
        return reply
    }

    private fun headers(): Map<String, String> = mapOf(
        "x-api-key" to config.apiKey,
        "anthropic-version" to API_VERSION,
        "content-type" to "application/json",
    )

    override fun toString(): String = "AnthropicLlmClient($id)"

    internal companion object {
        const val PATH = "/v1/messages"
        const val API_VERSION = "2023-06-01"

        const val NO_CONNECTION = "нет связи с Claude"
        const val AUTH = "ключ Claude не подходит"
        const val BUSY = "Claude перегружен — попробуйте чуть позже"
        const val UNAVAILABLE = "сервис Claude сейчас недоступен"
        const val REJECTED = "Claude не принял запрос"
        const val BAD_REQUEST = "запрос к Claude собран неверно"
        const val MALFORMED = "непонятный ответ Claude"
        const val CUT_OFF = "ответ Claude оборвался"
        const val REFUSED = "Claude отказался отвечать"
        const val TOO_LONG = "разговор стал слишком длинным — начните новый"
    }
}

/**
 * Что известно о запросе без его содержимого: кто, код ответа (`null` — до
 * ответа не дошло), сколько шёл и чем кончился.
 */
data class CallTrace(
    val clientId: String,
    val status: Int?,
    val durationMs: Long,
    val outcome: Outcome,
) {
    enum class Outcome { OK, FAILED, CANCELLED }
}

/** Перевод между контрактом агента и форматом Messages API — туда и обратно. */
internal object AnthropicFormat {

    /** Тело запроса. Незнакомое сочетание частей — [IllegalArgumentException]. */
    fun body(config: AnthropicConfig, request: LlmRequest): Map<String, Any?> = buildMap {
        put("model", config.model)
        put("max_tokens", config.maxTokens)
        if (request.system.isNotEmpty()) put("system", request.system)
        put("messages", request.messages.map(::message))
        if (request.tools.isNotEmpty()) {
            put(
                "tools",
                request.tools.map { spec ->
                    // Схема — та же, что описал инструмент: без переписывания.
                    mapOf("name" to spec.name, "description" to spec.description, "input_schema" to spec.inputSchema)
                },
            )
        }
    }

    private fun message(message: LlmMessage): Map<String, Any?> = mapOf(
        "role" to when (message.role) {
            Role.USER -> "user"
            Role.ASSISTANT -> "assistant"
        },
        "content" to message.parts.map { block(it, message.role) },
    )

    private fun block(part: LlmPart, role: Role): Map<String, Any?> = when (part) {
        is LlmPart.Text -> mapOf("type" to "text", "text" to part.text)
        is LlmPart.ToolCall -> {
            require(role == Role.ASSISTANT) { "вызов инструмента — только в реплике модели" }
            mapOf("type" to "tool_use", "id" to part.id, "name" to part.name, "input" to part.input)
        }
        is LlmPart.ToolResult -> {
            require(role == Role.USER) { "ответ инструмента — только в реплике человека" }
            buildMap {
                put("type", "tool_result")
                put("tool_use_id", part.callId)
                put("content", part.content)
                if (part.isError) put("is_error", true)
            }
        }
        // Как пришло от провайдера — так и уходит.
        is LlmPart.Opaque -> part.raw
    }

    /** Ответ сервера с кодом 2xx. */
    fun reply(body: String): LlmReply {
        val parts: List<LlmPart>
        val stop: Any?
        try {
            val root = Json.read(body) as? Map<*, *> ?: return LlmReply.Failure(AnthropicLlmClient.MALFORMED)
            val content = root["content"] as? List<*> ?: return LlmReply.Failure(AnthropicLlmClient.MALFORMED)
            parts = content.map(::part)
            stop = root["stop_reason"]
        } catch (_: IllegalArgumentException) {
            return LlmReply.Failure(AnthropicLlmClient.MALFORMED)
        }
        return when (stop) {
            "end_turn", "stop_sequence", "tool_use" -> LlmReply.Turn(parts)
            "max_tokens" -> LlmReply.Failure(AnthropicLlmClient.CUT_OFF)
            "model_context_window_exceeded" -> LlmReply.Failure(AnthropicLlmClient.TOO_LONG)
            "refusal" -> {
                val answered = parts.none { it is LlmPart.ToolCall } &&
                    parts.any { it is LlmPart.Text && it.text.isNotBlank() }
                if (answered) LlmReply.Turn(parts) else LlmReply.Failure(AnthropicLlmClient.REFUSED)
            }
            else -> LlmReply.Failure(AnthropicLlmClient.MALFORMED)
        }
    }

    /** Блок ответа. Кривой блок — [IllegalArgumentException]. */
    private fun part(raw: Any?): LlmPart {
        val block = raw as? Map<*, *> ?: throw IllegalArgumentException("блок не объект")
        return when (block["type"]) {
            "text" -> LlmPart.Text(block["text"] as? String ?: throw IllegalArgumentException("нет text"))
            "tool_use" -> LlmPart.ToolCall(
                id = block["id"] as? String ?: throw IllegalArgumentException("нет id"),
                name = block["name"] as? String ?: throw IllegalArgumentException("нет name"),
                input = stringKeys(block["input"] as? Map<*, *> ?: throw IllegalArgumentException("нет input")),
            )
            // thinking, redacted_thinking и всё незнакомое — целиком, не толкуя.
            else -> LlmPart.Opaque(stringKeys(block))
        }
    }

    private fun stringKeys(map: Map<*, *>): Map<String, Any?> =
        map.entries.associate { (key, value) -> (key as? String ?: throw IllegalArgumentException("ключ не строка")) to value }
}
