package app.askya.agent.apply

import app.askya.agent.Proposal
import app.askya.agent.ProposalId
import app.askya.agent.ProposalPayload
import app.askya.agent.ProposalStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import kotlin.reflect.KClass

/**
 * Применение предложений, которые подтвердил человек. Приложенческий механизм,
 * а не инструмент: ни модель, ни [app.askya.agent.ToolRegistry], ни сами
 * инструменты ссылки на него не получают. Держит его тот, кто показывает
 * человеку карточку с кнопкой «Сделать», и зовёт только по её нажатию.
 *
 * Правила:
 * - применяется только [ProposalStatus.CONFIRMED]; ждущее, отклонённое и
 *   истёкшее не трогаются;
 * - удалось — [ProposalStatus.APPLIED], не удалось — [ProposalStatus.FAILED]
 *   с причиной, в которой нет чужих данных;
 * - одно предложение применяется не больше одного раза. Исполнитель помнит, что
 *   уже применял, поэтому второе нажатие — или старая копия предложения, всё
 *   ещё CONFIRMED, — второй записи не создаёт. Помнит он в памяти, пока жив
 *   сам: предложения на этом этапе тоже живут только в памяти сеанса.
 *
 * Отмена проходит насквозь. Но если её застали посреди применения, предложение
 * помечается проваленным: что успело записаться, неизвестно, и повтор мог бы
 * записать второй раз.
 */
class ProposalExecutor(
    handlers: List<ProposalHandler<*>>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val byType: Map<KClass<*>, ProposalHandler<*>>

    init {
        val seen = LinkedHashMap<KClass<*>, ProposalHandler<*>>()
        handlers.forEach { handler ->
            require(seen.put(handler.payloadType, handler) == null) {
                "для ${handler.payloadType.simpleName} заведено два обработчика"
            }
        }
        byType = seen
    }

    private val lock = Mutex()
    private val running = HashSet<ProposalId>()
    private val settled = HashMap<ProposalId, Proposal>()

    suspend fun apply(proposal: Proposal): ApplyOutcome {
        lock.withLock {
            settled[proposal.id]?.let { known -> return ApplyOutcome.Skipped(known, skipOf(known.status)) }
            if (proposal.id in running) return ApplyOutcome.Skipped(proposal, SkipReason.IN_PROGRESS)
            if (proposal.status != ProposalStatus.CONFIRMED) {
                return ApplyOutcome.Skipped(proposal, skipOf(proposal.status))
            }
            running += proposal.id
        }

        val result = try {
            run(proposal.payload)
        } catch (cancel: CancellationException) {
            settle(proposal.fail(INTERRUPTED))
            throw cancel
        } catch (_: Exception) {
            // Текст исключения наружу не идёт: в нём бывает что угодно из базы.
            return failed(proposal, COULD_NOT_SAVE)
        }

        return when (result) {
            is HandlerResult.Invalid -> failed(proposal, result.reason)
            is HandlerResult.Done -> {
                val applied = proposal.moveTo(ProposalStatus.APPLIED, clock.instant())
                settle(applied)
                ApplyOutcome.Applied(applied, result.result)
            }
        }
    }

    private suspend fun run(payload: ProposalPayload): HandlerResult {
        val handler = byType[payload::class] ?: return HandlerResult.Invalid(NO_HANDLER)
        @Suppress("UNCHECKED_CAST")
        return (handler as ProposalHandler<ProposalPayload>).apply(payload)
    }

    private suspend fun failed(proposal: Proposal, reason: String): ApplyOutcome {
        val failed = proposal.fail(reason)
        settle(failed)
        return ApplyOutcome.Failed(failed, reason)
    }

    private fun Proposal.fail(reason: String): Proposal =
        moveTo(ProposalStatus.FAILED, clock.instant(), failure = reason)

    /** Запомнить итог. Под отменой тоже: иначе прерванное можно было бы повторить. */
    private suspend fun settle(done: Proposal) = withContext(NonCancellable) {
        lock.withLock {
            running -= done.id
            settled[done.id] = done
        }
    }

    private fun skipOf(status: ProposalStatus): SkipReason = when (status) {
        ProposalStatus.PENDING -> SkipReason.NOT_CONFIRMED
        ProposalStatus.REJECTED -> SkipReason.REJECTED
        ProposalStatus.EXPIRED -> SkipReason.EXPIRED
        ProposalStatus.APPLIED -> SkipReason.ALREADY_APPLIED
        ProposalStatus.FAILED -> SkipReason.ALREADY_FAILED
        // Подтверждённое, которое уже применяется: сюда попадает только оно.
        ProposalStatus.CONFIRMED -> SkipReason.IN_PROGRESS
    }

    private companion object {
        const val COULD_NOT_SAVE = "не удалось сохранить"
        const val NO_HANDLER = "такое предложение применить нечем"
        const val INTERRUPTED = "применение прервано — проверьте, появилась ли запись"
    }
}

/** Чем кончилось нажатие «Сделать». */
sealed interface ApplyOutcome {
    /** Предложение в том состоянии, в каком оно теперь. */
    val proposal: Proposal

    /**
     * Сделано — [result] говорит, что именно. «Сделано» не значит «всё как
     * хотели»: у дела с напоминанием звонка может не быть
     * ([app.askya.reminders.SavedReminder.NoAlarm]), и это видно из [result].
     */
    data class Applied(
        override val proposal: Proposal,
        val result: app.askya.agent.apply.Applied,
    ) : ApplyOutcome

    /** Не удалось — предложение в [ProposalStatus.FAILED], причина безопасна для показа. */
    data class Failed(override val proposal: Proposal, val reason: String) : ApplyOutcome

    /** Применять было нельзя, и ничего не произошло. */
    data class Skipped(override val proposal: Proposal, val reason: SkipReason) : ApplyOutcome
}

enum class SkipReason {
    NOT_CONFIRMED,
    REJECTED,
    EXPIRED,
    ALREADY_APPLIED,
    ALREADY_FAILED,
    IN_PROGRESS,
}
