package app.askya.ui.components

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Русские названия заданы списком, а не через Locale: язык интерфейса всегда
 * русский, а локаль устройства может быть любой — с ней дата вышла бы английской.
 */
internal val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

/**
 * Те же месяцы именительным падежом.
 *
 * Второй список, а не правка первого: «8 августа» и «Август 2026» — оба нужны,
 * и выводить одно из другого правилами русского словоизменения ради двенадцати
 * слов было бы дороже, чем написать их дважды.
 */
private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

/** Например: «Август 2026» — так месяц надписан в шапке над списком. */
fun formatMonthTitle(month: YearMonth): String =
    "${MONTH_NAMES[month.monthValue - 1]} ${month.year}"

/**
 * Например: «до августа 2027» — срок, к которому что-то кончится.
 *
 * Родительный падеж берётся из того же списка, что и «8 августа»: «до Август
 * 2027» — не сокращение, а ошибка, и читается она как опечатка.
 */
fun formatMonthUntil(month: YearMonth): String =
    "до ${MONTHS[month.monthValue - 1]} ${month.year}"

/**
 * Месяц в три буквы: «авг». Для подписей под столбиками, где полное имя не
 * помещается, а год надписан отдельно над всем рядом.
 */
private val MONTH_SHORT = listOf(
    "янв", "фев", "мар", "апр", "май", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)

fun formatMonthShort(month: YearMonth): String = MONTH_SHORT[month.monthValue - 1]

/**
 * Дни недели именительным падежом. Не `private`: их читает обратно и разбор
 * напечатанной даты ([parseTypedDate]) — «суббота» там значит ближайшую.
 */
internal val WEEKDAYS = listOf(
    "понедельник", "вторник", "среда", "четверг",
    "пятница", "суббота", "воскресенье",
)

/** Например: «Пятница, 8 августа». */
fun formatRussianDate(date: LocalDate): String {
    val weekday = WEEKDAYS[date.dayOfWeek.value - 1]
    return "${weekday.replaceFirstChar { it.uppercase() }}, ${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm")

fun formatTime(time: LocalTime): String = time.format(TIME)

/** Время дела строкой: с концом, если он задан. */
fun formatRange(start: LocalTime, end: LocalTime?): String =
    end?.let { "${formatTime(start)} – ${formatTime(it)}" } ?: formatTime(start)
