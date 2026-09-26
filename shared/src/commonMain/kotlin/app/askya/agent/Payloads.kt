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

    fun check(payload: CreateTaskPayload, now: LocalDateTime): String? {
        if (payload.title.isBlank()) return "у дела нет названия"
        if (payload.date.atTime(payload.start).isBefore(now)) return "время дела уже прошло"
        return payload.remind?.let { remind(it, payload.date, payload.start, now) }
    }

    fun check(payload: CreateReminderPayload, now: LocalDateTime): String? {
        if (payload.title.isBlank()) return "у напоминания нет названия"
        return remind(payload.remind, payload.date, payload.time, now)
    }

    fun check(payload: CreateNotePayload): String? =
        if (payload.body.isBlank()) "заметка пустая" else null

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
