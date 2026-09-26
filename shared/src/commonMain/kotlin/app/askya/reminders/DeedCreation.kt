package app.askya.reminders

import app.askya.data.db.AppDatabase
import app.askya.data.db.withTransaction
import app.askya.data.entity.Reminder
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.reminderOf
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.RemindAt
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * Создание дела дня и напоминания — одно место на всех, кто их заводит:
 * карточку дела в AskyaDay, экран напоминаний и агента.
 *
 * Правило о напоминаниях прежнее: **источник истины — строка в базе**, а
 * будильник системы — только способ донести её вовремя. Будильники теряются
 * при перезагрузке и обновлении приложения, и `ReminderBootReceiver` ставит их
 * заново из базы; поэтому отказ будильника не отменяет записанного. Но и не
 * замалчивается: тот, кто создал, получает [Alarm] и может сказать человеку,
 * что звонок не гарантирован.
 */

/**
 * Что стало с будильником только что записанного напоминания.
 *
 * Спросить у системы «поставлен ли» нельзя — [ReminderClock.schedule] ничего
 * не отвечает, — поэтому это лучшее, что известно: упал ли вызов, прошло ли
 * время и есть ли право звонить минута в минуту.
 */
enum class Alarm {
    /** Поставлен точно. */
    SET,

    /** Поставлен, но без права на точность: может прозвучать с опозданием. */
    INEXACT,

    /** Время звонка уже прошло — будильник не ставится, как и раньше. */
    PASSED,

    /** Поставить не удалось. Напоминание записано, но само может не прозвучать. */
    NOT_SET,
}

data class CreatedReminder(val id: Long, val alarm: Alarm)

/**
 * Новое напоминание: записать и завести будильник.
 *
 * Будильник заводится уже с номером из базы: по нему приёмник
 * (`ReminderReceiver`) находит напоминание, когда оно звонит, — без номера
 * звонить было бы не о чем.
 */
class ReminderCreator(
    private val reminders: ReminderRepository,
    private val alarms: ReminderClock,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {

    suspend fun create(reminder: Reminder): CreatedReminder {
        val saved = store(reminder)
        return CreatedReminder(saved.id, arm(saved))
    }

    /** Только записать. Будильник — [arm], когда запись уже окончательна. */
    internal suspend fun store(reminder: Reminder): Reminder =
        reminder.copy(id = reminders.add(reminder))

    /**
     * Завести будильник записанного напоминания. Зовётся и для прошедшего
     * времени — сам будильник его не ставит, и поведение остаётся прежним.
     */
    internal fun arm(saved: Reminder): Alarm {
        val passed = !saved.date.atTime(saved.time).isAfter(now())
        try {
            alarms.schedule(saved)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return Alarm.NOT_SET
        }
        return when {
            passed -> Alarm.PASSED
            !alarms.exact -> Alarm.INEXACT
            else -> Alarm.SET
        }
    }
}

/**
 * Новое дело дня — со всем, что человек о нём сказал, и напоминанием, если оно
 * нужно. Не сущность Room: номера у него ещё нет, и появится он только в базе.
 */
data class NewDeed(
    val date: LocalDate,
    val start: LocalTime,
    val title: String,
    val end: LocalTime? = null,
    val note: String = "",
    val icon: BlockIcon? = null,
    val link: String? = null,
    val remind: RemindAt? = null,
    val silent: Boolean = false,
    val sound: String? = null,
    val soundTitle: String? = null,
) {
    /**
     * Напоминание об этом деле, если оно нужно. Одно правило на создание и на
     * правку: у напоминания в карточке названы час и название дела, и они
     * должны совпадать с делом.
     */
    fun reminderFor(itemId: Long): Reminder? = remind?.let { remind ->
        reminderOf(
            title = title,
            eventDate = date,
            eventStart = start,
            eventEnd = end,
            remind = remind,
            icon = icon,
            silent = silent,
            sound = sound,
            soundTitle = soundTitle,
            itemId = itemId,
        )
    }
}

data class CreatedDeed(val deedId: Long, val reminder: CreatedReminder?)

/**
 * Завести дело дня вместе с напоминанием.
 *
 * Дело и напоминание пишутся одной транзакцией: упала вторая запись — нет и
 * первой, и в дне не остаётся дела, о котором человек просил напомнить, а
 * напоминания нет. Будильник заводится после, когда обе записи уже в базе:
 * его отказ записанного не отменяет (см. [Alarm]).
 *
 * Ошибка базы — исключение, как и раньше: тот, кто звал, сам решает, что
 * сказать человеку.
 */
class DeedCreator(
    private val db: AppDatabase,
    private val schedule: ScheduleRepository,
    private val reminders: ReminderCreator,
) {

    suspend fun create(deed: NewDeed): CreatedDeed {
        val (deedId, saved) = db.withTransaction {
            val id = schedule.add(
                ScheduleItem(
                    date = deed.date,
                    startTime = deed.start,
                    endTime = deed.end,
                    title = deed.title,
                    note = deed.note,
                    icon = deed.icon,
                    link = deed.link,
                ),
            )
            id to deed.reminderFor(id)?.let { reminders.store(it) }
        }
        return CreatedDeed(
            deedId = deedId,
            reminder = saved?.let { CreatedReminder(it.id, reminders.arm(it)) },
        )
    }
}
