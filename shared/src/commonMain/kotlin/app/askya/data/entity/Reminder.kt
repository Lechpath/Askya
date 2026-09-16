package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.askya.data.sync.Uid
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.RemindAt
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Разовое напоминание о событии. Выключенное не ставится будильником.
 *
 * Дат и времён здесь два, и это разные вещи. [eventDate] с [eventStart] — когда
 * состоится само дело; [date] с [time] — когда прозвучит напоминание. Второе
 * считается из первого и [lead] и хранится посчитанным: будильник ставится по
 * моменту, и пересчитывать его в каждом месте, где напоминание читают, значило
 * бы повторять одну и ту же арифметику.
 *
 * [lead] — «за сколько минут до начала». Пусто значит, что час напоминания
 * назвали прямо. Хранится вместе с посчитанным моментом, потому что человек
 * сказал именно промежуток: показать ему надо то, что он сказал, а при переносе
 * дела промежуток пересчитать.
 *
 * [silent] — способ: тихое приходит без звука и вибрации. Это выбор про это
 * дело, а не про уведомления вообще, поэтому лежит в самом напоминании.
 *
 * [sound] — чем звучать: ссылка на мелодию или песню с телефона. Пусто значит
 * «обычный звук напоминания». [soundTitle] — её название: его человек и выбрал,
 * его же надо показать в карточке. Хранится рядом со ссылкой, потому что иначе
 * за именем пришлось бы ходить в MediaStore при каждой отрисовке карточки — и
 * не находить его вовсе, если файл потом убрали с телефона.
 *
 * [itemId] связывает напоминание с делом дня — по нему колокольчик в карточке
 * знает, что напоминание уже стоит. Внешнего ключа нет: дело могут удалить, и
 * терять из-за этого запись о напоминании незачем.
 */
@Entity(tableName = "reminders", indices = [Index("date"), Index("uid", unique = true)])
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Имя строки, общее для всех устройств, — см. [app.askya.data.sync.Uid].
     * Проставляется само и не меняется никогда: по нему строку узнают при
     * слиянии с другим устройством.
     */
    val uid: String = Uid.new(),
    val title: String,
    val date: LocalDate,
    val time: LocalTime,
    val enabled: Boolean = true,
    val silent: Boolean = false,
    val sound: String? = null,
    val soundTitle: String? = null,
    val itemId: Long? = null,
    val eventDate: LocalDate = date,
    val eventStart: LocalTime? = null,
    val eventEnd: LocalTime? = null,
    val lead: Int? = null,
    val icon: BlockIcon? = null,
)

/** Как о нём напомнить — тем же способом, каким это сказал человек. */
val Reminder.remindAt: RemindAt
    get() = lead?.let { RemindAt.Before(it) } ?: RemindAt.Exact(time)

/**
 * Собирает напоминание, считая момент будильника из события и способа.
 *
 * Одна на два экрана: напоминание заводят и в своём разделе, и карточкой дела
 * в дне, а считаться момент должен одинаково.
 */
fun reminderOf(
    title: String,
    eventDate: LocalDate,
    eventStart: LocalTime?,
    remind: RemindAt,
    id: Long = 0,
    eventEnd: LocalTime? = null,
    icon: BlockIcon? = null,
    enabled: Boolean = true,
    silent: Boolean = false,
    sound: String? = null,
    soundTitle: String? = null,
    itemId: Long? = null,
    /**
     * Имя строки для облака. У правки — имя прежнего напоминания: строка
     * пересобирается целиком, но дело за ней то же самое, и новое имя
     * оставило бы на другом устройстве призрак старого.
     */
    uid: String = Uid.new(),
): Reminder {
    val moment = remindMoment(eventDate, eventStart, remind)
    return Reminder(
        id = id,
        uid = uid,
        title = title,
        date = moment.toLocalDate(),
        time = moment.toLocalTime(),
        enabled = enabled,
        silent = silent,
        sound = sound,
        soundTitle = soundTitle,
        itemId = itemId,
        eventDate = eventDate,
        eventStart = eventStart,
        eventEnd = eventEnd,
        lead = (remind as? RemindAt.Before)?.minutes,
        icon = icon,
    )
}

/**
 * Момент, когда прозвучит напоминание.
 *
 * У дела в начале суток «за полчаса» уводит напоминание во вчерашний вечер —
 * так и нужно: иначе оно прозвучало бы после самого дела.
 */
fun remindMoment(eventDate: LocalDate, eventStart: LocalTime?, remind: RemindAt): LocalDateTime =
    when (remind) {
        is RemindAt.Exact -> eventDate.atTime(remind.time)
        is RemindAt.Before -> eventDate.atTime(eventStart ?: LocalTime.MIDNIGHT)
            .minusMinutes(remind.minutes.toLong())
    }
