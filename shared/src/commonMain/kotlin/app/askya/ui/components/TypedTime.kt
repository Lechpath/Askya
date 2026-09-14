package app.askya.ui.components

import java.time.LocalTime

/** Начало дела и конец, если его написали. */
data class TypedRange(val start: LocalTime, val end: LocalTime?)

/**
 * Разбирает напечатанный промежуток: «20:45-22:45», «9 – 10.30», «7:30».
 *
 * Конец необязателен: дела, у которых он не важен, писать длиннее не нужно.
 *
 * Половинки разбираются тем же разбором, что и одиночное время, и диапазон
 * признаётся только когда разобрались обе. Иначе «7-30» перестало бы означать
 * половину восьмого: тире у нас исторически ещё и разделитель часов и минут,
 * и отнимать привычную запись ради новой было бы обменом не в пользу человека.
 */
fun parseTypedRange(raw: String): TypedRange? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    for (dash in RANGE_SEPARATOR.findAll(text)) {
        val start = parseTypedTime(text.substring(0, dash.range.first)) ?: continue
        val end = parseTypedTime(text.substring(dash.range.last + 1)) ?: continue
        return TypedRange(start, end)
    }

    return parseTypedTime(text)?.let { TypedRange(it, null) }
}

private val RANGE_SEPARATOR = Regex("""\s*[-–—]\s*""")

/**
 * Разбирает напечатанное время: «7», «730», «7:30», «19.00».
 *
 * Пишут по-разному, а выбора из готового по ТЗ нет — значит принимать надо
 * все привычные формы, а не одну каноническую.
 */
fun parseTypedTime(raw: String): LocalTime? {
    val text = raw.trim()
    if (text.isEmpty()) return null

    val separator = Regex("""^(\d{1,2})\s*[:.\-\s]\s*(\d{1,2})$""").matchEntire(text)
    val (hour, minute) = when {
        separator != null ->
            separator.groupValues[1].toInt() to separator.groupValues[2].toInt()

        text.all { it.isDigit() } -> when (text.length) {
            1, 2 -> text.toInt() to 0
            3 -> text.substring(0, 1).toInt() to text.substring(1).toInt()
            4 -> text.substring(0, 2).toInt() to text.substring(2).toInt()
            else -> return null
        }

        else -> return null
    }

    return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
}
