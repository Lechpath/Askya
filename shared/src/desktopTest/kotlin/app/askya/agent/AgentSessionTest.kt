package app.askya.agent

import app.askya.agent.llm.LlmClient
import app.askya.agent.llm.LlmPart
import app.askya.agent.llm.LlmReply
import app.askya.agent.llm.Role
import app.askya.agent.llm.ScriptedClient
import app.askya.agent.llm.ScriptedClient.Companion.call
import app.askya.agent.llm.ScriptedClient.Companion.calls
import app.askya.agent.llm.ScriptedClient.Companion.text
import app.askya.agent.llm.lastResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Цикл разговора: модель, инструменты через ход, история и отмена. */
class AgentSessionTest {

    private val context = AgentContext(LocalDate.of(2030, 5, 10), LocalTime.of(9, 0), ZoneId.of("UTC"), true, true)

    private class Echo : ReadTool {
        var calls = 0
        override val name = "echo"
        override val description = "Повторяет x"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult {
            calls++
            return ToolResult.Ok(mapOf("echo" to input["x"]))
        }
    }

    private class Broken : ReadTool {
        override val name = "broken"
        override val description = "Ломается"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult =
            throw IllegalStateException("секрет из базы")
    }

    private class Refusing : ReadTool {
        override val name = "refusing"
        override val description = "Отказывает"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext) = ToolResult.Failed("не так")
    }

    private val echo = Echo()
    private val registry = ToolRegistry(listOf(echo, Broken(), Refusing()))

    private fun session(client: LlmClient, policy: AgentPolicy = AgentPolicy()) =
        AgentSession(client, registry, { policy }, { context })

    private fun local(vararg steps: suspend (app.askya.agent.llm.LlmRequest) -> LlmReply) =
        ScriptedClient(online = false, *steps)

    // --- Обычный ход ------------------------------------------------------------

    @Test
    fun `обычный ответ — один запрос, ход в истории`() = runTest {
        val client = local({ text("Привет") })
        val session = session(client)

        assertEquals(TurnOutcome.Answered("Привет", emptyList()), session.send("Здравствуй"))
        assertEquals(1, client.requests.size)
        val request = client.requests.single()
        assertEquals(AgentSession.SYSTEM, request.system)
        assertEquals(listOf(Role.USER), request.messages.map { it.role })
        assertEquals(listOf(Role.USER, Role.ASSISTANT), session.history.map { it.role })
    }

    @Test
    fun `модель получает ровно описания зарегистрированных инструментов`() = runTest {
        val client = local({ text("ок") })
        session(client).send("что умеешь?")
        assertEquals(registry.specs, client.requests.single().tools)
        assertEquals(listOf("echo", "broken", "refusing"), client.requests.single().tools.map { it.name })
    }

    @Test
    fun `вызов инструмента, результат обратно модели, потом ответ`() = runTest {
        val client = local(
            { call("c1", "echo", "x" to "раз") },
            { request ->
                val result = request.lastResults.single()
                assertEquals("c1", result.callId)
                assertEquals("""{"echo":"раз"}""", result.content)
                assertEquals(false, result.isError)
                text("Готово")
            },
        )
        val session = session(client)

        assertEquals(TurnOutcome.Answered("Готово", emptyList()), session.send("повтори"))
        assertEquals(2, client.requests.size)
        assertEquals(1, echo.calls)
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.USER, Role.ASSISTANT), session.history.map { it.role })
    }

    @Test
    fun `несколько вызовов подряд в одном ходе`() = runTest {
        val client = local(
            { call("c1", "echo", "x" to 1L) },
            { call("c2", "echo", "x" to 2L) },
            { request ->
                assertEquals("""{"echo":2}""", request.lastResults.single().content)
                text("Два шага")
            },
        )
        assertEquals(TurnOutcome.Answered("Два шага", emptyList()), session(client).send("два раза"))
        assertEquals(3, client.requests.size)
        assertEquals(2, echo.calls)
    }

    @Test
    fun `два вызова в одной реплике — ответы одним сообщением и по порядку`() = runTest {
        val client = local(
            { calls(LlmPart.ToolCall("a", "echo", mapOf("x" to "А")), LlmPart.ToolCall("b", "echo", mapOf("x" to "Б"))) },
            { request ->
                assertEquals(listOf("a", "b"), request.lastResults.map { it.callId })
                text("ок")
            },
        )
        session(client).send("два сразу")
        assertEquals(2, echo.calls)
    }

    // --- Ошибки инструментов ----------------------------------------------------

    @Test
    fun `неизвестный инструмент — отказ хода модели, цикл идёт дальше`() = runTest {
        val client = local(
            { call("c1", "delete_everything") },
            { request ->
                val result = request.lastResults.single()
                assertTrue(result.isError)
                assertEquals("инструмента «delete_everything» нет", result.content)
                text("Так не умею")
            },
        )
        assertEquals(TurnOutcome.Answered("Так не умею", emptyList()), session(client).send("удали всё"))
    }

    @Test
    fun `упавший инструмент не роняет ход и не выдаёт подробностей`() = runTest {
        val client = local(
            { call("c1", "broken") },
            { request ->
                val result = request.lastResults.single()
                assertTrue(result.isError)
                assertTrue("секрет" !in result.content, result.content)
                text("Не вышло")
            },
        )
        assertIs<TurnOutcome.Answered>(session(client).send("сломайся"))
    }

    @Test
    fun `отказ инструмента — словами модели`() = runTest {
        val client = local(
            { call("c1", "refusing") },
            { request ->
                assertEquals(LlmPart.ToolResult("c1", "не так", isError = true), request.lastResults.single())
                text("Понял")
            },
        )
        assertIs<TurnOutcome.Answered>(session(client).send("?"))
    }

    @Test
    fun `модель, которая не останавливается, упирается в предел хода`() = runTest {
        val policy = AgentPolicy(maxToolCalls = 2)
        val endless = Array<suspend (app.askya.agent.llm.LlmRequest) -> LlmReply>(20) { n -> { call("c$n", "echo") } }
        val client = ScriptedClient(false, *endless)
        val session = session(client, policy)

        assertEquals(TurnOutcome.Failed(AgentSession.NO_END), session.send("зацикли"))
        // Два вызова прошли, третий отказан ходом, после четвёртого ход прерван.
        assertEquals(2, echo.calls)
        assertEquals(4, client.requests.size)
        assertEquals(emptyList(), session.history, "незаконченный ход в историю не попал")
    }

    // --- Клиент ---------------------------------------------------------------

    @Test
    fun `ошибка клиента — состояние хода, а не исключение`() = runTest {
        val failing = local({ LlmReply.Failure("нет сети") })
        assertEquals(TurnOutcome.Failed("нет сети"), session(failing).send("?"))

        val throwing = local({ throw IllegalStateException("запрос целиком: ...") })
        val session = session(throwing)
        assertEquals(TurnOutcome.Failed(AgentSession.CLIENT_FAILED), session.send("?"))
        assertEquals(emptyList(), session.history)
    }

    @Test
    fun `история переходит в следующий ход, неудачный ход — нет`() = runTest {
        val client = local(
            { text("Первый") },
            { LlmReply.Failure("сбой") },
            { request ->
                assertEquals(listOf("один", "Первый", "три"), request.messages.map { (it.parts.single() as LlmPart.Text).text })
                text("Третий")
            },
        )
        val session = session(client)
        session.send("один")
        session.send("два")
        assertEquals(TurnOutcome.Answered("Третий", emptyList()), session.send("три"))
        assertEquals(4, session.history.size)
    }

    // --- Служебные блоки провайдера ---------------------------------------------

    @Test
    fun `служебный блок доходит до следующих запросов как есть`() = runTest {
        val raw = mapOf(
            "text" to "ход мысли",
            "count" to 42L,
            "ratio" to 0.5,
            "flag" to true,
            "nothing" to null,
            "nested" to mapOf("inner" to "значение", "deeper" to mapOf("n" to 1L)),
            "list" to listOf("а", 2L, false, null, mapOf("k" to "v")),
        )
        val block = LlmPart.Opaque(raw)
        val client = local(
            // Блок рядом с вызовом инструмента — его надо вернуть в том же ходе.
            { LlmReply.Turn(listOf(block, LlmPart.ToolCall("c1", "echo", mapOf("x" to 1L)))) },
            { request ->
                val assistant = request.messages[1]
                assertEquals(Role.ASSISTANT, assistant.role)
                assertTrue(assistant.parts.first() === block, "тот же объект, без пересборки")
                text("Первый")
            },
            { request ->
                // И в следующем ходе — из истории.
                val kept = request.messages.flatMap { it.parts }.filterIsInstance<LlmPart.Opaque>().single()
                assertTrue(kept === block)
                assertEquals(raw, kept.raw, "все поля и значения на месте")
                text("Второй")
            },
        )
        val session = session(client)

        assertEquals(TurnOutcome.Answered("Первый", emptyList()), session.send("раз"), "блок в текст ответа не попадает")
        assertTrue(session.history.flatMap { it.parts }.contains(block))
        assertEquals(TurnOutcome.Answered("Второй", emptyList()), session.send("два"))
        assertEquals(3, client.requests.size)
    }

    // --- Отмена ---------------------------------------------------------------

    @Test
    fun `отмена проходит насквозь, ход не остаётся в истории, сессия жива`() = runTest {
        val started = CompletableDeferred<Unit>()
        val client = local(
            { text("Первый") },
            { call("c1", "echo", "x" to "до отмены") },
            { started.complete(Unit); awaitCancellation() },
            { text("После") },
        )
        val session = session(client)
        session.send("один")

        val turn = async { session.send("долгий") }
        started.await()
        turn.cancel()
        assertFailsWith<CancellationException> { turn.await() }

        assertEquals(2, session.history.size, "в истории только первый ход")
        assertEquals(TurnOutcome.Answered("После", emptyList()), session.send("ещё"))
    }

    @Test
    fun `отмена внутри инструмента тоже проходит насквозь`() = runTest {
        val cancelling = object : ReadTool {
            override val name = "cancelling"
            override val description = "Отменяется"
            override val inputSchema = mapOf<String, Any?>("type" to "object")
            override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult =
                throw CancellationException("ушли с экрана")
        }
        val client = local({ call("c1", "cancelling") })
        val session = AgentSession(client, ToolRegistry(listOf(cancelling)), { AgentPolicy() }, { context })
        assertFailsWith<CancellationException> { session.send("?") }
        assertEquals(emptyList(), session.history)
    }

    // --- Клиент не меняется -----------------------------------------------------

    @Test
    fun `сменить клиента у сессии нечем`() {
        val cls = AgentSession::class.java
        // Клиент принимает только конструктор; ни метода, ни поля для другого.
        val takesClient = cls.declaredMethods.filter { method -> method.parameterTypes.any { LlmClient::class.java.isAssignableFrom(it) } }
        assertEquals(emptyList(), takesClient.map { it.name })
        val clientField = cls.getDeclaredField("client")
        assertTrue(java.lang.reflect.Modifier.isFinal(clientField.modifiers), "клиент — val")
        assertTrue(cls.declaredMethods.none { it.name == "setClient" })
    }

    @Test
    fun `новый клиент — новая сессия с пустой историей`() = runTest {
        val a = local({ text("A") })
        val first = session(a)
        first.send("заметки про горы")

        val b = ScriptedClient(online = true, { request ->
            assertEquals(1, request.messages.size, "история A сюда не попала")
            text("B")
        })
        val second = session(b, AgentPolicy(allowCloud = true))
        assertEquals(emptyList(), second.history)
        second.send("привет")
        assertTrue(b.requests.single().messages.none { message ->
            message.parts.any { it is LlmPart.Text && "горы" in it.text }
        })
        assertEquals(a, first.client)
    }
}
