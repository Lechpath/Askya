package app.askya.ui.components

import app.askya.domain.model.RemindAt

/**
 * Разбирает напечатанное напоминание: «19:00», «за 15 мин», «за час»,
 * «за 1:30», «полчаса».
 *
 * Одной строкой, а не двумя полями с переключателем «время/промежуток»:
 * человек и говорит это одной фразой — «в семь» или «за десять минут». Что
 * именно он сказал, видно по самой записи.
 *
 * Приставка «за» (или минус) означает промежуток, и тогда голое число — это
 * минуты. Без неё голое число разбирается тем же [parseTypedTime], что и время
 * дела: «19» — это семь вечера, а не «за девятнадцать минут».
 */
fun parseTypedRemind(raw: String): RemindAt? {
    val text = raw.trim().lowercase().replace('ё', 'е')
    if (text.isEmpty()) return null

    val prefix = LEAD_PREFIX.find(text)
    if (prefix != null) {
        val body = text.removeRange(prefix.range).trim()
        return parseLead(body)?.let(RemindAt::Before)
    }

    // «15 мин» без «за» — тоже промежуток: единица измерения сказана словом,
    // и часом это уже не прочесть.
    parseUnits(text)?.let { return RemindAt.Before(it) }

    return parseTypedTime(text)?.let(RemindAt::Exact)
}

/** Напоминание строкой: «19:00» или «за 1 ч 30 мин». */
fun formatRemind(remind: RemindAt): String = when (remind) {
    is RemindAt.Exact -> formatTime(remind.time)
    is RemindAt.Before -> "за ${formatMinutes(remind.minutes)}"
}

private val LEAD_PREFIX = Regex("""^(за\s+|-\s*)""")

private val CLOCK_LEAD = Regex("""^(\d{1,2})\s*[:.]\s*(\d{1,2})$""")

// Хвост единицы перечислен буквами кириллицы, а не `\w`: в Java этот знак
// значит латиницу с цифрами, и «часа» разобралось бы как «час» с непонятным
// «а» на конце.
private val UNIT = Regex("""(\d{1,3})?\s*(мин[а-я]*|час[а-я]*|ч|м)""")

/**
 * Промежуток после «за»: «15», «15 мин», «час», «1:30».
 *
 * Голое число здесь — минуты: «за 15» про четверть часа, а не про три часа
 * дня, и время после «за» вообще не имеет смысла.
 */
private fun parseLead(text: String): Int? {
    if (text.isEmpty()) return null
    parseUnits(text)?.let { return it }
    CLOCK_LEAD.matchEntire(text)?.let { match ->
        val hours = match.groupValues[1].toInt()
        val minutes = match.groupValues[2].toInt()
        return (hours * 60 + minutes).takeIf { it > 0 && minutes < 60 }
    }
    return text.toIntOrNull()?.takeIf { it > 0 }
}

/**
 * Промежуток, названный словами: «15 мин», «2 часа», «1 ч 30 мин», «полчаса».
 * Всё, что не разобралось в число с единицей, отвергается целиком — иначе
 * опечатка молча превратилась бы в другое время.
 */
private fun parseUnits(text: String): Int? {
    if (text == "полчаса") return 30

    var total = 0
    var found = false
    UNIT.findAll(text).forEach { match ->
        val count = match.groupValues[1].toIntOrNull() ?: 1
        total += if (match.groupValues[2].startsWith("ч")) count * 60 else count
        found = true
    }
    if (!found) return null
    if (text.replace(UNIT, "").isNotBlank()) return null

    return total.takeIf { it > 0 }
}

private fun formatMinutes(total: Int): String {
    val hours = total / 60
    val minutes = total % 60
    return listOfNotNull(
        hours.takeIf { it > 0 }?.let { "$it ч" },
        minutes.takeIf { it > 0 }?.let { "$it мин" },
    ).joinToString(" ").ifEmpty { "0 мин" }
}
