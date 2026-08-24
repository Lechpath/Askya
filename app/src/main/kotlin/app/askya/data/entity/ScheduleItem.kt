package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.askya.domain.model.BlockIcon
import java.time.LocalDate
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
 */
@Entity(tableName = "schedule_items", indices = [Index("date")])
data class ScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val title: String,
    val note: String = "",
    val done: Boolean = false,
    val icon: BlockIcon? = null,
)
