package app.askya.agent.tools

import app.askya.agent.AgentContext
import app.askya.agent.AgentPolicy
import app.askya.agent.ToolCallOutcome
import app.askya.agent.ToolRegistry
import app.askya.agent.ToolResult
import app.askya.data.db.dao.ScheduleDao
import app.askya.data.db.dao.YetDao
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.RoutineRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.YetRepository
import app.askya.data.sync.Json
import app.askya.domain.model.ListMark
import app.askya.testing.TestDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `get_tasks` на настоящей базе Askya: дата, дела, строки, списки Yet и
 * граница чтения.
 */
class GetTasksToolTest {

    private val base = TestDatabase()
    private val db = base.db
    private val zone = ZoneId.of("Europe/Moscow")
    private val today = LocalDate.of(2030, 5, 10)
    private val clock = Clock.fixed(today.atTime(9, 30).atZone(zone).toInstant(), zone)
    private val context = AgentContext(today, LocalTime.of(9, 30), zone, true, true)

    private val schedule = ScheduleRepository(db.scheduleDao())
    private val deedTasks = DeedTaskRepository(db.deedTaskDao())

    /** Считает каждое чтение списков Yet, которое делает репозиторий. */
    private inner class CountingYet(real: YetDao) : YetDao by real {
        private val inner = real
        var reads = 0
        override fun observeLists(): Flow<List<YetList>> { reads++; return inner.observeLists() }
        override fun observeAllItems(): Flow<List<YetItem>> { reads++; return inner.observeAllItems() }
        override fun observeItems(listId: Long): Flow<List<YetItem>> { reads++; return inner.observeItems(listId) }
        override fun observeList(id: Long): Flow<YetList?> { reads++; return inner.observeList(id) }
        override fun observeRecentLists(limit: Int): Flow<List<YetList>> { reads++; return inner.observeRecentLists(limit) }
    }

    private val yetDao = CountingYet(db.yetDao())
    private val yet = YetRepository(yetDao)

    @AfterTest
    fun close() = base.close()

    private fun tool(scheduleRepository: ScheduleRepository = schedule) =
        GetTasksTool(scheduleRepository, deedTasks, yet, clock)

