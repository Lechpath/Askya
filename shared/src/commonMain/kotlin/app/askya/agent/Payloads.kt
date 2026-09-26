package app.askya.agent

import app.askya.data.entity.remindMoment
import app.askya.domain.model.RemindAt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Данные предложений v0.1 — ровно то, что нужно операции, и ничего сверх.
 *
 * Не сущности Room: у предложения ещё нет строки в базе, а `id = 0`,
 * `uid`, `removedAt` и прочие служебные поля в нём значили бы то, чего нет.
 * Правки, удаления, отметки «сделано» и переноса здесь нет — их v0.1 не делает.
 */

/** Новое дело дня. [remind] — напоминание о нём, если человек его просил. */
data class CreateTaskPayload(
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime? = null,
    val note: String = "",
    val remind: RemindAt? = null,
) : ProposalPayload

/** Отдельное напоминание, не привязанное к делу. [time] — час события. */
data class CreateReminderPayload(
    val title: String,
    val date: LocalDate,
    val time: LocalTime,
    val remind: RemindAt = RemindAt.Exact(time),
) : ProposalPayload

/** Быстрая заметка в Библиотеку, без книги. */
data class CreateNotePayload(
    val title: String,
    val body: String,
) : ProposalPayload

/**
 * Проверка данных предложения — одна и та же при сборке и при применении.
 *
 * При применении она повторяется, а не берётся на веру: между «предложено» и
 * «Сделать» проходит время, и час, который был впереди, успевает пройти.
 * Отвечает `null`, если всё годится, или причиной словами — её можно показать
 * человеку и передать модели: чужих данных в ней нет.
 */
object PayloadRules {

    /*
     * Пределы — предложений агента, а не самих записей Askya: в базе и на
     * экранах их нет, и человек, пишущий руками, им не подчиняется. Они
     * взяты из архитектуры агента (`AI_AGENT_ARCHITECTURE.md`, §9): то, что
     * модель предлагает одной карточкой, должно на неё помещаться. Меряется
     * текст без краевых пробелов — их при записи всё равно срежут.
     */

    /** Название дела. */
    const val MAX_TASK_TITLE = 120

    /** Название напоминания — столько же, сколько у дела: оно и бывает названием дела. */
    const val MAX_REMINDER_TITLE = 120

    /** Текст заметки. Имени заметки предела нет: архитектура его не задаёт. */
    const val MAX_NOTE_BODY = 20_000

    fun check(payload: CreateTaskPayload, now: LocalDateTime): String? {
        val title = payload.title.trim()
        if (title.isEmpty()) return "у дела нет названия"
        if (title.length > MAX_TASK_TITLE) return "название дела длиннее $MAX_TASK_TITLE знаков"
        if (payload.date.atTime(payload.start).isBefore(now)) return "время дела уже прошло"
        return payload.remind?.let { remind(it, payload.date, payload.start, now) }
    }

    fun check(payload: CreateReminderPayload, now: LocalDateTime): String? {
        val title = payload.title.trim()
        if (title.isEmpty()) return "у напоминания нет названия"
        if (title.length > MAX_REMINDER_TITLE) return "название напоминания длиннее $MAX_REMINDER_TITLE знаков"
        return remind(payload.remind, payload.date, payload.time, now)
    }

    fun check(payload: CreateNotePayload): String? {
        val body = payload.body.trim()
        if (body.isEmpty()) return "заметка пустая"
        if (body.length > MAX_NOTE_BODY) return "заметка длиннее $MAX_NOTE_BODY знаков"
        return null
    }

    private fun remind(remind: RemindAt, date: LocalDate, start: LocalTime, now: LocalDateTime): String? {
        if (remind is RemindAt.Before && remind.minutes <= 0) {
            return "напомнить можно только заранее — хотя бы за минуту"
        }
        // Тем же правилом, каким момент звонка считает само напоминание.
        if (!remindMoment(date, start, remind).isAfter(now)) {
            return "время напоминания уже прошло"
        }
        return null
    }
}
