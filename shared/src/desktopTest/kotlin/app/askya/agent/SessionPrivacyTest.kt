package app.askya.agent

import app.askya.agent.llm.LlmRequest
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.ScriptedClient
import app.askya.agent.llm.ScriptedClient.Companion.call
import app.askya.agent.llm.ScriptedClient.Companion.text
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Граница приватности в самом цикле, а не в политике: клиент, которому
 * отправлять нельзя, не получает ни одного запроса — ни первого, ни
 * следующего после вызова инструмента.
 */
class SessionPrivacyTest {

    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    private val secret = object : ReadTool {
        override val name = "secret"
        override val description = "Отдаёт личное"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext) =
            ToolResult.Ok(mapOf("text" to "дневник"))
    }
    private val registry = ToolRegistry(listOf(secret))

    private fun cloud(vararg steps: suspend (LlmRequest) -> LlmReply) = ScriptedClient(online = true, *steps)
    private fun local(vararg steps: suspend (LlmRequest) -> LlmReply) = ScriptedClient(online = false, *steps)

    @Test
    fun `облако закрыто, клиент в сети — ни одного запроса`() = runTest {
        val client = cloud({ text("не должно случиться") })
        val session = AgentSession(client, registry, { AgentPolicy(allowCloud = false) }, { context })

        assertEquals(TurnOutcome.Blocked, session.send("мои заметки"))
        assertEquals(0, client.requests.size)
        assertEquals(emptyList(), session.history)
    }

    @Test
    fun `облако открыто, клиент в сети — вызывается`() = runTest {
        val client = cloud({ text("ок") })
        val session = AgentSession(client, registry, { AgentPolicy(allowCloud = true) }, { context })
        assertIs<TurnOutcome.Answered>(session.send("привет"))
        assertEquals(1, client.requests.size)
    }

    @Test
    fun `клиент на устройстве работает и при закрытом облаке`() = runTest {
        val client = local({ call("c1", "secret") }, { text("прочитал") })
        val session = AgentSession(client, registry, { AgentPolicy(allowCloud = false) }, { context })
        assertIs<TurnOutcome.Answered>(session.send("мои заметки"))
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `клиент на устройстве работает и при открытом облаке`() = runTest {
        val client = local({ call("c1", "secret") }, { text("прочитал") })
        val session = AgentSession(client, registry, { AgentPolicy(allowCloud = true) }, { context })
        assertIs<TurnOutcome.Answered>(session.send("мои заметки"))
        assertEquals(2, client.requests.size)
    }

    @Test
    fun `облако закрыли посреди хода — результат инструмента не уходит`() = runTest {
        var policy = AgentPolicy(allowCloud = true)
        val client = cloud(
            {
                // Пока модель думала, человек выключил облако.
                policy = AgentPolicy(allowCloud = false)
                call("c1", "secret")
            },
            { text("не должно случиться") },
        )
        val session = AgentSession(client, registry, { policy }, { context })

        assertEquals(TurnOutcome.Blocked, session.send("мои заметки"))
        assertEquals(1, client.requests.size, "первый запрос ушёл, второй — с «дневником» — нет")
        assertEquals(false, client.requests.single().toString().contains("дневник"))
        assertEquals(emptyList(), session.history)
    }

    @Test
    fun `проверка на каждом ходе, а не при создании сессии`() = runTest {
        var policy = AgentPolicy(allowCloud = true)
        val client = cloud({ text("первый") }, { text("не должно случиться") })
        val session = AgentSession(client, registry, { policy }, { context })

        assertIs<TurnOutcome.Answered>(session.send("раз"))
        policy = AgentPolicy(allowCloud = false)
        assertEquals(TurnOutcome.Blocked, session.send("два"))
        assertEquals(1, client.requests.size)
        assertEquals(2, session.history.size, "в истории только разрешённый ход")
    }
}
