package app.askya.agent

import app.askya.data.sync.Uid
import java.time.Instant

/**
 * Номер предложения — случайный, тем же [Uid], каким помечаются строки базы.
 *
 * Не счётчик: предложения живут в нескольких ходах разговора, и счётчик,
 * начатый заново, выдал бы второе «предложение 1» — подтверждение ушло бы не
 * тому.
 */
@JvmInline
value class ProposalId(val value: String) {
    override fun toString(): String = value

    companion object {
        fun new(): ProposalId = ProposalId(Uid.new())
    }
}

/**
 * Данные предложения — то, что понадобится, чтобы его применить.
 *
 * Своё у каждого инструмента (дело, напоминание, заметка) и заводится вместе с
 * ним. Сущностью Room оно быть не должно: предложение — это ещё не строка
 * базы, и `id = 0` у несуществующей строки легко принять за настоящий номер.
 */
interface ProposalPayload

/**
 * Предложение агента: проверенное «что будет сделано», которое ждёт человека.
 *
 * Собирает его только [ToolTurn] — конструктор закрыт, — и всегда в
 * [ProposalStatus.PENDING]. Состояние меняется только через [moveTo], и только
 * по правилам [ProposalStatus]: `copy` закрыт вместе с конструктором
 * ([ConsistentCopyVisibility]), и выдать предложение за подтверждённое в обход
 * правил снаружи модуля нельзя.
 *
 * Применять предложение здесь нечем — и это нарочно. Тот, кто применяет, будет
 * отдельным механизмом, и ни модель, ни инструменты доступа к нему не получат.
 */
@ConsistentCopyVisibility
data class Proposal internal constructor(
    val id: ProposalId,
    /** Имя инструмента, который его собрал. */
    val tool: String,
    val summary: String,
    val payload: ProposalPayload,
    val status: ProposalStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Почему не удалось — только у [ProposalStatus.FAILED]. */
    val failure: String? = null,
) {

    /**
     * Перейти в [target]. Переход не по правилам — [IllegalStateException]:
     * это ошибка того, кто ведёт предложение, и молча её проглатывать нельзя.
     *
     * [failure] обязателен для [ProposalStatus.FAILED] и запрещён для
     * остальных: причина провала без провала только сбила бы с толку.
     */
    fun moveTo(target: ProposalStatus, at: Instant, failure: String? = null): Proposal {
        check(status.canMoveTo(target)) { "предложение $id: из $status в $target перейти нельзя" }
        if (target == ProposalStatus.FAILED) {
            require(!failure.isNullOrBlank()) { "у провала должна быть причина" }
        } else {
            require(failure == null) { "причина бывает только у провала" }
        }
        return copy(status = target, updatedAt = at, failure = failure)
    }
}
