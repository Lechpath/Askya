package app.askya.agent.llm.anthropic

import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.AgentSession
import app.askya.agent.ToolRegistry
import app.askya.agent.TurnOutcome
import app.askya.agent.llm.LlmPart
import app.askya.agent.tools.SearchNotesTool
import app.askya.data.entity.Note
import app.askya.data.repository.NoteRepository
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.TestDatabase
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Клиент Anthropic внутри настоящей сессии: человек → сессия → клиент →
 * `tool_use` → ход → `search_notes` → `tool_result` → клиент → ответ.
 * Сеть подменена, база настоящая.
 */
class AnthropicSessionTest {

    private val base = TestDatabase()
    private val db = base.db
    private val tools = ToolRegistry(listOf(SearchNotesTool(NoteRepository(db.noteDao(), db.topicDao(), db.albumDao(), NoImages, NoVoices))))
    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)
    private val key = "sk-ant-test-не-настоящий"
    private val mountains = "Летом пойти в горы: Эльбрус, палатка."

    @AfterTest
    fun close() = base.close()

    private val firstContent = """[{"type":"thinking","thinking":"","signature":"sig-первый-ход"},
        {"type":"tool_use","id":"toolu_42","name":"search_notes","input":{"query":"горы"}}]"""

    private fun transport() = FakeTransport(
        ok("""{"content":$firstContent,"stop_reason":"tool_use"}"""),
        ok("""{"content":[{"type":"text","text":"Нашлась заметка «Походы»."}],"stop_reason":"end_turn"}"""),
    )

    private suspend fun seed() {
        val at = LocalDateTime.of(2030, 5, 1, 10, 0)
        db.noteDao().insert(Note(title = "Походы", body = mountains, createdAt = at, updatedAt = at))
    }

    @Test
    fun `полный ход через Anthropic с поиском заметок`() = runTest {
        seed()
        val transport = transport()
        val session = AgentSession(
            AnthropicLlmClient(AnthropicConfig(apiKey = key), transport),
            tools,
            { AgentPolicy(allowCloud = true) },
            { context },
        )

        val outcome = assertIs<TurnOutcome.Answered>(session.send("Найди мои заметки про горы"))
        assertEquals("Нашлась заметка «Походы».", outcome.text)
        assertEquals(2, transport.sent.size)

        val second = transport.sent[1]
        assertEquals(AgentSession.SYSTEM, second.json["system"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("search_notes"), (second.json["tools"] as List<Map<String, Any?>>).map { it["name"] })

        val messages = second.messages
        assertEquals(listOf("user", "assistant", "user"), messages.map { it["role"] })
        // Ответ модели — вместе с подписанным рассуждением — вернулся как был.
        assertEquals(jsonMap("""{"content":$firstContent}""").content(), messages[1].content())
        // Ответ инструмента — на тот же вызов и с найденным текстом.
        val result = messages[2].content().single()
        assertEquals("tool_result", result["type"])
        assertEquals("toolu_42", result["tool_use_id"])
        assertTrue(mountains in result["content"] as String)
        assertFalse(result.containsKey("is_error"))

        // Ключ — только в заголовке: ни в телах, ни в истории.
        assertTrue(transport.sent.all { key !in it.body && it.headers["x-api-key"] == key })
        assertFalse(key in session.history.toString())
        assertTrue(session.history.flatMap { it.parts }.any { it is LlmPart.Opaque })
    }

    @Test
    fun `облако закрыто — клиент Anthropic не получает ни одного запроса`() = runTest {
        seed()
        val transport = transport()
        val session = AgentSession(
            AnthropicLlmClient(AnthropicConfig(apiKey = key), transport),
            tools,
            { AgentPolicy(allowCloud = false) },
            { context },
        )
        assertEquals(TurnOutcome.Blocked, session.send("Найди мои заметки про горы"))
        assertEquals(0, transport.sent.size)
    }
}
