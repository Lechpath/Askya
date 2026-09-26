package app.askya.reminders

import app.askya.data.db.dao.ReminderDao
import app.askya.data.db.dao.ScheduleDao
import app.askya.data.entity.Reminder
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.reminderOf
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.domain.model.RemindAt
import app.askya.testing.RecordingClock
import app.askya.testing.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Создание дела с напоминанием — на настоящей базе. Проверяется то, ради чего
 * операцию вынесли: дело и напоминание пишутся вместе или не пишутся вовсе,
 * будильник получает номер из базы, а его отказ записанного не стирает.
 */
class DeedCreationTest {

    private val base = TestDatabase()
    private val db = base.db
    private val alarms = RecordingClock()
    private val now = LocalDateTime.of(2030, 5, 10, 8, 0)
    private val day = LocalDate.of(2030, 5, 10)

    private fun creator(
        scheduleDao: ScheduleDao = db.scheduleDao(),
        reminderDao: ReminderDao = db.reminderDao(),
    ): Pair<DeedCreator, ReminderRepository> {
        val reminders = ReminderRepository(reminderDao)
        val reminderCreator = ReminderCreator(reminders, alarms) { now }
        return DeedCreator(db, ScheduleRepository(scheduleDao), reminderCreator) to reminders
    }

    private suspend fun deedsOn(date: LocalDate) = ScheduleRepository(db.scheduleDao()).itemsOnce(date)
    private suspend fun allReminders() = ReminderRepository(db.reminderDao()).reminders().first()

    @AfterTest
    fun close() = base.close()

