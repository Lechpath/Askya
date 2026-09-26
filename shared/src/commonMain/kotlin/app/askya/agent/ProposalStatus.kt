package app.askya.agent

/**
 * Состояние предложения и то, куда из него можно перейти.
 *
 * ```
 * PENDING ──► CONFIRMED ──► APPLIED
 *    │            └───────► FAILED
 *    ├──► REJECTED
 *    └──► EXPIRED
 * ```
 *
 * Ждущее ([PENDING]) человек подтверждает или отклоняет; не решил, пока
 * предложение было в силе, — оно истекает. Подтверждённое ([CONFIRMED]) —
 * ещё не сделанное: применение может и не удаться. Остальные состояния
 * конечные, и выйти из них нельзя: сделанное не становится несделанным от
 * смены надписи, а отклонённое не оживает само.
 *
 * Переходы описаны здесь, а не в том, кто их делает, — тогда правило одно на
 * всех, и следующий этап не сможет по ошибке провести предложение мимо
 * подтверждения.
 */
enum class ProposalStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    APPLIED,
    FAILED,
    EXPIRED;

    /** Куда можно перейти отсюда. */
    val next: Set<ProposalStatus>
        get() = when (this) {
            PENDING -> setOf(CONFIRMED, REJECTED, EXPIRED)
            CONFIRMED -> setOf(APPLIED, FAILED)
            REJECTED, APPLIED, FAILED, EXPIRED -> emptySet()
        }

    val terminal: Boolean get() = next.isEmpty()

    fun canMoveTo(target: ProposalStatus): Boolean = target in next
}
