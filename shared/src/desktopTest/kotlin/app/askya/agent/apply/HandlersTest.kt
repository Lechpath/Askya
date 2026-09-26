package app.askya.agent.apply

import app.askya.agent.CreateNotePayload
import app.askya.agent.CreateReminderPayload
import app.askya.agent.CreateTaskPayload
import app.askya.agent.Proposal
import app.askya.agent.ProposalId
import app.askya.agent.ProposalPayload
import app.askya.agent.ProposalStatus
import app.askya.data.db.dao.ReminderDao
import app.askya.data.entity.Reminder
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.domain.model.RemindAt
import app.askya.reminders.DeedCreator
import app.askya.reminders.ReminderCreator
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.RecordingClock
import app.askya.testing.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Предложения, применённые по-настоящему: исполнитель, обработчик и общая
 * операция на живой базе. Проверяется, что агент ничего не делает своей
 * дорогой и что повторная проверка стоит перед записью.
 */
class HandlersTest {

    private val base = TestDatabase()
    private val db = base.db
    private val alarms = RecordingClock()
    private var now = LocalDateTime.of(2030, 5, 10, 8, 0)
    private val day = LocalDate.of(2030, 5, 10)
    private val instant = Instant.parse("2030-05-10T05:00:00Z")

    private val schedule = ScheduleRepository(db.scheduleDao())
    private val reminders = ReminderRepository(db.reminderDao())
    private val notes = NoteRepository(db.noteDao(), db.topicDao(), db.albumDao(), NoImages, NoVoices)

    private fun executor(reminderDao: ReminderDao = db.reminderDao()): ProposalExecutor {
        val creator = ReminderCreator(ReminderRepository(reminderDao), alarms) { now }
        return ProposalExecutor(
            listOf(
                CreateTaskHandler(DeedCreator(db, schedule, creator)) { now },
                CreateReminderHandler(creator) { now },
                CreateNoteHandler(notes),
            ),
            Clock.fixed(instant, ZoneOffset.UTC),
        )
    }

    private fun confirmed(payload: ProposalPayload) = Proposal(
        id = ProposalId.new(),
        tool = "test",
        summary = "проверка",
        payload = payload,
        status = ProposalStatus.PENDING,
        createdAt = instant,
        updatedAt = instant,
    ).moveTo(ProposalStatus.CONFIRMED, instant)

    private val task = CreateTaskPayload(
        title = "  Созвон  ",
        date = day,
        start = LocalTime.of(9, 0),
        remind = RemindAt.Before(15),
    )

    @AfterTest
    fun close() = base.close()

    @Test
    fun `дело через предложение — то же, что из карточки`() = runTest {
        val outcome = assertIs<ApplyOutcome.Applied>(executor().apply(confirmed(task)))

        val deed = schedule.itemsOnce(day).single()
        assertEquals("Созвон", deed.title)
        val reminder = reminders.reminders().first().single()
        assertEquals(deed.id, reminder.itemId)
        assertEquals(reminder.id, alarms.scheduled.single().id)
        assertEquals(deed.id, outcome.data["deedId"])
        assertEquals(reminder.id, outcome.data["reminderId"])
        assertTrue(outcome.warnings.isEmpty())
    }

