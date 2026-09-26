package app.askya.agent.tools

import app.askya.agent.AgentContext
import app.askya.agent.ReadTool
import app.askya.agent.ToolResult
import app.askya.data.entity.DeedTask
import app.askya.data.entity.Reminder
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.remindAt
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.domain.model.DayPlan
import app.askya.domain.model.RemindAt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDateTime

/**
 * `get_today` — что у человека сегодня: дела, строки их списков, напоминания и
 * то дело, которое идёт сейчас.
 *
 * Только читает. День не собирает: `DayRepository.ensureComposed` — это запись,
 * и чтение не должно её вызывать. Поэтому в дне, который ещё не открывали, дел
 * из списка дел может не оказаться; `autoFillDay` в ответе говорит, соберётся
 * ли он сам при открытии.
 *
 * Дата и время — только из [clock], а не от модели: «сегодня» у агента то же,
 * что у телефона.
 *
 * Отдаёт выборку, а не сущности Room: без `uid`, `removedAt` и прочего
 * служебного. Даты и часы — строками в том же виде, в каком их пишет база
 * (`Converters`: `toString()` у `java.time`), номера — числами.
 */
class GetTodayTool(
    private val schedule: ScheduleRepository,
    private val deedTasks: DeedTaskRepository,
    private val reminders: ReminderRepository,
    private val settings: SettingsPreferences,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ReadTool {

    override val name = "get_today"

    override val description =
        "Сегодняшний день в Askya: дела с часами, строки их списков, напоминания " +
            "на сегодня и дело, которое идёт сейчас. Ничего не меняет."

    override val inputSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any?>(),
        "additionalProperties" to false,
    )

    override suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult {
        val now = LocalDateTime.now(clock)
        val today = now.toLocalDate()
        return try {
            val deeds = schedule.itemsOnce(today)
            val tasks = deedTasks.tasksOnce(today)
            val todays = reminders.reminders().first().filter { it.eventDate == today }
            ToolResult.Ok(
                mapOf(
                    "date" to today.toString(),
                    "now" to now.toString(),
                    "deeds" to deeds.map(::deedOf),
                    "tasks" to tasksOf(tasks),
                    "reminders" to todays.map(::reminderOf),
                    "current" to DayPlan(today, deeds).currentBlock(now.toLocalTime())?.id,
                    "autoFillDay" to settings.state.value.autoFillDay,
                ),
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            // Ни текста исключения, ни SQL: в них бывает то, что человек записал.
            ToolResult.Failed(COULD_NOT_READ)
        }
    }

    private fun deedOf(item: ScheduleItem): Map<String, Any?> = mapOf(
        "id" to item.id,
        "start" to item.startTime.toString(),
        "end" to item.endTime?.toString(),
        "title" to item.title,
        "note" to item.note,
        "done" to item.done,
        "link" to item.link,
    )

    /**
     * Строки, разложенные по делам. Ключ — номер дела строкой: у объекта JSON
     * ключи бывают только строками, а номер дела в самих делах — число.
     */
    private fun tasksOf(tasks: List<DeedTask>): Map<String, Any?> =
        tasks.groupBy { it.deedId }.entries.associate { (deedId, rows) ->
            deedId.toString() to rows.map { row ->
                mapOf("text" to row.text, "done" to row.done, "heading" to row.heading)
            }
        }

    /**
     * Напоминание — тем, чем оно видно человеку: о чём, к какому часу события,
     * когда прозвенит и как это задано. `lead` — за сколько минут до начала,
     * как хранит его сама база; `null` — названо прямое время звонка.
     */
    private fun reminderOf(reminder: Reminder): Map<String, Any?> = mapOf(
        "id" to reminder.id,
        "title" to reminder.title,
        "eventDate" to reminder.eventDate.toString(),
        "eventStart" to reminder.eventStart?.toString(),
        "eventEnd" to reminder.eventEnd?.toString(),
        "ringsAt" to reminder.date.atTime(reminder.time).toString(),
        "lead" to (reminder.remindAt as? RemindAt.Before)?.minutes,
        "enabled" to reminder.enabled,
        "deedId" to reminder.itemId,
    )

    private companion object {
        const val COULD_NOT_READ = "не удалось прочитать сегодняшний день"
    }
}
