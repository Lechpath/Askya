package app.askya.agent.apply

import app.askya.agent.CreateNotePayload
import app.askya.agent.CreateReminderPayload
import app.askya.agent.CreateTaskPayload
import app.askya.agent.PayloadRules
import app.askya.data.entity.reminderOf
import app.askya.data.repository.NoteRepository
import app.askya.reminders.Alarm
import app.askya.reminders.CreatedReminder
import app.askya.reminders.DeedCreator
import app.askya.reminders.NewDeed
import app.askya.reminders.ReminderCreator
import java.time.LocalDateTime

/*
 * Обработчики предложений v0.1. Каждый проверяет данные заново и зовёт ту же
 * операцию, которой пользуется обычный экран, — своей копии последовательности
 * «дело → напоминание → будильник» у агента нет.
 */

/** Новое дело дня — через [DeedCreator], как из карточки дела в AskyaDay. */
class CreateTaskHandler(
    private val deeds: DeedCreator,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ProposalHandler<CreateTaskPayload> {

    override val payloadType = CreateTaskPayload::class

    override suspend fun apply(payload: CreateTaskPayload): HandlerResult {
        PayloadRules.check(payload, now())?.let { return HandlerResult.Invalid(it) }
        val created = deeds.create(
            NewDeed(
                date = payload.date,
                start = payload.start,
                title = payload.title.trim(),
                end = payload.end,
                note = payload.note.trim(),
                remind = payload.remind,
            ),
        )
        return HandlerResult.Done(
            data = mapOf(
                "deedId" to created.deedId,
                "reminderId" to created.reminder?.id,
                "alarm" to created.reminder?.alarm?.name,
            ),
            warnings = listOfNotNull(created.reminder?.let(::warningOf)),
        )
    }
}

/** Отдельное напоминание — через [ReminderCreator], как с экрана напоминаний. */
class CreateReminderHandler(
    private val reminders: ReminderCreator,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ProposalHandler<CreateReminderPayload> {

    override val payloadType = CreateReminderPayload::class

    override suspend fun apply(payload: CreateReminderPayload): HandlerResult {
        PayloadRules.check(payload, now())?.let { return HandlerResult.Invalid(it) }
        val created = reminders.create(
            reminderOf(
                title = payload.title.trim(),
                eventDate = payload.date,
                eventStart = payload.time,
                remind = payload.remind,
            ),
        )
        return HandlerResult.Done(
            data = mapOf("reminderId" to created.id, "alarm" to created.alarm.name),
            warnings = listOfNotNull(warningOf(created)),
        )
    }
}

/** Быстрая заметка — [NoteRepository.quickNote], как кнопкой из меню. */
class CreateNoteHandler(private val notes: NoteRepository) : ProposalHandler<CreateNotePayload> {

    override val payloadType = CreateNotePayload::class

    override suspend fun apply(payload: CreateNotePayload): HandlerResult {
        PayloadRules.check(payload)?.let { return HandlerResult.Invalid(it) }
        val id = notes.quickNote(payload.title, payload.body)
        return HandlerResult.Done(data = mapOf("noteId" to id))
    }
}

/**
 * Что сказать о будильнике, если звонок не гарантирован. Напоминание при этом
 * записано: база — источник истины, и будильник поставится заново при
 * перезагрузке или обновлении приложения.
 */
private fun warningOf(reminder: CreatedReminder): String? = when (reminder.alarm) {
    Alarm.SET -> null
    Alarm.INEXACT -> "напоминание может прийти с опозданием: у Askya нет права на точный будильник"
    Alarm.PASSED -> "время напоминания уже прошло — оно сохранено, но не прозвучит"
    Alarm.NOT_SET -> "будильник не поставился: напоминание сохранено, но может не прозвучать"
}
