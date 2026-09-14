package app.askya.reminders

import app.askya.data.entity.Reminder
import app.askya.data.entity.remindAt
import app.askya.data.entity.reminderOf
import app.askya.data.repository.ReminderRepository
import java.time.LocalDate
import java.time.LocalTime

/**
 * Будильник напоминаний — то, что о нём знает общий код: поставить и снять.
 *
 * На телефоне это `ReminderAlarms`: точный будильник `AlarmManager` и
 * уведомление в шторке, переживающие и закрытое приложение, и перезагрузку.
 * На компьютере — часы внутри запущенной Askya и уведомление Windows рядом с
 * часами: звонит, пока Askya открыта или свёрнута в трей (см. README,
 * «Windows-версия»).
 */
interface ReminderClock {
    /** Поставить — или снять, если напоминание выключено. Прошедшее не ставится. */
    fun schedule(reminder: Reminder)

    fun cancel(id: Long)

    /**
     * Звонит ли будильник минута в минуту. На телефоне право на точный
     * будильник человек может отнять руками; тогда экран напоминаний
     * предлагает вернуть его ([openExactSettings]).
     */
    val exact: Boolean get() = true

    /** Отвести туда, где право на точный будильник возвращают. */
    fun openExactSettings() {}
}

/**
 * Снять напоминания об убранных делах — вместе с их будильниками.
 *
 * Одно место на всех, кто убирает дела: убирает их и день (карточкой), и
 * список дел (сняли день недели, выключили дело). Напоминание, пережившее своё
 * дело, звонит о том, чего в дне уже нет, — и человек идёт искать несуществующее.
 *
 * Читается из базы, а не из потока экрана: тот жив, только пока на экран
 * смотрят, а дело убирают и в тот же миг уходят назад.
 */
suspend fun dropReminders(
    clock: ReminderClock,
    reminders: ReminderRepository,
    itemIds: List<Long>,
) {
    if (itemIds.isEmpty()) return
    reminders.forItems(itemIds).forEach { old ->
        clock.cancel(old.id)
        reminders.delete(old)
    }
}

/**
 * Переносит напоминание о деле на новый час — тем же способом, каким его задал
 * человек: сказанный прямо час остаётся, «за столько-то до» едет вслед за
 * началом.
 *
 * Одно место на всех, кто двигает дела: их двигает и день (карточку тянут за
 * соседнюю), и список дел — правка часа в правиле доходит до уже собранных
 * дней. «За пятнадцать минут» — это про дело, а не про час, в который оно
 * раньше стояло.
 *
 * Выключенное напоминание переписывается, но будильник ему не заводится:
 * выключили — значит, не звонить.
 */
suspend fun moveReminder(
    clock: ReminderClock,
    reminders: ReminderRepository,
    itemId: Long,
    date: LocalDate,
    start: LocalTime,
    end: LocalTime?,
) {
    val old = reminders.forItems(listOf(itemId)).firstOrNull() ?: return
    clock.cancel(old.id)
    val moved = reminderOf(
        title = old.title,
        eventDate = date,
        eventStart = start,
        eventEnd = end,
        remind = old.remindAt,
        id = old.id,
        icon = old.icon,
        enabled = old.enabled,
        silent = old.silent,
        sound = old.sound,
        soundTitle = old.soundTitle,
        itemId = itemId,
    )
    reminders.save(moved)
    if (moved.enabled) clock.schedule(moved)
}
