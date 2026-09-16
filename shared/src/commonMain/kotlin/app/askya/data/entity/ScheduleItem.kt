package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.askya.data.sync.Uid
import app.askya.domain.model.BlockIcon
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Дело дня: когда, что, заметка и сделано ли.
 *
 * Ни типа, ни многосоставного статуса: в расписании они не помогали читать
 * список, а заполнять их приходилось при каждом деле. Осталось [done] —
 * единственное, что человек про дело действительно отмечает.
 *
 * [endTime] необязательно и в диалоге не спрашивается: у большинства дел
 * понятного конца нет, а у заведённых раньше он сохраняется как был.
 *
 * [icon] пуст почти всегда: знак угадывается по названию, а колонка хранит
 * только то, что человек выбрал руками вопреки догадке.
 *
 * [link] — чем это дело делается: книга, список, заметка, раздел
 * (см. [app.askya.domain.model.DeedLink]). Пусто почти всегда, и это нормально:
 * привязка нужна там, где дело и правда чем-то делается, а «Завтрак» ничем не
 * делается. Одной строкой «вид:адрес», а не колонкой на каждый вид: видов
 * будет прибавляться.
 */
@Entity(tableName = "schedule_items", indices = [Index("date"), Index("uid", unique = true)])
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Имя строки, общее для всех устройств, — см. [app.askya.data.sync.Uid].
     * Проставляется само и не меняется никогда: по нему строку узнают при
     * слиянии с другим устройством.
     */
    val uid: String = Uid.new(),
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    val icon: BlockIcon? = null,
    val link: String? = null,
    /**
     * Когда дело убрали. `null` — на месте.
     *
     * Мягкое удаление, а не строка из базы вон: подтверждение «вы уверены?» не
     * отменяет ошибку, оно перекладывает её на человека, который торопится, — и
     * через месяц жмётся не читая. Убранное живёт сутки, снизу на несколько
     * секунд появляется «Вернуть», и подтверждений не нужно вовсе. Старое
     * подчищается при запуске приложения, без фоновой службы.
     */
    val removedAt: LocalDateTime? = null,
)
