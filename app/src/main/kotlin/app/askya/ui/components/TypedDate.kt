package app.askya.ui.components

import java.time.LocalDate

/**
 * Разбирает напечатанную дату: «сегодня», «завтра», «20 августа», «20.08»,
 * «20.08.2026», «20».
 *
 * Календаря нет намеренно: дата в карточке набирается там же и так же, как
 * время и название, — строкой. Слова «сегодня» и «завтра» первые в списке,
 * потому что чаще всего имеют в виду именно их.
 *
 * Название дня недели впереди пропускается: ровно так дату показывает
 * [formatRussianDate], и написанное приложением должно читаться им обратно.
 */
fun parseTypedDate(raw: String, today: LocalDate = LocalDate.now()): LocalDate? {
    val text = raw.trim().lowercase().replace('ё', 'е')
        .substringAfterLast(',')
        .trim()
    if (text.isEmpty()) return null

    when (text) {
        "сегодня" -> return today
        "завтра" -> return today.plusDays(1)
        "послезавтра" -> return today.plusDays(2)
        "вчера" -> return today.minusDays(1)
    }

    NUMERIC.matchEntire(text)?.let { match ->
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val year = match.groupValues[3].toIntOrNull()
        return dateOf(day, month, year, today)
    }

    NAMED.matchEntire(text)?.let { match ->
        val day = match.groupValues[1].toInt()
        val name = match.groupValues[2]
        // Достаточно начала названия: «20 авг» человек пишет чаще, чем «20 августа».
        val month = MONTHS.indexOfFirst { it.startsWith(name) || name.startsWith(it) }
        if (month < 0) return null
        return dateOf(day, month + 1, match.groupValues[3].toIntOrNull(), today)
    }

    // Одно число — ближайшее такое число: в этом месяце, а если оно прошло, в
    // следующем. «Двадцатого» про будущее, прошлому напоминание не нужно.
    text.toIntOrNull()?.let { day ->
        if (day !in 1..31) return null
        return generateSequence(today.withDayOfMonth(1)) { it.plusMonths(1) }
            .take(13)
            .mapNotNull { month -> dayIn(month, day) }
            .firstOrNull { !it.isBefore(today) }
    }

    return null
}

/** Дата строкой: «Сегодня», «Завтра» или «Пятница, 8 августа». */
fun formatTypedDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    else -> formatRussianDate(date)
}

private val NUMERIC = Regex("""^(\d{1,2})\s*[./]\s*(\d{1,2})(?:\s*[./]\s*(\d{2,4}))?$""")

private val NAMED = Regex("""^(\d{1,2})\s+([а-я]{3,})\.?(?:\s+(\d{4}))?$""")

/**
 * Год не назвали — берётся тот, в котором дата ещё не прошла: «20 августа»
 * в сентябре означает следующий август, а не позапрошлое воскресенье.
 */
private fun dateOf(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
    if (month !in 1..12 || day !in 1..31) return null
    if (year != null) {
        val full = if (year < 100) 2000 + year else year
        return runCatching { LocalDate.of(full, month, day) }.getOrNull()
    }
    val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
    if (thisYear != null && !thisYear.isBefore(today)) return thisYear
    return runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull() ?: thisYear
}

/** Такое число в этом месяце, если оно в нём есть: тридцатого февраля не бывает. */
private fun dayIn(month: LocalDate, day: Int): LocalDate? =
    if (day <= month.lengthOfMonth()) month.withDayOfMonth(day) else null
