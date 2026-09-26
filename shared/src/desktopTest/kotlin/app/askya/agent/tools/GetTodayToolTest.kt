package app.askya.agent.tools

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.useReaderConnection
import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.ToolCallOutcome
import app.askya.agent.ToolRegistry
import app.askya.agent.ToolResult
import app.askya.data.db.dao.ScheduleDao
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.reminderOf
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.RoutineRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.sync.Json
import app.askya.domain.model.RemindAt
import app.askya.testing.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `get_today` на настоящей базе Askya: что он отдаёт, в каком виде и что он
 * ничего не пишет.
 */
class GetTodayToolTest {

    private val base = TestDatabase()
    private val db = base.db
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2030, 5, 10)

    private val schedule = ScheduleRepository(db.scheduleDao())
    private val deedTasks = DeedTaskRepository(db.deedTaskDao())
    private val reminders = ReminderRepository(db.reminderDao())

    private val prefsFolder = Files.createTempDirectory("askya-settings").toFile()
    private val settings = SettingsPreferences(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(prefsFolder, "settings.preferences_pb").absolutePath.toPath() },
        ),
    )

    private val context = AgentContext(today, LocalTime.of(9, 0), zone, true, true)

    @AfterTest
    fun close() {
        base.close()
        prefsFolder.deleteRecursively()
    }

    private fun clockAt(hour: Int, minute: Int = 0): Clock =
        Clock.fixed(today.atTime(hour, minute).atZone(zone).toInstant(), zone)

    private fun tool(
        clock: Clock = clockAt(9, 30),
        scheduleRepository: ScheduleRepository = schedule,
    ) = GetTodayTool(scheduleRepository, deedTasks, reminders, settings, clock)

    private suspend fun read(clock: Clock = clockAt(9, 30)): Map<String, Any?> =
        assertIs<ToolResult.Ok>(tool(clock).read(emptyMap(), context)).data

    /** Настройка записана и уже дошла до `state` — он собирается в фоне. */
    private fun autoFill(value: Boolean) = runBlocking {
        settings.setAutoFillDay(value)
        withTimeout(5_000) { settings.state.first { it.autoFillDay == value } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = this[key] as List<Map<String, Any?>>

    // --- Пустой день ---------------------------------------------------------

    @Test
    fun `пустой день`() = runTest {
        autoFill(true)
        val data = read()

        assertEquals("2030-05-10", data["date"])
        assertEquals("2030-05-10T09:30", data["now"])
        assertEquals(emptyList<Any>(), data["deeds"])
        assertEquals(emptyMap<String, Any>(), data["tasks"])
        assertEquals(emptyList<Any>(), data["reminders"])
        assertNull(data["current"])
        assertEquals(true, data["autoFillDay"])
    }

    @Test
    fun `autoFillDay берётся из настроек`() = runTest {
        autoFill(false)
        assertEquals(false, read()["autoFillDay"])
        autoFill(true)
        assertEquals(true, read()["autoFillDay"])
    }

    // --- Дела ----------------------------------------------------------------

    @Test
    fun `одно дело — только нужные поля`() = runTest {
        val id = schedule.add(
            ScheduleItem(
                date = today,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(10, 30),
                title = "Созвон",
                note = "про отпуск",
                done = true,
                link = "note:5",
            ),
        )
        // Чужой день и убранное дело в ответ не попадают.
        schedule.add(ScheduleItem(date = today.plusDays(1), startTime = LocalTime.of(9, 0), title = "Завтра"))
        schedule.remove(schedule.add(ScheduleItem(date = today, startTime = LocalTime.of(11, 0), title = "Убрано")))

        val deed = read().list("deeds").single()
        assertEquals(
            mapOf(
                "id" to id,
                "start" to "09:00",
                "end" to "10:30",
                "title" to "Созвон",
                "note" to "про отпуск",
                "done" to true,
                "link" to "note:5",
            ),
            deed,
        )
    }

    @Test
    fun `дело со строками`() = runTest {
        val id = schedule.add(ScheduleItem(date = today, startTime = LocalTime.of(9, 0), title = "Магазин"))
        deedTasks.addLines(id, "# Молочное\nмолоко\nкефир")
        val kefir = deedTasks.tasksOnce(today).first { it.text == "кефир" }
        deedTasks.toggle(kefir)
        val gone = deedTasks.tasksOnce(today).first { it.text == "молоко" }
        deedTasks.remove(gone.id)

        @Suppress("UNCHECKED_CAST")
        val tasks = read()["tasks"] as Map<String, List<Map<String, Any?>>>

        assertEquals(setOf(id.toString()), tasks.keys)
        assertEquals(
            listOf(
                mapOf("text" to "Молочное", "done" to false, "heading" to true),
                mapOf("text" to "кефир", "done" to true, "heading" to false),
            ),
            tasks.getValue(id.toString()),
            "убранная строка «молоко» в ответ не попала",
        )
    }

    @Test
    fun `current — по правилу DayPlan`() = runTest {
        val first = schedule.add(
            ScheduleItem(date = today, startTime = LocalTime.of(9, 0), endTime = LocalTime.of(10, 0), title = "Работа"),
        )
        // Без конца: по DayPlan такое дело длится час.
        val second = schedule.add(ScheduleItem(date = today, startTime = LocalTime.of(12, 0), title = "Обед"))

        assertNull(read(clockAt(8, 0))["current"], "до первого дела")
        assertEquals(first, read(clockAt(9, 30))["current"], "внутри первого")
        assertNull(read(clockAt(11, 0))["current"], "между делами — первое кончилось в 10:00")
        assertEquals(second, read(clockAt(12, 30))["current"], "внутри второго")
        assertNull(read(clockAt(14, 0))["current"], "после последнего — второе кончилось в 13:00")
    }

    // --- Напоминания ---------------------------------------------------------

    @Test
    fun `напоминание сегодняшнего события попадает, чужого — нет`() = runTest {
        val deed = schedule.add(ScheduleItem(date = today, startTime = LocalTime.of(9, 0), title = "Созвон"))
        val todays = reminders.add(
            reminderOf(
                title = "Созвон",
                eventDate = today,
                eventStart = LocalTime.of(9, 0),
                remind = RemindAt.Before(15),
                itemId = deed,
            ),
        )
        // Звонит сегодня в 23:30, но о событии завтрашнего утра — не сегодняшнее.
        reminders.add(
            reminderOf(
                title = "Ранний поезд",
                eventDate = today.plusDays(1),
                eventStart = LocalTime.of(0, 30),
                remind = RemindAt.Before(60),
            ),
        )

        val reminder = read().list("reminders").single()
        assertEquals(
            mapOf(
                "id" to todays,
                "title" to "Созвон",
                "eventDate" to "2030-05-10",
                "eventStart" to "09:00",
                "eventEnd" to null,
                "ringsAt" to "2030-05-10T08:45",
                "lead" to 15,
                "enabled" to true,
                "deedId" to deed,
            ),
            reminder,
        )
    }

    // --- JSON и границы ------------------------------------------------------

    @Test
    fun `ответ записывается существующим Json и проходит ход`() = runTest {
        val id = schedule.add(ScheduleItem(date = today, startTime = LocalTime.of(9, 0), title = "Созвон"))
        deedTasks.addLines(id, "план")
        reminders.add(
            reminderOf("Созвон", today, LocalTime.of(9, 0), RemindAt.Exact(LocalTime.of(8, 50)), itemId = id),
        )

        val json = Json.write(read())
        assertTrue(json.contains("\"start\":\"09:00\""), json)

        val outcome = ToolRegistry(listOf(tool()))
            .openTurn(AgentPolicy(), context)
            .call("get_today", emptyMap())
        assertIs<ToolCallOutcome.Read>(outcome)
    }

    @Test
    fun `ошибка базы — Failed без подробностей, ход не падает`() = runTest {
        val broken = object : ScheduleDao by db.scheduleDao() {
            override suspend fun itemsOn(date: LocalDate): List<ScheduleItem> =
                error("no such column: title = «личное»")
        }
        val result = tool(scheduleRepository = ScheduleRepository(broken)).read(emptyMap(), context)

        val failed = assertIs<ToolResult.Failed>(result)
        assertTrue("личное" !in failed.reason && "column" !in failed.reason, failed.reason)
    }

    @Test
    fun `get_today ничего не пишет`() = runTest {
        // Распорядок на сегодня: если бы инструмент собирал день, дело из него
        // встало бы в schedule_items, а отметка — в generated_days.
        RoutineRepository(db).add(RoutineItem(title = "Зарядка", startTime = LocalTime.of(7, 0)))
        val id = schedule.add(ScheduleItem(date = today, startTime = LocalTime.of(9, 0), title = "Созвон"))
        deedTasks.addLines(id, "план")
        reminders.add(reminderOf("Созвон", today, LocalTime.of(9, 0), RemindAt.Before(10), itemId = id))

        val before = snapshot()
        repeat(3) { read() }
        val after = snapshot()

        assertEquals(before, after)
        assertTrue(before.getValue("generated_days").isEmpty(), "день не собран")
    }

    /** Все таблицы базы построчно — чтобы сравнить «до» и «после». */
    private suspend fun snapshot(): Map<String, List<String>> = db.useReaderConnection { connection ->
        val tables = connection.usePrepared(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        ) { statement ->
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
        tables.associateWith { table ->
            connection.usePrepared("SELECT * FROM `$table`") { statement ->
                buildList {
                    while (statement.step()) {
                        add(
                            (0 until statement.getColumnCount()).joinToString("|") { column ->
                                if (statement.isNull(column)) "∅" else statement.getText(column)
                            },
                        )
                    }
                }.sorted()
            }
        }
    }
}
