package app.askya.agent

import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Реестр инструментов и ход агента: что попадает в реестр, что пропускает ход
 * и где он останавливает модель. Инструменты здесь игрушечные — настоящих на
 * этом шаге нет.
 */
class ToolRegistryTest {

    private val context = AgentContext(
        currentDate = LocalDate.of(2026, 9, 26),
        currentTime = LocalTime.of(10, 0),
        timezone = ZoneId.of("Europe/Moscow"),
        weekStartsMonday = true,
        autoFillDay = true,
    )

    private val clock = Clock.fixed(Instant.parse("2026-09-26T07:00:00Z"), ZoneOffset.UTC)

    private class Reader(
        override val name: String = "get_today",
        private val answer: suspend () -> ToolResult = { ToolResult.Ok(mapOf("deeds" to emptyList<Any>())) },
    ) : ReadTool {
        var calls = 0
        override val description = "Что сегодня"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult {
            calls++
            return answer()
        }
    }

    private data class Note(val body: String) : ProposalPayload

    private class Proposer(
        override val name: String = "create_note",
        private val answer: (Map<String, Any?>) -> ProposalCheck = { input ->
            ProposalCheck.Valid("Заметка: ${input["body"]}", Note(input["body"].toString()))
        },
    ) : ProposeTool {
        var calls = 0
        override val description = "Записать заметку"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun propose(input: Map<String, Any?>, context: AgentContext): ProposalCheck {
            calls++
            return answer(input)
        }
    }

    /** Прикидывается обоими родами разом — такого в реестре быть не должно. */
    private class Both : ReadTool, ProposeTool {
        override val name = "both"
        override val description = "И то и другое"
        override val inputSchema = mapOf<String, Any?>("type" to "object")
        override suspend fun read(input: Map<String, Any?>, context: AgentContext) = ToolResult.Ok(emptyMap())
        override suspend fun propose(input: Map<String, Any?>, context: AgentContext) =
            ProposalCheck.Valid("тайком", Note("тайком"))
    }

    private fun turn(vararg tools: AgentTool, policy: AgentPolicy = AgentPolicy()) =
        ToolRegistry(tools.toList()).openTurn(policy, context, clock)

    // --- Регистрация ---------------------------------------------------------

    @Test
    fun `читающий инструмент регистрируется как READ`() = runTest {
        val registry = ToolRegistry(listOf(Reader()))

        assertEquals(ToolKind.READ, registry.kindOf("get_today"))
        assertEquals(listOf(ToolSpec("get_today", "Что сегодня", mapOf("type" to "object"), ToolKind.READ)), registry.specs)

        val outcome = registry.openTurn(AgentPolicy(), context, clock).call("get_today", emptyMap())
        assertIs<ToolCallOutcome.Read>(outcome)
        assertEquals(mapOf("deeds" to emptyList<Any>()), outcome.data)
    }

    @Test
    fun `инструмент-предложение регистрируется как PROPOSE`() {
        val registry = ToolRegistry(listOf(Proposer()))

        assertEquals(ToolKind.PROPOSE, registry.kindOf("create_note"))
        assertEquals(ToolKind.PROPOSE, registry.specs.single().kind)
    }

    @Test
    fun `инструмент обоих родов в реестр не попадает`() {
        assertFailsWith<IllegalArgumentException> { ToolRegistry(listOf(Both())) }
    }

    @Test
    fun `предложение нельзя выдать за чтение`() = runTest {
        val proposer = Proposer()
        val outcome = turn(proposer).call("create_note", mapOf("body" to "хлеб"))

        // Предложение не возвращается данными, как чтение, — только предложением,
        // и только ждущим человека.
        val proposed = assertIs<ToolCallOutcome.Proposed>(outcome)
        assertEquals(ProposalStatus.PENDING, proposed.proposal.status)
        assertEquals(ToolKind.PROPOSE, (proposer as AgentTool).kind)
    }

    @Test
    fun `повтор имени отвергается`() {
        assertFailsWith<IllegalArgumentException> { ToolRegistry(listOf(Reader(), Reader())) }
    }

    @Test
    fun `кривое имя отвергается`() {
        assertFailsWith<IllegalArgumentException> { ToolRegistry(listOf(Reader(name = "что сегодня"))) }
        assertFailsWith<IllegalArgumentException> { ToolRegistry(listOf(Reader(name = "x".repeat(65)))) }
    }

    @Test
    fun `неизвестный инструмент отклоняется и считается вызовом`() = runTest {
        val turn = turn(Reader())
        val outcome = turn.call("delete_everything", emptyMap())

        val refused = assertIs<ToolCallOutcome.Refused>(outcome)
        assertEquals(RefusalReason.UNKNOWN_TOOL, refused.reason)
        assertEquals(1, turn.callsMade)
        assertNull(ToolRegistry(listOf(Reader())).kindOf("delete_everything"))
    }

    // --- Пределы хода --------------------------------------------------------

