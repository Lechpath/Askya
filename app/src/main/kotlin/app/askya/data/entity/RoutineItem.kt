package app.askya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.DeedDays
import app.askya.domain.model.Priority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Дело обычного дня — то, из чего день состоит по умолчанию. Askya разворачивает
 * эти записи в расписание конкретной даты, а дальше день живёт своей жизнью:
 * правки в дне на распорядок не влияют и наоборот.
 *
 * [priority] в расписании не показывается: он остался от разбора рассказа о
 * себе, где решал, куда поставить дело без названного времени. Сейчас время
 * названо у каждого дела, и важность ни на что не влияет.
 *
 * [enabled] позволяет временно выключить дело, не теряя его формулировку.
 *
 * [icon] пуст почти всегда: знак угадывается по названию, а колонка хранит
 * только то, что человек выбрал руками вопреки догадке.
 *
 * [link] — чем это дело делается (см. [app.askya.domain.model.DeedLink]).
 * Привязка стоит и здесь, а не только у дела в дне, потому что повторяющееся
 * дело делается одним и тем же: сказав это один раз в списке, человек не
 * повторяет выбор в каждом дне.
 *
 * [days] — по каким дням недели дело повторяется (см. [DeedDays]). Пусто —
 * каждый день, и это же значение у всех дел, заведённых до появления колонки.
 */
@Entity(tableName = "routine_items")
data class RoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val priority: Priority = Priority.NORMAL,
    val enabled: Boolean = true,
    val icon: BlockIcon? = null,
    val link: String? = null,
    val days: String? = null,
) {

    /** Дни недели дела разобранными. Пустой набор — каждый день. */
    val repeatDays: Set<DayOfWeek> get() = DeedDays.of(days)

    /**
     * Случается ли дело в эту дату.
     *
     * Спрашивает сборка дня и всё, что показывает список рядом с датой:
     * повторение — свойство самого дела, и разбирать колонку в каждом месте
     * заново значило бы получить два разных ответа об одном деле.
     */
    fun on(date: LocalDate): Boolean = DeedDays.on(repeatDays, date)
}
