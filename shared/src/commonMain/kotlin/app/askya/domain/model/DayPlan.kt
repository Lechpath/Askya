package app.askya.domain.model

import app.askya.data.entity.ScheduleItem
import java.time.LocalDate
import java.time.LocalTime

/**
 * День на экране AskyaDay: дата и её дела.
 *
 * Чек-ина здесь нет намеренно: AskyaDay показывает расписание, а состояние
 * живёт в своём разделе. Модель отдельная от списка дел потому, что дата
 * нужна вместе с ними — экран листает дни.
 */
data class DayPlan(
    val date: LocalDate,
    val schedule: List<ScheduleItem> = emptyList(),
) {
    val doneCount: Int get() = schedule.count { it.done }

    /**
     * Дело, которое идёт прямо сейчас. Конец не обязателен, поэтому у дела без
     * него берётся час — этого хватает, чтобы подсветка не висела до вечера.
     */
    fun currentBlock(now: LocalTime): ScheduleItem? = schedule.lastOrNull { item ->
        if (item.done || now.isBefore(item.startTime)) return@lastOrNull false
        val end = item.endTime ?: item.startTime.plusHours(1)
        // plusHours у LocalTime переходит через полночь: у позднего дела конец
        // оказался бы раньше начала, и подсветка не сработала бы вовсе.
        end <= item.startTime || now.isBefore(end)
    }
}