    @Test
    fun `вызовов не больше maxToolCalls`() = runTest {
        val reader = Reader()
        val turn = turn(reader, policy = AgentPolicy(maxToolCalls = 2))

        assertIs<ToolCallOutcome.Read>(turn.call("get_today", emptyMap()))
        assertIs<ToolCallOutcome.Refused>(turn.call("unknown", emptyMap()))
        val third = assertIs<ToolCallOutcome.Refused>(turn.call("get_today", emptyMap()))

        assertEquals(RefusalReason.TOOL_CALL_LIMIT, third.reason)
        assertEquals(1, reader.calls, "третий вызов до инструмента не дошёл")
        assertEquals(2, turn.callsMade)
    }

    @Test
    fun `предложений не больше maxProposals`() = runTest {
        val proposer = Proposer()
        val turn = turn(proposer, policy = AgentPolicy(maxProposals = 2))

        assertIs<ToolCallOutcome.Proposed>(turn.call("create_note", mapOf("body" to "раз")))
        assertIs<ToolCallOutcome.Proposed>(turn.call("create_note", mapOf("body" to "два")))
        val third = assertIs<ToolCallOutcome.Refused>(turn.call("create_note", mapOf("body" to "три")))

        assertEquals(RefusalReason.PROPOSAL_LIMIT, third.reason)
        assertEquals(2, proposer.calls)
        assertEquals(2, turn.proposals.size)
    }

    @Test
    fun `отказ инструмента места под предложение не занимает`() = runTest {
        var valid = false
        val proposer = Proposer(answer = { input ->
            if (valid) ProposalCheck.Valid("ок", Note("ок")) else ProposalCheck.Invalid("нет текста: ${input.size}")
        })
        val turn = turn(proposer, policy = AgentPolicy(maxProposals = 1))

        assertIs<ToolCallOutcome.Failed>(turn.call("create_note", emptyMap()))
        valid = true
        assertIs<ToolCallOutcome.Proposed>(turn.call("create_note", emptyMap()))
    }

    @Test
    fun `выключенные предложения инструмента не зовут`() = runTest {
        val proposer = Proposer()
        val outcome = turn(proposer, policy = AgentPolicy(allowProposals = false))
            .call("create_note", mapOf("body" to "хлеб"))

        assertEquals(RefusalReason.PROPOSALS_DISABLED, assertIs<ToolCallOutcome.Refused>(outcome).reason)
        assertEquals(0, proposer.calls)
    }

    @Test
    fun `слишком длинный ответ не уходит модели`() = runTest {
        val reader = Reader(answer = { ToolResult.Ok(mapOf("text" to "а".repeat(200))) })
        val outcome = turn(reader, policy = AgentPolicy(maxResultSize = 100)).call("get_today", emptyMap())

        assertEquals(RefusalReason.RESULT_TOO_LARGE, assertIs<ToolCallOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `ответ на пределе проходит`() = runTest {
        // {"t":"…"} — восемь знаков обвязки: ровно сто вместе с текстом.
        val reader = Reader(answer = { ToolResult.Ok(mapOf("t" to "а".repeat(92))) })
        val outcome = turn(reader, policy = AgentPolicy(maxResultSize = 100)).call("get_today", emptyMap())

        assertIs<ToolCallOutcome.Read>(outcome)
    }

    @Test
    fun `ответ не из JSON не уходит модели`() = runTest {
        val reader = Reader(answer = { ToolResult.Ok(mapOf("date" to LocalDate.of(2026, 9, 26))) })
        val outcome = turn(reader).call("get_today", emptyMap())

        assertEquals(RefusalReason.INVALID_RESULT, assertIs<ToolCallOutcome.Refused>(outcome).reason)
    }

    @Test
    fun `сломавшийся инструмент не роняет ход и не выдаёт подробностей`() = runTest {
        val reader = Reader(answer = { error("строка 42 из таблицы notes") })
        val outcome = assertIs<ToolCallOutcome.Failed>(turn(reader).call("get_today", emptyMap()))

        assertTrue("notes" !in outcome.reason)
    }

    // --- Предложения ---------------------------------------------------------

    @Test
    fun `у каждого предложения свой номер`() = runTest {
        val turn = turn(Proposer(), policy = AgentPolicy(maxProposals = 3, maxToolCalls = 3))
        repeat(3) { turn.call("create_note", mapOf("body" to "одно и то же")) }

        val ids = turn.proposals.map { it.id }
        assertEquals(3, ids.toSet().size)
        assertTrue(ids.all { it.value.length == 32 })
    }

    @Test
    fun `предложение рождается ждущим и со временем хода`() = runTest {
        val proposal = assertIs<ToolCallOutcome.Proposed>(
            turn(Proposer()).call("create_note", mapOf("body" to "хлеб")),
        ).proposal

        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertEquals("create_note", proposal.tool)
        assertEquals("Заметка: хлеб", proposal.summary)
        assertEquals(Note("хлеб"), proposal.payload)
        assertEquals(clock.instant(), proposal.createdAt)
    }
}
