package app.askya.agent.llm.anthropic

import app.askya.agent.ToolKind
import app.askya.agent.ToolSpec
import app.askya.agent.llm.LlmMessage
import app.askya.agent.llm.LlmPart
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.LlmRequest
import app.askya.agent.llm.Role
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Перевод между контрактом агента и Messages API — без сети. */
class AnthropicLlmClientTest {

    private val key = "sk-ant-test-не-настоящий"
    private val config = AnthropicConfig(apiKey = key, model = "claude-opus-5")

    private val spec = ToolSpec(
        name = "search_notes",
        description = "Ищет заметки",
        inputSchema = mapOf(
            "type" to "object",
            "properties" to mapOf("query" to mapOf("type" to "string"), "limit" to mapOf("type" to "integer", "maximum" to 30)),
            "required" to listOf("query"),
            "additionalProperties" to false,
        ),
        kind = ToolKind.READ,
    )

    private fun user(text: String) = LlmMessage(Role.USER, listOf(LlmPart.Text(text)))
    private fun request(vararg messages: LlmMessage) = LlmRequest("Правила Askya", messages.toList(), listOf(spec))

    private suspend fun reply(json: String): LlmReply =
        AnthropicLlmClient(config, FakeTransport(ok(json))).next(request(user("?")))

    // --- Запрос ---------------------------------------------------------------