    private suspend fun read(vararg args: Pair<String, Any?>): Map<String, Any?> =
        assertIs<ToolResult.Ok>(tool().read(mapOf(*args), context)).data

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String) = this[key] as List<Map<String, Any?>>

    private suspend fun deed(date: LocalDate, hour: Int, title: String, end: LocalTime? = null, note: String = "", done: Boolean = false, link: String? = null) =
        schedule.add(ScheduleItem(date = date, startTime = LocalTime.of(hour, 0), endTime = end, title = title, note = note, done = done, link = link))

    // --- Дата ----------------------------------------------------------------

    @Test
    fun `без даты — сегодня`() = runTest {
        deed(today, 9, "Сегодня")
        deed(today.plusDays(1), 9, "Завтра")

        val data = read()
        assertEquals("2030-05-10", data["date"])
        assertEquals(listOf("Сегодня"), data.list("deeds").map { it["title"] })
    }

    @Test
    fun `конкретная, прошлая и будущая дата`() = runTest {
        deed(today.minusYears(1), 9, "Год назад")
        deed(today.plusDays(3), 9, "Через три дня")

        assertEquals(listOf("Год назад"), read("date" to "2029-05-10").list("deeds").map { it["title"] })
        assertEquals(listOf("Через три дня"), read("date" to "2030-05-13").list("deeds").map { it["title"] })
        assertEquals("2030-05-13", read("date" to " 2030-05-13 ")["date"])
    }

    @Test
    fun `неправильная дата — Failed`() = runTest {
        listOf("завтра", "2030-13-01", "10.05.2030", "").forEach { bad ->
            assertIs<ToolResult.Failed>(tool().read(mapOf("date" to bad), context), bad)
        }
        assertIs<ToolResult.Failed>(tool().read(mapOf("date" to 20300510L), context))
    }

    @Test
    fun `неверные аргументы — Failed`() = runTest {
        assertIs<ToolResult.Failed>(tool().read(mapOf("includeDone" to "yes"), context))
        assertIs<ToolResult.Failed>(tool().read(mapOf("delete" to true), context))
    }

    // --- Дела ----------------------------------------------------------------

    @Test
    fun `несколько дел со всеми полями, сделанное дело остаётся`() = runTest {
        val first = deed(today, 9, "Созвон", end = LocalTime.of(10, 0), note = "про отпуск", link = "note:5")
        val second = deed(today, 12, "Обед", done = true)

        val deeds = read().list("deeds")
        assertEquals(
            listOf(
                mapOf(
                    "id" to first, "start" to "09:00", "end" to "10:00", "title" to "Созвон",
                    "note" to "про отпуск", "done" to false, "link" to "note:5", "tasks" to emptyList<Any>(),
                ),
                mapOf(
                    "id" to second, "start" to "12:00", "end" to null, "title" to "Обед",
                    "note" to "", "done" to true, "link" to null, "tasks" to emptyList<Any>(),
                ),
            ),
            deeds,
        )
    }

    // --- Строки дела ---------------------------------------------------------

    private suspend fun deedWithRows(): Long {
        val id = deed(today, 9, "Магазин")
        deedTasks.addLines(id, "# Молочное\nмолоко\nкефир")
        deedTasks.toggle(deedTasks.tasksOnce(today).first { it.text == "кефир" })
        return id
    }

    @Test
    fun `строки без сделанных по умолчанию`() = runTest {
        deedWithRows()
        val tasks = read().list("deeds").single().list("tasks")

        assertEquals(
            listOf(
                mapOf("text" to "Молочное", "done" to false, "heading" to true),
                mapOf("text" to "молоко", "done" to false, "heading" to false),
            ),
            tasks,
        )
    }

    @Test
    fun `includeDone — со сделанными строками`() = runTest {
        deedWithRows()
        val tasks = read("includeDone" to true).list("deeds").single().list("tasks")

        assertEquals(listOf("Молочное", "молоко", "кефир"), tasks.map { it["text"] })
        assertEquals(true, tasks.single { it["text"] == "кефир" }["done"])
    }

    @Test
    fun `строки лежат в своём деле`() = runTest {
        val shop = deedWithRows()
        val call = deed(today, 11, "Созвон")
        deedTasks.addLines(call, "повестка")

        val deeds = read().list("deeds").associateBy { it["id"] }
        assertEquals(listOf("Молочное", "молоко"), deeds.getValue(shop).list("tasks").map { it["text"] })
        assertEquals(listOf("повестка"), deeds.getValue(call).list("tasks").map { it["text"] })
    }

    // --- Yet -----------------------------------------------------------------

    private suspend fun groceries(): Long {
        val id = yet.addList("Покупки", ListMark.SQUARE)
        yet.addLines(id, "хлеб\nсыр")
        yet.toggle(yet.items(id).first().first { it.text == "сыр" })
        return id
    }

    @Test
    fun `без includeLists к спискам не обращаются`() = runTest {
        groceries()
        yetDao.reads = 0

        val data = read("includeDone" to true)

        assertNull(data["lists"])
        assertTrue("lists" in data, "ключ есть всегда — просто пустой")
        assertEquals(0, yetDao.reads, "YetRepository не спрашивали")
    }

    @Test
    fun `includeLists — списки отдельно от строк дел`() = runTest {
        val list = groceries()
        deedWithRows()
        yetDao.reads = 0

        val data = read("includeLists" to true)
        assertTrue(yetDao.reads > 0, "счётчик и правда видит обращения к спискам")
        assertEquals(
            listOf(mapOf("id" to list, "title" to "Покупки", "items" to listOf(mapOf("text" to "хлеб", "done" to false)))),
            data["lists"],
        )
        // В строках дела нет пунктов Yet, в списке — строк дела.
        val deedRows = data.list("deeds").single().list("tasks").map { it["text"] }
        assertTrue("хлеб" !in deedRows && "молоко" in deedRows)
    }

    @Test
    fun `includeLists с includeDone — сделанные пункты тоже`() = runTest {
        groceries()
        val items = read("includeLists" to true, "includeDone" to true).list("lists").single().list("items")

        assertEquals(listOf(mapOf("text" to "хлеб", "done" to false), mapOf("text" to "сыр", "done" to true)), items)
    }

    @Test
    fun `includeLists без списков — пустой массив, а не null`() = runTest {
        assertEquals(emptyList<Any>(), read("includeLists" to true)["lists"])
    }

    // --- Пусто, JSON, ошибки, чтение ----------------------------------------

    @Test
    fun `пустая дата`() = runTest {
        assertEquals(
            mapOf("date" to "2030-05-10", "deeds" to emptyList<Any>(), "lists" to null),
            read(),
        )
    }

    @Test
    fun `ответ записывается существующим Json и проходит ход`() = runTest {
        groceries()
        deedWithRows()
        val data = read("includeLists" to true, "includeDone" to true)
        assertTrue(Json.write(data).contains("\"lists\":[{"))

        val outcome = ToolRegistry(listOf(tool()))
            .openTurn(AgentPolicy(), context)
            .call("get_tasks", mapOf("includeLists" to true))
        assertIs<ToolCallOutcome.Read>(outcome)
    }

    @Test
    fun `ошибка базы — Failed без подробностей`() = runTest {
        val broken = object : ScheduleDao by db.scheduleDao() {
            override suspend fun itemsOn(date: LocalDate): List<ScheduleItem> = error("SQL: «личное»")
        }
        val failed = assertIs<ToolResult.Failed>(tool(ScheduleRepository(broken)).read(emptyMap(), context))
        assertTrue("личное" !in failed.reason && "SQL" !in failed.reason, failed.reason)
    }

    @Test
    fun `get_tasks ничего не пишет`() = runTest {
        RoutineRepository(db).add(RoutineItem(title = "Зарядка", startTime = LocalTime.of(7, 0)))
        deedWithRows()
        groceries()

        val before = base.snapshot()
        read()
        read("includeDone" to true, "includeLists" to true)
        read("date" to "2030-05-11", "includeLists" to true)
        val after = base.snapshot()

        assertEquals(before, after)
        assertTrue(before.getValue("generated_days").isEmpty(), "день не собран")
    }
}
