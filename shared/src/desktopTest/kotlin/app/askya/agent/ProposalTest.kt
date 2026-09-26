package app.askya.agent

import app.askya.data.preferences.AppSettings
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Состояния предложения, пределы политики и контекст. Переходы проверяются
 * все: следующий этап будет водить предложения по этим правилам, и дыра в них
 * значила бы запись без подтверждения.
 */
class ProposalTest {

    private val start = Instant.parse("2026-09-26T07:00:00Z")
    private val later = start.plusSeconds(30)

    private data class Note(val body: String) : ProposalPayload

    private val context = AgentContext(
        currentDate = LocalDate.of(2026, 9, 26),
        currentTime = LocalTime.of(10, 0),
        timezone = ZoneId.of("Europe/Moscow"),
        weekStartsMonday = true,
        autoFillDay = true,
    )

    /** Предложение собирается как в жизни — ходом, а не конструктором. */
    private fun pending(): Proposal = kotlinx.coroutines.runBlocking {
        val tool = object : ProposeTool {
            override val name = "create_note"
            override val description = "Записать заметку"
            override val inputSchema = mapOf<String, Any?>("type" to "object")
            override suspend fun propose(input: Map<String, Any?>, context: AgentContext) =
                ProposalCheck.Valid("Заметка", Note("хлеб"))
        }
        val outcome = ToolRegistry(listOf(tool))
            .openTurn(AgentPolicy(), context, Clock.fixed(start, ZoneId.of("UTC")))
            .call("create_note", emptyMap())
        assertIs<ToolCallOutcome.Proposed>(outcome).proposal
    }

    @Test
    fun `ждущее подтверждается и применяется`() {
        val applied = pending()
            .moveTo(ProposalStatus.CONFIRMED, later)
            .moveTo(ProposalStatus.APPLIED, later)

        assertEquals(ProposalStatus.APPLIED, applied.status)
        assertEquals(later, applied.updatedAt)
        assertEquals(start, applied.createdAt)
    }

    @Test
    fun `подтверждённое может не примениться — с причиной`() {
        val failed = pending()
            .moveTo(ProposalStatus.CONFIRMED, later)
            .moveTo(ProposalStatus.FAILED, later, failure = "база занята")

        assertEquals(ProposalStatus.FAILED, failed.status)
        assertEquals("база занята", failed.failure)
    }

    @Test
    fun `провал без причины и причина без провала запрещены`() {
        val confirmed = pending().moveTo(ProposalStatus.CONFIRMED, later)

        assertFailsWith<IllegalArgumentException> { confirmed.moveTo(ProposalStatus.FAILED, later) }
        assertFailsWith<IllegalArgumentException> {
            confirmed.moveTo(ProposalStatus.APPLIED, later, failure = "лишнее")
        }
    }

    @Test
    fun `ждущее отклоняется и истекает`() {
        assertEquals(ProposalStatus.REJECTED, pending().moveTo(ProposalStatus.REJECTED, later).status)
        assertEquals(ProposalStatus.EXPIRED, pending().moveTo(ProposalStatus.EXPIRED, later).status)
    }

    @Test
    fun `мимо подтверждения не применить`() {
        assertFailsWith<IllegalStateException> { pending().moveTo(ProposalStatus.APPLIED, later) }
        assertFailsWith<IllegalStateException> {
            pending().moveTo(ProposalStatus.FAILED, later, failure = "не было")
        }
    }

    @Test
    fun `из конечных состояний выхода нет`() {
        val terminal = ProposalStatus.entries.filter { it.terminal }
        assertEquals(
            setOf(ProposalStatus.REJECTED, ProposalStatus.APPLIED, ProposalStatus.FAILED, ProposalStatus.EXPIRED),
            terminal.toSet(),
        )
        terminal.forEach { from ->
            ProposalStatus.entries.forEach { to -> assertFalse(from.canMoveTo(to), "$from → $to") }
        }

        val rejected = pending().moveTo(ProposalStatus.REJECTED, later)
        assertFailsWith<IllegalStateException> { rejected.moveTo(ProposalStatus.CONFIRMED, later) }
    }

    @Test
    fun `подтверждённое не отклоняется и не истекает`() {
        val confirmed = pending().moveTo(ProposalStatus.CONFIRMED, later)
        assertFailsWith<IllegalStateException> { confirmed.moveTo(ProposalStatus.REJECTED, later) }
        assertFailsWith<IllegalStateException> { confirmed.moveTo(ProposalStatus.EXPIRED, later) }
    }

    @Test
    fun `номера предложений не повторяются`() {
        val ids = List(1_000) { ProposalId.new() }
        assertEquals(1_000, ids.toSet().size)
    }

    // --- Политика и контекст -------------------------------------------------

    @Test
    fun `по умолчанию облако закрыто, устройство открыто`() {
        val policy = AgentPolicy()
        assertFalse(policy.allowsClient(online = true))
        assertTrue(policy.allowsClient(online = false))
        assertTrue(AgentPolicy(allowCloud = true).allowsClient(online = true))
    }

    @Test
    fun `бессмысленные пределы не принимаются`() {
        assertFailsWith<IllegalArgumentException> { AgentPolicy(maxToolCalls = 0) }
        assertFailsWith<IllegalArgumentException> { AgentPolicy(maxProposals = -1) }
        assertFailsWith<IllegalArgumentException> { AgentPolicy(maxResultSize = 0) }
    }

    @Test
    fun `контекст берётся из настроек и часов, время до минуты`() = runTest {
        val moscow = ZoneId.of("Europe/Moscow")
        val clock = Clock.fixed(Instant.parse("2026-09-26T20:59:42Z"), moscow)
        val ctx = AgentContext.of(AppSettings(weekStartsMonday = false, autoFillDay = false), clock)

        assertEquals(LocalDate.of(2026, 9, 26), ctx.currentDate)
        assertEquals(LocalTime.of(23, 59), ctx.currentTime)
        assertEquals(moscow, ctx.timezone)
        assertFalse(ctx.weekStartsMonday)
        assertFalse(ctx.autoFillDay)
    }
}
