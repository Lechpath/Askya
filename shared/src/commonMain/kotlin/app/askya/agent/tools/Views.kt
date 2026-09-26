package app.askya.agent.tools

import app.askya.data.entity.DeedTask
import app.askya.data.entity.ScheduleItem

/*
 * Как записи Askya выглядят для модели — одно описание на все инструменты,
 * чтобы дело в `get_today` и в `get_tasks` было одним и тем же делом.
 *
 * Только нужное для разговора: без `uid`, `removedAt` и прочего служебного.
 * Даты и часы — строками в том виде, в каком их пишет база (`Converters`:
 * `toString()` у `java.time`), номера — числами.
 */

/** Дело дня. `end` — `null`, если конца у дела нет. */
internal fun ScheduleItem.agentView(): Map<String, Any?> = mapOf(
    "id" to id,
    "start" to startTime.toString(),
    "end" to endTime?.toString(),
    "title" to title,
    "note" to note,
    "done" to done,
    "link" to link,
)

/** Строка списка внутри дела. */
internal fun DeedTask.agentView(): Map<String, Any?> = mapOf(
    "text" to text,
    "done" to done,
    "heading" to heading,
)
