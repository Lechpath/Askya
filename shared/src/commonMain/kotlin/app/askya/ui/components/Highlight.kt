package app.askya.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import app.askya.ui.theme.CoralInk
import app.askya.ui.theme.CoralSoft

/**
 * Что подсвечивать в набранном тексте — то, что сейчас ищут.
 *
 * Через `CompositionLocal`, а не параметром: разметка рисуется десятком
 * вложенных друг в друга видов (абзац, список, ячейка таблицы, сноска), и
 * протаскивать строку поиска через каждый значило бы дописать её в десять
 * подписей ради того, что нужно одному месту — самой строке текста.
 *
 * Пустая строка — обычное чтение, никакой подсветки.
 */
val LocalHighlight = compositionLocalOf { "" }

/**
 * Найденное, отмеченное маркером.
 *
 * Мягкая коралловая плашка и тёмно-коралловая буква — те же цвета, которыми в
 * Askya отмечено «это то самое»: метка формата на карточке файла, код в
 * разметке. Полужирным вдобавок к цвету: подсветку ищут глазами, скользя по
 * строке, и одного оттенка для этого мало.
 */
fun AnnotatedString.highlighted(query: String): AnnotatedString {
    val ranges = matches(text, query)
    if (ranges.isEmpty()) return this

    return buildAnnotatedString {
        // Приписывается поверх уже разобранной разметки, а не вместо неё:
        // жирное внутри найденного должно остаться жирным.
        append(this@highlighted)
        ranges.forEach { (start, end) -> addStyle(MARKER, start, end) }
    }
}

/** То же для простой строки — названия файла, имени книги. */
fun highlighted(text: String, query: String): AnnotatedString = buildAnnotatedString {
    append(text)
    matches(text, query).forEach { (start, end) -> addStyle(MARKER, start, end) }
}

/**
 * Кусок текста вокруг первого совпадения — то, ради чего запись попала в
 * список найденного.
 *
 * Без него результат поиска по тексту выглядит как список названий, из
 * которых ни одно не содержит искомого слова, — и непонятно, почему они
 * здесь. Отрезается по словам, а не по буквам: обрубок посреди слова читается
 * как опечатка.
 */
fun snippet(text: String, query: String, radius: Int = 80): String {
    val at = text.indexOf(query.trim(), ignoreCase = true)
    if (at < 0) return text.take(radius * 2).trim()

    val from = (at - radius).coerceAtLeast(0)
    val to = (at + query.trim().length + radius).coerceAtMost(text.length)
    val cut = text.substring(from, to).replace('\n', ' ').trim()

    val head = if (from > 0) "…" else ""
    val tail = if (to < text.length) "…" else ""
    return head + cut + tail
}

/** Все места, где встречается искомое. Регистр не важен: ищут не по буквам. */
private fun matches(text: String, query: String): List<Pair<Int, Int>> {
    val needle = query.trim()
    if (needle.isEmpty() || text.isEmpty()) return emptyList()

    val found = mutableListOf<Pair<Int, Int>>()
    var at = text.indexOf(needle, ignoreCase = true)
    while (at >= 0) {
        found += at to (at + needle.length)
        at = text.indexOf(needle, startIndex = at + needle.length, ignoreCase = true)
    }
    return found
}

// Сами краски, а не роли темы: маркер собирается один раз на файл, вне
// разметки, и прочитать выбранную гамму отсюда нечем. На плашке подсветки это
// заметно меньше всего — она и должна отличаться от всего вокруг.
private val MARKER = SpanStyle(
    background = CoralSoft,
    color = CoralInk,
    fontWeight = FontWeight.SemiBold,
)