    @Test
    fun `создаётся дело`() = runTest {
        val (deeds, _) = creator()
        val created = deeds.create(NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон", note = "про отпуск"))

        val deed = deedsOn(day).single()
        assertEquals(created.deedId, deed.id)
        assertEquals("Созвон", deed.title)
        assertEquals("про отпуск", deed.note)
        assertEquals(LocalTime.of(9, 0), deed.startTime)
    }

    @Test
    fun `без remind напоминания нет`() = runTest {
        val (deeds, _) = creator()
        val created = deeds.create(NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон"))

        assertNull(created.reminder)
        assertTrue(allReminders().isEmpty())
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `с remind создаётся напоминание, и будильник получает его номер`() = runTest {
        val (deeds, _) = creator()
        val created = deeds.create(
            NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон", remind = RemindAt.Before(15)),
        )

        val reminder = allReminders().single()
        assertEquals(created.reminder?.id, reminder.id)
        assertEquals(created.deedId, reminder.itemId)
        assertEquals(LocalTime.of(8, 45), reminder.time)
        assertEquals(15, reminder.lead)

        val armed = alarms.scheduled.single()
        assertEquals(reminder.id, armed.id)
        assertTrue(armed.id > 0)
        assertEquals(SavedReminder.AlarmRequested(reminder.id, exact = true), created.reminder)
    }

    @Test
    fun `ошибка записи напоминания не оставляет дела`() = runTest {
        val broken = object : ReminderDao by db.reminderDao() {
            override suspend fun insert(reminder: Reminder): Long = error("диск полон")
        }
        val (deeds, _) = creator(reminderDao = broken)

        assertFailsWith<IllegalStateException> {
            deeds.create(NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон", remind = RemindAt.Before(15)))
        }
        assertTrue(deedsOn(day).isEmpty(), "дело откатилось вместе с напоминанием")
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `ошибка записи дела ничего не оставляет`() = runTest {
        val broken = object : ScheduleDao by db.scheduleDao() {
            override suspend fun insert(item: ScheduleItem): Long = error("диск полон")
        }
        val (deeds, _) = creator(scheduleDao = broken)

        assertFailsWith<IllegalStateException> {
            deeds.create(NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон", remind = RemindAt.Before(15)))
        }
        assertTrue(allReminders().isEmpty())
    }

    @Test
    fun `отказ будильника записанного не стирает`() = runTest {
        alarms.failing = true
        val (deeds, _) = creator()
        val created = deeds.create(
            NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон", remind = RemindAt.Before(15)),
        )

        // Вариант C: напоминание есть, звонка нет — и это видно, а не спрятано.
        val reminder = allReminders().single()
        assertEquals(SavedReminder.NoAlarm(reminder.id, NoAlarmReason.REQUEST_FAILED), created.reminder)
        assertEquals(1, deedsOn(day).size)
        assertEquals(1, allReminders().size, "база — источник истины: будильник поставится при перезапуске")
    }

    @Test
    fun `без права на точность будильник неточный`() = runTest {
        alarms.exactAllowed = false
        val (deeds, _) = creator()
        val created = deeds.create(
            NewDeed(date = day, start = LocalTime.of(9, 0), title = "Созвон", remind = RemindAt.Before(15)),
        )
        assertEquals(false, assertIs<SavedReminder.AlarmRequested>(created.reminder).exact)
    }

    @Test
    fun `прошедшее время звонка честно названо`() = runTest {
        val (deeds, _) = creator()
        val created = deeds.create(
            NewDeed(date = day, start = LocalTime.of(7, 0), title = "Зарядка", remind = RemindAt.Before(15)),
        )
        // Как и раньше, запись есть, а будильник её не ставит — об этом и сказано.
        assertEquals(NoAlarmReason.TIME_PASSED, assertIs<SavedReminder.NoAlarm>(created.reminder).reason)
    }

    @Test
    fun `результат называет созданное`() = runTest {
        val (deeds, _) = creator()
        val created = deeds.create(
            NewDeed(
                date = day,
                start = LocalTime.of(9, 0),
                end = LocalTime.of(9, 30),
                title = "Созвон",
                remind = RemindAt.Exact(LocalTime.of(8, 30)),
            ),
        )

        val deed = deedsOn(day).single()
        val reminder = allReminders().single()
        assertEquals(
            CreatedDeed(
                deedId = deed.id,
                date = day,
                start = LocalTime.of(9, 0),
                end = LocalTime.of(9, 30),
                title = "Созвон",
                reminder = SavedReminder.AlarmRequested(reminder.id, exact = true),
            ),
            created,
        )
        assertEquals(reminder.id, created.reminderId)
    }

    // --- Отдельное напоминание -----------------------------------------------

    private fun standalone() = reminderOf(
        title = "Лекарство",
        eventDate = day,
        eventStart = LocalTime.of(21, 0),
        remind = RemindAt.Exact(LocalTime.of(21, 0)),
    )

    @Test
    fun `напоминание с просьбой о звонке`() = runTest {
        val saved = ReminderCreator(ReminderRepository(db.reminderDao()), alarms) { now }.create(standalone())

        val reminder = allReminders().single()
        assertEquals(SavedReminder.AlarmRequested(reminder.id, exact = true), saved)
        assertEquals(reminder.id, alarms.scheduled.single().id)
    }

    @Test
    fun `напоминание без звонка остаётся в базе`() = runTest {
        alarms.failing = true
        val saved = ReminderCreator(ReminderRepository(db.reminderDao()), alarms) { now }.create(standalone())

        val reminder = allReminders().single()
        assertEquals(SavedReminder.NoAlarm(reminder.id, NoAlarmReason.REQUEST_FAILED), saved)
        assertTrue(alarms.scheduled.isEmpty())
    }

    @Test
    fun `ошибка записи напоминания — исключение, просьбы о звонке нет`() = runTest {
        val broken = object : ReminderDao by db.reminderDao() {
            override suspend fun insert(reminder: Reminder): Long = error("диск полон")
        }
        assertFailsWith<IllegalStateException> {
            ReminderCreator(ReminderRepository(broken), alarms) { now }.create(standalone())
        }
        assertTrue(alarms.scheduled.isEmpty())
    }
}
