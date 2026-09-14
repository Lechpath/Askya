package app.askya.desktop

import app.askya.data.entity.Reminder
import app.askya.data.repository.ReminderRepository
import app.askya.reminders.ReminderClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Будильник напоминаний у Windows-версии — часы внутри запущенной Askya.
 *
 * У телефона будильник заводит система, и он звонит, даже когда Askya
 * закрыта. У Windows такого будильника для обычной программы нет: завести
 * задачу в планировщике Windows значило бы оставлять в системе следы, которые
 * живут без Askya. Поэтому напоминание звонит, пока Askya запущена — открыта
 * или свёрнута в трей: закрытие окна её не завершает, а прячет к часам.
 *
 * Время сверяется по настенным часам, а не по счётчику ожидания: уснувший
 * ноутбук останавливает счётчик, и напоминание, отсчитанное «через час»,
 * после сна прозвенело бы с опозданием на всё время сна.
 */
class DesktopAlarms(private val reminders: ReminderRepository) : ReminderClock {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val waiting = ConcurrentHashMap<Long, Job>()

    /** Показать уведомление. Ставит его окно — у него есть трей. */
    @Volatile
    var notify: (title: String, text: String) -> Unit = { _, _ -> }

    override fun schedule(reminder: Reminder) {
        cancel(reminder.id)
        if (!reminder.enabled) return

        val at = reminder.date.atTime(reminder.time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        // Прошедшее не ставится — как у телефона: звонить о том, что уже
        // прошло, незачем.
        if (at <= System.currentTimeMillis()) return

        waiting[reminder.id] = scope.launch {
            while (true) {
                val left = at - System.currentTimeMillis()
                if (left <= 0) break
                delay(minOf(left, CHECK_MS))
            }
            waiting.remove(reminder.id)
            // Перечитывается из базы: за час его могли выключить или убрать.
            val fresh = reminders.get(reminder.id) ?: return@launch
            if (!fresh.enabled) return@launch
            notify(fresh.title, "Напоминание Askya")
        }
    }

    override fun cancel(id: Long) {
        waiting.remove(id)?.cancel()
    }

    /** Завести всё включённое — на запуске: часы в памяти запуск не переживают. */
    suspend fun rescheduleAll() {
        reminders.enabled().forEach(::schedule)
    }

    private companion object {
        /** Раз в столько сверяться с часами — и после сна тоже. */
        const val CHECK_MS = 30_000L
    }
}
