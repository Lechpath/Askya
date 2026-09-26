package app.askya.agent.apply

import app.askya.agent.Proposal
import app.askya.agent.ProposalId
import app.askya.agent.ProposalPayload
import app.askya.agent.ProposalStatus
import app.askya.agent.expirePending
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Исполнитель предложений: что он применяет, что нет и сколько раз. Обработчик
 * здесь игрушечный и считает вызовы — по счёту и видно, записал бы он дубль.
 */
class ProposalExecutorTest {

    private val start = Instant.parse("2030-05-10T05:00:00Z")
    private val clock = Clock.fixed(start.plusSeconds(60), ZoneOffset.UTC)

    private companion object {
        val NOTE = Applied.Note(noteId = 1L, title = "хлеб")
    }

    private data class Line(val text: String) : ProposalPayload
    private data class Other(val text: String) : ProposalPayload

    private class Counting(
        private val answer: suspend (Line) -> HandlerResult = { HandlerResult.Done(NOTE) },
    ) : ProposalHandler<Line> {
        var calls = 0
        override val payloadType = Line::class
        override suspend fun apply(payload: Line): HandlerResult {
            calls++
            return answer(payload)
        }
    }

    private fun proposal(payload: ProposalPayload = Line("хлеб")) = Proposal(
        id = ProposalId.new(),
        tool = "create_note",
        summary = "Заметка",
        payload = payload,
        status = ProposalStatus.PENDING,
        createdAt = start,
        updatedAt = start,
    )

    private fun confirmed(payload: ProposalPayload = Line("хлеб")) =
        proposal(payload).moveTo(ProposalStatus.CONFIRMED, start)

    @Test
    fun `подтверждённое применяется`() = runTest {
        val handler = Counting()
        val outcome = ProposalExecutor(listOf(handler), clock).apply(confirmed())

        val applied = assertIs<ApplyOutcome.Applied>(outcome)
        assertEquals(ProposalStatus.APPLIED, applied.proposal.status)
        assertEquals(NOTE, applied.result)
        assertEquals(clock.instant(), applied.proposal.updatedAt)
        assertEquals(1, handler.calls)
    }

    @Test
    fun `сбой обработчика — FAILED без подробностей`() = runTest {
        val handler = Counting(answer = { error("UNIQUE constraint failed: notes.body = «мой пароль 1234»") })
        val outcome = ProposalExecutor(listOf(handler), clock).apply(confirmed())

        val failed = assertIs<ApplyOutcome.Failed>(outcome)
        assertEquals(ProposalStatus.FAILED, failed.proposal.status)
        assertTrue("1234" !in failed.reason && "notes" !in failed.reason)
        assertEquals(failed.reason, failed.proposal.failure)
    }

    @Test
    fun `повторная проверка не прошла — FAILED с её причиной`() = runTest {
        val handler = Counting(answer = { HandlerResult.Invalid("время дела уже прошло") })
        val failed = assertIs<ApplyOutcome.Failed>(ProposalExecutor(listOf(handler), clock).apply(confirmed()))

        assertEquals("время дела уже прошло", failed.reason)
    }

    @Test
    fun `ждущее не применяется`() = runTest {
        val handler = Counting()
        val outcome = ProposalExecutor(listOf(handler), clock).apply(proposal())

        assertEquals(SkipReason.NOT_CONFIRMED, assertIs<ApplyOutcome.Skipped>(outcome).reason)
        assertEquals(ProposalStatus.PENDING, outcome.proposal.status)
        assertEquals(0, handler.calls)
    }

    @Test
    fun `отклонённое не применяется`() = runTest {
        val handler = Counting()
        val outcome = ProposalExecutor(listOf(handler), clock)
            .apply(proposal().moveTo(ProposalStatus.REJECTED, start))

        assertEquals(SkipReason.REJECTED, assertIs<ApplyOutcome.Skipped>(outcome).reason)
        assertEquals(0, handler.calls)
    }

    @Test
    fun `истёкшее не применяется`() = runTest {
        val handler = Counting()
        val expired = listOf(proposal()).expirePending(start).single()
        val outcome = ProposalExecutor(listOf(handler), clock).apply(expired)

        assertEquals(ProposalStatus.EXPIRED, expired.status)
        assertEquals(SkipReason.EXPIRED, assertIs<ApplyOutcome.Skipped>(outcome).reason)
        assertEquals(0, handler.calls)
    }

