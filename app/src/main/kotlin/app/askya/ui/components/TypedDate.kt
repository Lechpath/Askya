package app.askya.ui.components

import java.time.LocalDate

/**
 * Куда тянуть год, когда его не назвали.
 *
 * Одна и та же «31.08», напечатанная третьего сентября, значит разное в разных
 * местах приложения. В карточке дела и напоминания она про будущее: то, что
 * назначают, назначают вперёд, и прошлому напоминание не нужно. В записи
 * расходной книги — ровно наоборот: книгу ведут о случившемся, и «31.08» в
 * сентябре означает позавчера, а не будущий август.
 *
 * Без этого различия запись уезжала на год вперёд молча: в ленте месяца её
 * не видно (месяцы вперёд не листаются), а в остатке счёта и в статистике она
 * есть — и счёт переставал сходиться с настоящей картой без всякой видимой
 * причины.
 */
enum class DateLean {
    /** Вперёд: ближайшая такая дата, которая ещё не прошла. */
    AHEAD,

    /** Назад: ближайшая такая дата, которая уже была. */
    BEHIND,
}

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
 *
 * [lean] решает только один вопрос — какой год подставить, когда его не
 * назвали (см. [DateLean]). Слова «вчера» и «завтра» ему не подчиняются: они
 * названы прямо, и додумывать за ними нечего.
 */
fun parseTypedDate(
    raw: String,
    today: LocalDate = LocalDate.now(),
    lean: DateLean = DateLean.AHEAD,
): LocalDate? {
    val text = raw.trim().lowercase().replace('ё', 'е')
        .substringAfterLast(',')
        .trim()
    if (text.isEmpty()) return null

    when (text) {
        "сегодня" -> return today
        "завтра" -> return today.plusDays(1)
        "послезавтра" -> return today.plusDays(2)
        "вчера" -> return today.minusDays(1)
        "позавчера" -> return today.minusDays(2)
    }

    NUMERIC.matchEntire(text)?.let { match ->
        val day = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val year = match.groupValues[3].toIntOrNull()
        return dateOf(day, month, year, today, lean)
    }

    NAMED.matchEntire(text)?.let { match ->
        val day = match.groupValues[1].toInt()
        val name = match.groupValues[2]
        // Достаточно начала названия: «20 авг» человек пишет чаще, чем «20 августа».
        val month = MONTHS.indexOfFirst { it.startsWith(name) || name.startsWith(it) }
        if (month < 0) return null
        return dateOf(day, month + 1, match.groupValues[3].toIntOrNull(), today, lean)
    }

    // Одно число — ближайшее такое число: в ту сторону, в какую смотрит место,
    // где его печатают. «Двадцатого» в напоминании — про будущее, «двадцатого»
    // в трате — про уже случившееся.
    text.toIntOrNull()?.let { day ->
        if (day !in 1..31) return null
        val step = if (lean == DateLean.AHEAD) 1L else -1L
        return generateSequence(today.withDayOfMonth(1)) { it.plusMonths(step) }
            .take(13)
            .mapNotNull { month -> dayIn(month, day) }
            .firstOrNull { fits(it, today, lean) }
    }

    return null
}

/**
 * Дата строкой: «Сегодня», «Завтра» или «Пятница, 8 августа».
 *
 * Год приписывается, только если он не нынешний: «8 августа» в 2026-м — это
 * этот август, и писать его год значило бы дописывать очевидное к каждой
 * строке. Зато дата другого года без года — обман: именно так запись,
 * уехавшая в будущий август, выглядела ровно как позавчерашняя.
 */
fun formatTypedDate(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    else -> formatRussianDate(date) + if (date.year == today.year) "" else " ${date.year}"
}

private val NUMERIC = Regex("""^(\d{1,2})\s*[./]\s*(\d{1,2})(?:\s*[./]\s*(\d{2,4}))?$""")

private val NAMED = Regex("""^(\d{1,2})\s+([а-я]{3,})\.?(?:\s+(\d{4}))?$""")

/**
 * Год не назвали — берётся тот, в котором дата стоит с нужной стороны от
 * сегодня: «20 августа» в сентябре — это будущий август у дела и прошлый у
 * записи о трате (см. [DateLean]).
 *
 * Не подошедший год не выбрасывается совсем: тридцатого февраля не бывает ни в
 * каком году, и отвечать на «29.02» соседним годом честнее нечем — ответом
 * остаётся то, что вышло из нынешнего года, то есть ничего.
 */
private fun dateOf(day: Int, month: Int, year: Int?, today: LocalDate, lean: DateLean): LocalDate? {
    if (month !in 1..12 || day !in 1..31) return null
    if (year != null) {
        val full = if (year < 100) 2000 + year else year
        return runCatching { LocalDate.of(full, month, day) }.getOrNull()
    }
    val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
    if (thisYear != null && fits(thisYear, today, lean)) return thisYear
    val next = if (lean == DateLean.AHEAD) today.year + 1 else today.year - 1
    return runCatching { LocalDate.of(next, month, day) }.getOrNull() ?: thisYear
}

/** Стоит ли дата с той стороны от сегодня, в какую смотрит [lean]. Сегодня годится всегда. */
private fun fits(date: LocalDate, today: LocalDate, lean: DateLean): Boolean =
    if (lean == DateLean.AHEAD) !date.isBefore(today) else !date.isAfter(today)

/** Такое число в этом месяце, если оно в нём есть: тридцатого февраля не бывает. */
private fun dayIn(month: LocalDate, day: Int): LocalDate? =
    if (day <= month.lengthOfMonth()) month.withDayOfMonth(day) else null