    @Test
    fun `дело без напоминания`() = runTest {
        assertIs<ApplyOutcome.Applied>(executor().apply(confirmed(task.copy(remind = null))))

        assertEquals(1, schedule.itemsOnce(day).size)
        assertTrue(reminders.reminders().first().isEmpty())
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `двойное применение — одно дело`() = runTest {
        val executor = executor()
        val proposal = confirmed(task)
        executor.apply(proposal)
        executor.apply(proposal)

        assertEquals(1, schedule.itemsOnce(day).size)
        assertEquals(1, reminders.reminders().first().size)
    }

    @Test
    fun `прошедшее время — FAILED, ничего не записано`() = runTest {
        now = LocalDateTime.of(2030, 5, 10, 9, 30)
        val failed = assertIs<ApplyOutcome.Failed>(executor().apply(confirmed(task)))

        assertEquals("время дела уже прошло", failed.reason)
        assertTrue(schedule.itemsOnce(day).isEmpty())
    }

    @Test
    fun `время прошло между предложением и нажатием — проверка заново`() = runTest {
        // Предложено в 8:00, дело в 9:00 с напоминанием за 15 минут. Нажали в 8:50:
        // дело ещё впереди, а звонок — уже нет.
        val proposal = confirmed(task)
        now = LocalDateTime.of(2030, 5, 10, 8, 50)
        val failed = assertIs<ApplyOutcome.Failed>(executor().apply(proposal))

        assertEquals("время напоминания уже прошло", failed.reason)
        assertTrue(schedule.itemsOnce(day).isEmpty())
    }

    @Test
    fun `невалидное напоминание — FAILED`() = runTest {
        val failed = assertIs<ApplyOutcome.Failed>(
            executor().apply(confirmed(task.copy(remind = RemindAt.Before(0)))),
        )
        assertTrue(failed.reason.contains("заранее"))
        assertTrue(schedule.itemsOnce(day).isEmpty())
    }

    @Test
    fun `пустое название — FAILED`() = runTest {
        assertIs<ApplyOutcome.Failed>(executor().apply(confirmed(task.copy(title = "   "))))
        assertTrue(schedule.itemsOnce(day).isEmpty())
    }

    @Test
    fun `ошибка репозитория — FAILED без подробностей и без дела`() = runTest {
        val broken = object : ReminderDao by db.reminderDao() {
            override suspend fun insert(reminder: Reminder): Long = error("SQLITE_FULL: «Созвон»")
        }
        val failed = assertIs<ApplyOutcome.Failed>(executor(reminderDao = broken).apply(confirmed(task)))

        assertEquals("не удалось сохранить", failed.reason)
        assertTrue(schedule.itemsOnce(day).isEmpty())
    }

    @Test
    fun `отказ будильника — APPLIED с предупреждением`() = runTest {
        alarms.failing = true
        val outcome = assertIs<ApplyOutcome.Applied>(executor().apply(confirmed(task)))

        assertEquals(1, schedule.itemsOnce(day).size)
        assertEquals(1, reminders.reminders().first().size)
        assertEquals("NOT_SET", outcome.data["alarm"])
        assertTrue(outcome.warnings.single().contains("может не прозвучать"))
    }

    @Test
    fun `отдельное напоминание`() = runTest {
        val payload = CreateReminderPayload(title = "Лекарство", date = day, time = LocalTime.of(21, 0))
        val outcome = assertIs<ApplyOutcome.Applied>(executor().apply(confirmed(payload)))

        val reminder = reminders.reminders().first().single()
        assertEquals(null, reminder.itemId)
        assertEquals(LocalTime.of(21, 0), reminder.time)
        assertEquals(reminder.id, outcome.data["reminderId"])
        assertEquals(reminder.id, alarms.scheduled.single().id)
    }

    @Test
    fun `напоминание в прошлом — FAILED`() = runTest {
        val payload = CreateReminderPayload(title = "Лекарство", date = day, time = LocalTime.of(7, 0))
        assertIs<ApplyOutcome.Failed>(executor().apply(confirmed(payload)))
        assertTrue(reminders.reminders().first().isEmpty())
    }

    @Test
    fun `заметка`() = runTest {
        val outcome = assertIs<ApplyOutcome.Applied>(
            executor().apply(confirmed(CreateNotePayload(title = "Покупки", body = "хлеб\nмолоко"))),
        )
        val note = notes.notes().first().single()
        assertEquals("Покупки", note.title)
        assertEquals(note.id, outcome.data["noteId"])
    }

    @Test
    fun `пустая заметка — FAILED`() = runTest {
        assertIs<ApplyOutcome.Failed>(executor().apply(confirmed(CreateNotePayload(title = "x", body = " "))))
        assertTrue(notes.notes().first().isEmpty())
    }
}
