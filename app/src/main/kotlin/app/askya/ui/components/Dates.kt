package app.askya.ui.components

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Русские названия заданы списком, а не через Locale: язык интерфейса всегда
 * русский, а локаль устройства может быть любой — с ней дата вышла бы английской.
 */
internal val MONTHS = listOf(
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
)

private val WEEKDAYS = listOf(
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
