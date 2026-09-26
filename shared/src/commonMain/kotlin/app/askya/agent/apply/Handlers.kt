package app.askya.agent.apply

import app.askya.agent.CreateNotePayload
import app.askya.agent.CreateReminderPayload
import app.askya.agent.CreateTaskPayload
import app.askya.agent.PayloadRules
import app.askya.data.entity.reminderOf
import app.askya.data.repository.NoteRepository
import app.askya.domain.model.quickNoteTitle
import app.askya.reminders.DeedCreator
import app.askya.reminders.NewDeed
import app.askya.reminders.ReminderCreator
import java.time.LocalDateTime

/*
 * Обработчики предложений v0.1. Каждый проверяет данные заново и зовёт ту же
 * операцию, которой пользуется обычный экран, — своей копии последовательности
 * «дело → напоминание → будильник» у агента нет.
 */

/**
 * Новое дело дня — через [DeedCreator], как из карточки дела в AskyaDay.
 *
 * Известное ограничение: одинаковых дел здесь не ищут — правила «то же дело»
 * для двух дел дня в Askya нет. Два одинаковых предложения, подтверждённые
 * порознь, заведут два дела.
 */
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
        return HandlerResult.Done(Applied.Task(created))
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
        val saved = reminders.create(
            reminderOf(
                title = payload.title.trim(),
                eventDate = payload.date,
                eventStart = payload.time,
                remind = payload.remind,
            ),
        )
        return HandlerResult.Done(Applied.Reminder(saved))
    }
}

/**
 * Быстрая заметка — [NoteRepository.quickNote] с именем по правилу быстрой
 * заметки ([quickNoteTitle]), как кнопкой из меню.
 */
class CreateNoteHandler(private val notes: NoteRepository) : ProposalHandler<CreateNotePayload> {

    override val payloadType = CreateNotePayload::class

    override suspend fun apply(payload: CreateNotePayload): HandlerResult {
        PayloadRules.check(payload)?.let { return HandlerResult.Invalid(it) }
        val title = quickNoteTitle(payload.title, payload.body)
        val id = notes.quickNote(title, payload.body)
        return HandlerResult.Done(Applied.Note(id, title))
    }
}