    @Test
    fun `запрос — адрес, заголовки, модель, system, сообщения и инструменты`() = runTest {
        val transport = FakeTransport(ok("""{"content":[{"type":"text","text":"ок"}],"stop_reason":"end_turn"}"""))
        AnthropicLlmClient(config, transport).next(request(user("Привет")))

        val sent = transport.sent.single()
        assertEquals("https://api.anthropic.com/v1/messages", sent.url)
        assertEquals(mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01", "content-type" to "application/json"), sent.headers)
        val body = sent.json
        assertEquals("claude-opus-5", body["model"])
        assertEquals(16_000L, body["max_tokens"])
        assertEquals("Правила Askya", body["system"])
        assertEquals(listOf(mapOf("role" to "user", "content" to listOf(mapOf("type" to "text", "text" to "Привет")))), body["messages"])
        // Схема — ровно та, что описал инструмент.
        assertEquals(
            listOf(mapOf("name" to "search_notes", "description" to "Ищет заметки", "input_schema" to jsonMap(app.askya.data.sync.Json.write(spec.inputSchema)))),
            body["tools"],
        )
        assertFalse(key in sent.body, "ключ — только в заголовке")
    }

    @Test
    fun `клиент в сети, ключ не выводится`() {
        val client = AnthropicLlmClient(config)
        assertTrue(client.online)
        assertEquals("anthropic:claude-opus-5", client.id)
        assertFalse(key in client.toString())
        assertFalse(key in config.toString())
    }

    // --- A, K. Текст ------------------------------------------------------------

    @Test
    fun `A и K — текстовый ответ становится Text в Turn`() = runTest {
        val reply = reply("""{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5",
            "content":[{"type":"text","text":"Сегодня три дела."}],"stop_reason":"end_turn","usage":{"input_tokens":10,"output_tokens":5}}""")
        assertEquals(LlmReply.Turn(listOf(LlmPart.Text("Сегодня три дела."))), reply)
    }

    // --- B. Вызов инструмента ------------------------------------------------------

    @Test
    fun `B — tool_use становится ToolCall с id, именем и входом`() = runTest {
        val reply = reply("""{"content":[{"type":"text","text":"Ищу."},
            {"type":"tool_use","id":"toolu_01","name":"search_notes","input":{"query":"горы","limit":5,"extra":{"a":[1,true,null]}}}],
            "stop_reason":"tool_use"}""")
        val turn = assertIs<LlmReply.Turn>(reply)
        assertEquals(LlmPart.Text("Ищу."), turn.parts[0])
        assertEquals(
            LlmPart.ToolCall("toolu_01", "search_notes", mapOf("query" to "горы", "limit" to 5L, "extra" to mapOf("a" to listOf(1L, true, null)))),
            turn.parts[1],
        )
    }

    // --- C. Рассуждение ---------------------------------------------------------

    private val thinking = """{"type":"thinking","thinking":"","signature":"EqQBCkgIBxABGAIiQL+подпись==","future":{"x":[1,2.5]}}"""
    private val redacted = """{"type":"redacted_thinking","data":"EmwKAhgBEgy3va3pzix/LafPsn4a"}"""
    private val unknown = """{"type":"something_new","payload":{"deep":{"list":["a",1,false,null]}}}"""

    @Test
    fun `C — рассуждение и незнакомые блоки целиком в Opaque, текстом не становятся`() = runTest {
        val turn = assertIs<LlmReply.Turn>(
            reply("""{"content":[$thinking,$redacted,$unknown,{"type":"text","text":"Ответ"}],"stop_reason":"end_turn"}"""),
        )
        assertEquals(
            listOf(LlmPart.Opaque(jsonMap(thinking)), LlmPart.Opaque(jsonMap(redacted)), LlmPart.Opaque(jsonMap(unknown)), LlmPart.Text("Ответ")),
            turn.parts,
        )
        assertEquals("EqQBCkgIBxABGAIiQL+подпись==", (turn.parts[0] as LlmPart.Opaque).raw["signature"])
    }

    // --- D. Opaque туда и обратно -------------------------------------------------

    @Test
    fun `D — блоки ответа возвращаются в следующем запросе без потерь и на своих местах`() = runTest {
        val content = """[$thinking,{"type":"text","text":"Сейчас поищу."},$redacted,
            {"type":"tool_use","id":"toolu_07","name":"search_notes","input":{"query":"горы"}}]"""
        val first = assertIs<LlmReply.Turn>(reply("""{"content":$content,"stop_reason":"tool_use"}"""))

        val transport = FakeTransport(ok("""{"content":[{"type":"text","text":"Готово"}],"stop_reason":"end_turn"}"""))
        AnthropicLlmClient(config, transport).next(
            request(
                user("Найди про горы"),
                LlmMessage(Role.ASSISTANT, first.parts),
                LlmMessage(Role.USER, listOf(LlmPart.ToolResult("toolu_07", """{"items":[]}""", isError = false))),
            ),
        )

        val assistant = transport.sent.single().messages[1]
        assertEquals("assistant", assistant["role"])
        @Suppress("UNCHECKED_CAST")
        val original = jsonMap("""{"content":$content}""").content()
        assertEquals(original, assistant.content(), "каждый блок, каждое поле, тот же порядок")
    }

    // --- E, F. Ответы инструментов --------------------------------------------------

    @Test
    fun `E и F — ответы инструментов одним сообщением, по порядку, с tool_use_id`() = runTest {
        val transport = FakeTransport(ok("""{"content":[{"type":"text","text":"ок"}],"stop_reason":"end_turn"}"""))
        val calls = listOf(LlmPart.ToolCall("toolu_a", "get_today", emptyMap()), LlmPart.ToolCall("toolu_b", "search_notes", mapOf("query" to "x")))
        AnthropicLlmClient(config, transport).next(
            request(
                user("?"),
                LlmMessage(Role.ASSISTANT, calls),
                LlmMessage(Role.USER, listOf(LlmPart.ToolResult("toolu_a", """{"deeds":[]}""", false), LlmPart.ToolResult("toolu_b", "не так", true))),
            ),
        )
        val messages = transport.sent.single().messages
        assertEquals(3, messages.size)
        assertEquals(
            mapOf(
                "role" to "user",
                "content" to listOf(
                    mapOf("type" to "tool_result", "tool_use_id" to "toolu_a", "content" to """{"deeds":[]}"""),
                    mapOf("type" to "tool_result", "tool_use_id" to "toolu_b", "content" to "не так", "is_error" to true),
                ),
            ),
            messages[2],
        )
    }

    @Test
    fun `вызов в реплике человека — запрос не уходит`() = runTest {
        val transport = FakeTransport()
        val reply = AnthropicLlmClient(config, transport).next(request(LlmMessage(Role.USER, listOf(LlmPart.ToolCall("x", "y", emptyMap())))))
        assertEquals(LlmReply.Failure(AnthropicLlmClient.BAD_REQUEST), reply)
        assertEquals(0, transport.sent.size)
    }

    // --- G, H. Ошибки -------------------------------------------------------------

    private val secretBody = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key секрет-из-тела"}}"""

    private suspend fun status(code: Int): LlmReply =
        AnthropicLlmClient(config, FakeTransport(HttpAnswer(code, secretBody))).next(request(user("?")))

    @Test
    fun `G — 401 и 403 — ключ не подходит, без тела и без ключа`() = runTest {
        listOf(401, 403).forEach { code ->
            val failure = assertIs<LlmReply.Failure>(status(code))
            assertEquals(AnthropicLlmClient.AUTH, failure.message)
            assertFalse("секрет" in failure.message || key in failure.message || "x-api-key" in failure.message)
        }
    }

    @Test
    fun `H — 500 и прочие коды — коротко и без тела`() = runTest {
        assertEquals(LlmReply.Failure(AnthropicLlmClient.UNAVAILABLE), status(500))
        assertEquals(LlmReply.Failure(AnthropicLlmClient.UNAVAILABLE), status(529))
        assertEquals(LlmReply.Failure(AnthropicLlmClient.BUSY), status(429))
        assertEquals(LlmReply.Failure(AnthropicLlmClient.REJECTED), status(400))
    }

    @Test
    fun `нет сети — своя ошибка`() = runTest {
        val client = AnthropicLlmClient(config) { _, _, _ -> throw IOException("connect timed out api.anthropic.com") }
        assertEquals(LlmReply.Failure(AnthropicLlmClient.NO_CONNECTION), client.next(request(user("?"))))
    }

    @Test
    fun `кривой ответ провайдера`() = runTest {
        listOf(
            "не JSON",
            "[]",
            """{"stop_reason":"end_turn"}""",
            """{"content":"строка","stop_reason":"end_turn"}""",
            """{"content":[{"type":"tool_use","id":"t","name":"n"}],"stop_reason":"tool_use"}""",
            """{"content":[{"type":"text"}],"stop_reason":"end_turn"}""",
            """{"content":[],"stop_reason":"pause_turn"}""",
            """{"content":[],"stop_reason":"что-то-новое"}""",
        ).forEach { body -> assertEquals(LlmReply.Failure(AnthropicLlmClient.MALFORMED), reply(body), body) }
    }

    // --- J. Причины остановки ------------------------------------------------------

    @Test
    fun `J — max_tokens с оборванным вызовом — не ToolCall, а ошибка`() = runTest {
        val cut = reply("""{"content":[{"type":"text","text":"Ищу"},{"type":"tool_use","id":"toolu_1","name":"search_notes","input":{}}],
            "stop_reason":"max_tokens"}""")
        assertEquals(LlmReply.Failure(AnthropicLlmClient.CUT_OFF), cut)
        // И обрезанный текст не выдаётся за готовый ответ.
        assertEquals(LlmReply.Failure(AnthropicLlmClient.CUT_OFF), reply("""{"content":[{"type":"text","text":"Во-первых, "}],"stop_reason":"max_tokens"}"""))
    }

    @Test
    fun `отказ — текст отказа или ошибка, но не пустой ответ и не вызов`() = runTest {
        assertEquals(
            LlmReply.Turn(listOf(LlmPart.Text("С этим помочь не могу."))),
            reply("""{"content":[{"type":"text","text":"С этим помочь не могу."}],"stop_reason":"refusal"}"""),
        )
        assertEquals(LlmReply.Failure(AnthropicLlmClient.REFUSED), reply("""{"content":[],"stop_reason":"refusal"}"""))
        assertEquals(
            LlmReply.Failure(AnthropicLlmClient.REFUSED),
            reply("""{"content":[{"type":"text","text":"Сейчас"},{"type":"tool_use","id":"t","name":"n","input":{}}],"stop_reason":"refusal"}"""),
        )
    }

    @Test
    fun `окно модели переполнено`() = runTest {
        assertEquals(LlmReply.Failure(AnthropicLlmClient.TOO_LONG), reply("""{"content":[],"stop_reason":"model_context_window_exceeded"}"""))
    }

    // --- I. Отмена ------------------------------------------------------------------

    @Test
    fun `I — отмена проходит насквозь и отменяет запрос`() = runTest {
        val started = CompletableDeferred<Unit>()
        var aborted = false
        val client = AnthropicLlmClient(config) { _, _, _ ->
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                aborted = true
            }
        }
        val call = async { client.next(request(user("?"))) }
        started.await()
        call.cancel()
        assertFailsWith<CancellationException> { call.await() }
        assertTrue(aborted, "запрос отменён, а не оставлен висеть")
    }
}
