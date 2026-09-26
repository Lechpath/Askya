package app.askya.agent.tools

import app.askya.agent.ToolRegistry
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.YetRepository
import java.time.Clock

/**
 * Все инструменты агента Askya — в одном месте. Что не перечислено здесь,
 * модели недоступно.
 *
 * Сейчас только чтение: `get_today`, `get_tasks`, `search_notes`. Инструментов
 * записи ещё нет (их обработчики в `agent.apply` ждут своих инструментов).
 *
 * Ledger сюда не входит и входить не будет (`AI_AGENT_ARCHITECTURE.md`, §8):
 * его репозитория среди параметров нет, и взять его инструментам неоткуда.
 */
fun askyaTools(
    schedule: ScheduleRepository,
    deedTasks: DeedTaskRepository,
    reminders: ReminderRepository,
    yet: YetRepository,
    notes: NoteRepository,
    settings: SettingsPreferences,
    clock: Clock = Clock.systemDefaultZone(),
): ToolRegistry = ToolRegistry(
    listOf(
        GetTodayTool(schedule, deedTasks, reminders, settings, clock),
        GetTasksTool(schedule, deedTasks, yet, clock),
        SearchNotesTool(notes),
    ),
)
