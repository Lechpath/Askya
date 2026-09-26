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
 * будильник — только способ донести её вовремя. Будильники теряются при
 * перезагрузке и обновлении приложения, и `ReminderBootReceiver` ставит их
 * заново из базы; поэтому неудача с будильником не отменяет записанного. Но и
 * не замалчивается: результат прямо говорит, передана ли просьба о звонке.
 */

/**
 * Записанное напоминание и то, что стало с просьбой о звонке.
 *
 * **Чего здесь нет — гарантии доставки.** [ReminderClock.schedule] ничего не
 * отвечает, и спросить систему, поставлен ли будильник, нельзя. Известно одно:
 * просили ли о звонке и не упала ли просьба. На телефоне это просьба к
 * `AlarmManager`, которую система может отложить или потерять (экономия
 * заряда, «убийцы» фона у части прошивок); на компьютере — таймер внутри
 * запущенной Askya, который живёт, пока она открыта или в трее.
 *
 * Запечатано, чтобы случай «напоминание есть, а звонка не будет» нельзя было
 * пропустить: `when` по результату его потребует.
 */
sealed interface SavedReminder {
    val id: Long

    /**
     * Просьба о звонке передана и не упала. [exact] — было ли у Askya право
     * звонить минута в минуту; без него система вправе опоздать.
     */
    data class AlarmRequested(override val id: Long, val exact: Boolean) : SavedReminder

    /** Напоминание записано, но звонка не просили или просьба упала. */
    data class NoAlarm(override val id: Long, val reason: NoAlarmReason) : SavedReminder
}

enum class NoAlarmReason {
    /**
     * Время звонка уже прошло. Будильник такого не ставит — так было и до
     * этого: звонить о прошедшем незачем.
     */
    TIME_PASSED,

    /** Просьба о звонке упала. Напоминание есть, звонок — нет. */
    REQUEST_FAILED,
}

/**
 * Новое напоминание: записать и попросить о звонке.
 *
 * Просьба уходит уже с номером из базы: по нему приёмник (`ReminderReceiver`)
 * находит напоминание, когда оно звонит, — без номера звонить было бы не о чем.
 */
class ReminderCreator(
    private val reminders: ReminderRepository,
    private val alarms: ReminderClock,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) {

    suspend fun create(reminder: Reminder): SavedReminder = arm(store(reminder))

    /** Только записать. Просьба о звонке — [arm], когда запись уже окончательна. */
    internal suspend fun store(reminder: Reminder): Reminder =
        reminder.copy(id = reminders.add(reminder))

    /**
     * Попросить о звонке записанного напоминания. Зовётся и для прошедшего
     * времени — сам будильник его не ставит, и поведение остаётся прежним.
     */
    internal fun arm(saved: Reminder): SavedReminder {
        val passed = !saved.date.atTime(saved.time).isAfter(now())
        try {
            alarms.schedule(saved)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return SavedReminder.NoAlarm(saved.id, NoAlarmReason.REQUEST_FAILED)
        }
        return if (passed) {
            SavedReminder.NoAlarm(saved.id, NoAlarmReason.TIME_PASSED)
        } else {
            SavedReminder.AlarmRequested(saved.id, exact = alarms.exact)
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

/**
 * Что получилось из [NewDeed].
 *
 * Дело есть всегда — иначе было бы исключение. Остальное различает [reminder]:
 * - `null` — напоминания не просили;
 * - [SavedReminder.NoAlarm] — напоминание записано, но звонка не будет;
 * - [SavedReminder.AlarmRequested] — записано, и о звонке попросили.
 */
data class CreatedDeed(
    val deedId: Long,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime?,
    val title: String,
    val reminder: SavedReminder?,
) {
    val reminderId: Long? get() = reminder?.id
}

/**
 * Завести дело дня вместе с напоминанием.
 *
 * Дело и напоминание пишутся одной транзакцией: упала вторая запись — нет и
 * первой, и в дне не остаётся дела, о котором человек просил напомнить, а
 * напоминания нет. О звонке просят после, когда обе записи уже в базе: неудача
 * записанного не отменяет (см. [SavedReminder]).
 *
 * Ошибка базы — исключение, как и раньше: тот, кто звал, сам решает, что
 * сказать человеку.
 *
 * Одинаковых дел операция не ищет: правила «это то же дело» для двух дел дня в
 * Askya нет (`sameDeed` сравнивает строку списка дел с делом дня), и два
 * одинаковых дела, заведённых порознь, встанут оба — так же, как из карточки.
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
            date = deed.date,
            start = deed.start,
            end = deed.end,
            title = deed.title,
            reminder = saved?.let(reminders::arm),
        )
    }
}