    @Test
    fun `применённое не применяется второй раз`() = runTest {
        val handler = Counting()
        val executor = ProposalExecutor(listOf(handler), clock)
        val applied = executor.apply(confirmed()).proposal

        val again = executor.apply(applied)
        assertEquals(SkipReason.ALREADY_APPLIED, assertIs<ApplyOutcome.Skipped>(again).reason)
        assertEquals(1, handler.calls)

        // И чужим исполнителем тоже: APPLIED само по себе не применяется.
        val other = Counting()
        assertIs<ApplyOutcome.Skipped>(ProposalExecutor(listOf(other), clock).apply(applied))
        assertEquals(0, other.calls)
    }

    @Test
    fun `проваленное не применяется второй раз`() = runTest {
        var fail = true
        val handler = Counting(answer = { if (fail) error("сбой") else HandlerResult.Done(NOTE) })
        val executor = ProposalExecutor(listOf(handler), clock)
        val stale = confirmed()

        assertIs<ApplyOutcome.Failed>(executor.apply(stale))
        fail = false
        val again = executor.apply(stale)

        assertEquals(SkipReason.ALREADY_FAILED, assertIs<ApplyOutcome.Skipped>(again).reason)
        assertEquals(ProposalStatus.FAILED, again.proposal.status)
        assertEquals(1, handler.calls)
    }

    @Test
    fun `второе нажатие старой копией дубля не создаёт`() = runTest {
        val handler = Counting()
        val executor = ProposalExecutor(listOf(handler), clock)
        val stale = confirmed()

        assertIs<ApplyOutcome.Applied>(executor.apply(stale))
        val again = executor.apply(stale)

        assertEquals(SkipReason.ALREADY_APPLIED, assertIs<ApplyOutcome.Skipped>(again).reason)
        assertEquals(ProposalStatus.APPLIED, again.proposal.status)
        assertEquals(1, handler.calls)
    }

    @Test
    fun `два нажатия разом — одна запись`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val handler = Counting(answer = {
            gate.await()
            HandlerResult.Done(NOTE)
        })
        val executor = ProposalExecutor(listOf(handler), clock)
        val stale = confirmed()

        val first = async { executor.apply(stale) }
        yield()
        val second = executor.apply(stale)
        gate.complete(Unit)

        assertEquals(SkipReason.IN_PROGRESS, assertIs<ApplyOutcome.Skipped>(second).reason)
        assertIs<ApplyOutcome.Applied>(first.await())
        assertEquals(1, handler.calls)
    }

    @Test
    fun `отмена проходит насквозь, а прерванное не повторяется`() = runTest {
        val started = CompletableDeferred<Unit>()
        val handler = Counting(answer = {
            started.complete(Unit)
            awaitCancellation()
        })
        val executor = ProposalExecutor(listOf(handler), clock)
        val stale = confirmed()

        val job = launch { executor.apply(stale) }
        started.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)

        val again = executor.apply(stale)
        assertEquals(SkipReason.ALREADY_FAILED, assertIs<ApplyOutcome.Skipped>(again).reason)
        assertEquals(1, handler.calls)
    }

    @Test
    fun `предложение без обработчика проваливается, а не падает`() = runTest {
        val outcome = ProposalExecutor(listOf(Counting()), clock).apply(confirmed(Other("?")))
        assertIs<ApplyOutcome.Failed>(outcome)
    }

    @Test
    fun `два обработчика одного вида не заводятся`() {
        assertFailsWith<IllegalArgumentException> { ProposalExecutor(listOf(Counting(), Counting()), clock) }
    }

    @Test
    fun `конец сеанса гасит только ждущие`() {
        val pending = proposal()
        val confirmed = confirmed()
        val rejected = proposal().moveTo(ProposalStatus.REJECTED, start)

        val after = listOf(pending, confirmed, rejected).expirePending(start)

        assertEquals(
            listOf(ProposalStatus.EXPIRED, ProposalStatus.CONFIRMED, ProposalStatus.REJECTED),
            after.map { it.status },
        )
        assertFailsWith<IllegalStateException> { confirmed.moveTo(ProposalStatus.EXPIRED, start) }
    }
}
