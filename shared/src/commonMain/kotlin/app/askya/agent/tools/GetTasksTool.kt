package app.askya.agent.tools

import app.askya.agent.AgentContext
import app.askya.agent.ArgsResult
import app.askya.agent.ReadTool
import app.askya.agent.ToolResult
import app.askya.agent.readArgs
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.YetRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate

/**
 * `get_tasks` — дела на дату со строками их списков и, по просьбе, списки Yet.
 *
 * Отличается от `get_today` тем, что спрашивают о любой дате — прошлой тоже, —
 * и нет ни текущего дела, ни напоминаний: это вопрос «что у меня на этот день»,
 * а не «что сейчас».
 *
 * Только читает: день не собирает, отметок не ставит, списки не трогает.
 *
 * **Контракт ответа** — ключи всегда одни и те же:
 * ```
 * {
 *   "date": "2026-09-26",
 *   "deeds": [ {id, start, end, title, note, done, link, tasks: [{text, done, heading}]} ],
 *   "lists": null | [ {id, title, items: [{text, done}]} ]
 * }
 * ```
 * `lists` — `null`, если списков не просили, и `[]`, если просили, а их нет:
 * «не спрашивали» и «нет ни одного» — разные ответы, и модель не должна их
 * путать.
 *
 * `includeDone` убирает из ответа сделанные **строки** — дела и пунктов Yet.
 * Сами дела со `done = true` остаются: дело дня и строка списка — разное, и
 * сделанное дело — тоже часть дня.
 */
class GetTasksTool(
    private val schedule: ScheduleRepository,
    private val deedTasks: DeedTaskRepository,
    private val yet: YetRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ReadTool {

    override val name = "get_tasks"

    override val description =
        "Дела Askya на указанную дату со строками их списков; по просьбе — ещё и " +
            "списки Yet. Дата в формате ГГГГ-ММ-ДД, без неё — сегодня. Прошлые и " +
            "будущие даты можно. Ничего не меняет."

    override val inputSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "date" to mapOf(
                "type" to "string",
                "description" to "Дата ГГГГ-ММ-ДД. Не указана — сегодня.",
            ),
            "includeDone" to mapOf(
                "type" to "boolean",
                "description" to "Включать ли сделанные строки. По умолчанию — нет.",
            ),
            "includeLists" to mapOf(
                "type" to "boolean",
                "description" to "Добавить ли списки Yet. По умолчанию — нет.",
            ),
        ),
        "additionalProperties" to false,
    )

    override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult {
        val request = when (val parsed = parse(input)) {
            is ArgsResult.Bad -> return ToolResult.Failed(parsed.reason)
            is ArgsResult.Ok -> parsed.value
        }
        return try {
            ToolResult.Ok(answer(request))
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Ни текста исключения, ни SQL: в них бывает то, что человек записал.
            ToolResult.Failed(COULD_NOT_READ)
        }
    }

    private suspend fun answer(request: Request): Map<String, Any?> {
        val deeds = schedule.itemsOnce(request.date)
        // Одним запросом на день — не по запросу на дело.
        val rows = deedTasks.tasksOnce(request.date)
            .filter { request.includeDone || !it.done }
            .groupBy { it.deedId }
        return mapOf(
            "date" to request.date.toString(),
            "deeds" to deeds.map { deed ->
                deed.agentView() + ("tasks" to rows[deed.id].orEmpty().map { it.agentView() })
            },
            // Не просили — к спискам не обращаемся вовсе.
            "lists" to if (request.includeLists) lists(request.includeDone) else null,
        )
    }

    private suspend fun lists(includeDone: Boolean): List<Map<String, Any?>> {
        val lists = yet.lists().first()
        val items = yet.itemsByList().first()
        return lists.map { list -> listView(list, items[list.id].orEmpty(), includeDone) }
    }

    private fun listView(list: YetList, items: List<YetItem>, includeDone: Boolean): Map<String, Any?> =
        mapOf(
            "id" to list.id,
            "title" to list.title,
            "items" to items.filter { includeDone || !it.done }
                .map { mapOf("text" to it.text, "done" to it.done) },
        )

    // --- Аргументы -----------------------------------------------------------

    private data class Request(val date: LocalDate, val includeDone: Boolean, val includeLists: Boolean)

    /** Строго по схеме — общим разбором [readArgs]: лишний ключ или не тот вид — отказ словами. */
    private fun parse(input: Map<String, Any?>): ArgsResult<Request> = readArgs(input, KEYS) {
        Request(
            date = date("date") ?: LocalDate.now(clock),
            includeDone = boolean("includeDone"),
            includeLists = boolean("includeLists"),
        )
    }

    private companion object {
        val KEYS = setOf("date", "includeDone", "includeLists")
        const val COULD_NOT_READ = "не удалось прочитать дела"
    }
}
