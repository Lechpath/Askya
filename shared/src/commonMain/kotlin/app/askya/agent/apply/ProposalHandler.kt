package app.askya.agent.apply

import app.askya.agent.ProposalPayload
import app.askya.reminders.CreatedDeed
import app.askya.reminders.SavedReminder
import kotlin.reflect.KClass

/**
 * Как применить предложение одного вида — дело, напоминание, заметку.
 *
 * Обработчик получает только данные предложения, а не само предложение:
 * состояние ведёт [ProposalExecutor], и обработчик не может ни пропустить
 * подтверждение, ни объявить предложение применённым сам.
 */
interface ProposalHandler<P : ProposalPayload> {

    /** Какие данные он умеет применять. Один вид — один обработчик. */
    val payloadType: KClass<P>

    /**
     * Проверить заново и применить. Ошибка базы — исключение: его ловит
     * исполнитель и превращает в безопасную причину, а подробности модели не
     * уходят.
     */
    suspend fun apply(payload: P): HandlerResult
}

/** Чем кончилось применение у обработчика. */
sealed interface HandlerResult {

    /** Сделано — вот что именно. */
    data class Done(val result: Applied) : HandlerResult

    /** Повторная проверка не прошла — ничего не записано. */
    data class Invalid(val reason: String) : HandlerResult
}

/**
 * Что сделано — типами, а не словарём. Словами для модели и человека это
 * станет выше: у экрана и у модели слова разные, а сделанное одно.
 */
sealed interface Applied {

    /**
     * Дело заведено. Звонок — в [CreatedDeed.reminder]: там же видно, что
     * напоминание есть, а звонка не будет ([SavedReminder.NoAlarm]).
     */
    data class Task(val deed: CreatedDeed) : Applied

    /** Напоминание записано; что с просьбой о звонке — в самом [reminder]. */
    data class Reminder(val reminder: SavedReminder) : Applied

    /** Заметка записана под именем [title] — таким, каким его дало правило быстрой заметки. */
    data class Note(val noteId: Long, val title: String) : Applied
}
