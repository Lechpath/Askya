package app.askya.widget

import app.askya.data.entity.ScheduleItem
import java.time.LocalDateTime
import java.time.LocalTime

/** Что показывает виджет: дела по порядку и текст на случай пустого дня. */
data class DayWidgetState(
    val items: List<ScheduleItem>,
    val empty: String,
    /**
     * Дела уже завтрашние. Строки в этом случае подписываются днём: без
     * подписи в одиннадцать вечера «07:00 Подъём» читалось бы как сейчас.
     */
    val tomorrow: Boolean = false,
)

/** Сколько дел помещается на экран виджета разом; остальные — прокруткой. */
const val WIDGET_VISIBLE_ROWS = 3

/**
 * Отбор дел для виджета: последовательность, начиная с текущего момента.
 *
 * Прошедшее не показывается. Впереди отдаётся всё, что осталось: на экране
 * помещаются три дела (высота строки — треть виджета), остальные достаются
 * прокруткой. Резать список здесь было бы неверно — прокручивать стало бы
 * нечего.
 *
 * Дело считается идущим, пока не кончилось; у дела без конца берётся час —
 * та же мерка, что у планировщика (`DayPlanner.SLOT`).
 *
 * Когда на сегодня всё, виджет переключается на завтра, а не пустеет: вечером
 * человеку полезнее знать, во сколько подъём, чем читать «на сегодня всё».
 */
object DayWidgetData {

    private const val DEFAULT_MINUTES = 60L

    fun select(
        now: LocalDateTime,
        today: List<ScheduleItem>,
        tomorrow: List<ScheduleItem>,
    ): DayWidgetState {
        // По времени начала: порядок дел в виджете — это порядок дня, а не
        // порядок, в котором их завели. От него же зависит, какое дело стоит
        // первым, а первое — то, которое идёт.
        val remaining = today.filter { it.endsAfter(now.toLocalTime()) }.sortedBy { it.startTime }

        return if (remaining.isNotEmpty()) {
            DayWidgetState(items = remaining, empty = "")
        } else if (tomorrow.isNotEmpty()) {
            DayWidgetState(items = tomorrow.sortedBy { it.startTime }, empty = "", tomorrow = true)
        } else {
            DayWidgetState(items = emptyList(), empty = "На сегодня всё.")
        }
    }

    /**
     * Дело идёт прямо сейчас: началось и ещё не кончилось.
     *
     * Спрашивается о самом деле, а не о его месте в списке. Раньше идущим
     * считалось первое дело списка, если оно уже началось, — и стоило двум
     * делам наложиться (длинное с утра и короткое посреди него), как идущее
     * оказывалось вторым и не подсвечивалось вовсе.
     */
    fun isNow(item: ScheduleItem, moment: LocalTime): Boolean =
        !item.startTime.isAfter(moment) && item.endsAfter(moment)

    /** Идёт сейчас или ещё впереди. */
    private fun ScheduleItem.endsAfter(moment: LocalTime): Boolean {
        val end = endTime ?: startTime.plusMinutes(DEFAULT_MINUTES)
        // Дело до полуночи, у которого конец «перевалил» за 00:00, обрезается
        // концом суток: иначе оно считалось бы прошедшим весь вечер.
        val bounded = if (end < startTime) LocalTime.MAX else end
        return bounded > moment
    }
}
