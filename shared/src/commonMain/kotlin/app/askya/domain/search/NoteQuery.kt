package app.askya.domain.search

import app.askya.data.entity.Note

/**
 * Правило поиска записей Scroll — одно на экран и на агента.
 *
 * Жило приватным внутри `ScrollScreen`; вынесено сюда без изменений, чтобы
 * поиск агента не разъехался с тем, что человек видит на экране: запись,
 * которую находит строка Scroll, находит и агент, и наоборот.
 *
 * Сравнение — в Kotlin через [lowercase], а не в SQL: `LIKE` в SQLite
 * игнорирует регистр только у латиницы, и «дача» не нашла бы «Дача».
 * `NoteDao.observeFiltered` устроен именно так, поэтому ни экран, ни агент
 * им не пользуются.
 *
 * Слова ищутся как есть, подстрокой: ни `%` и `_` из SQL, ни `.` и `*` из
 * регулярных выражений здесь ничего не значат. Особый знак один — `#` в
 * начале слова, он делает слово тегом.
 */
class Ask internal constructor(val words: List<String>, val tags: List<String>) {
    /** Спрашивать нечего: пусто, одни пробелы или голая `#`. */
    val empty: Boolean get() = words.isEmpty() && tags.isEmpty()
}

/**
 * Что спросили — вопрос человека, разобранный на слова и теги.
 *
 * Слово с решёткой — тег: так их пишут везде, и объяснять это отдельной
 * кнопкой «искать по тегу» не нужно. Остальные слова ищутся по всему, что у
 * записи есть буквами, теги в том числе: набравший «дача» без решётки имел в
 * виду и запись про дачу, и запись, помеченную «дача».
 *
 * Слова требуются все: два слова в строке — это уточнение, а не «или».
 */
fun askOf(query: String): Ask {
    val parts = query.trim().split(WHITESPACE).filter { it.isNotBlank() }
    return Ask(
        words = parts.filterNot { it.startsWith("#") }.map { it.lowercase() },
        tags = parts.filter { it.startsWith("#") && it.length > 1 }
            .map { it.drop(1).lowercase() },
    )
}

private val WHITESPACE = Regex("\\s+")

/**
 * Запись подходит, если в ней нашлось каждое слово и каждый тег.
 *
 * На пустой вопрос отвечает «да» — пустое «все» всегда верно. Экран с пустым
 * вопросом не ищет вовсе, а [findTextNotes] на нём ничего не находит.
 */
fun Note.matches(ask: Ask): Boolean {
    val hay = buildString {
        append(title.lowercase())
        append('\n')
        append(body.lowercase())
        append('\n')
        append(tags.joinToString(" ").lowercase())
    }
    if (!ask.words.all { hay.contains(it) }) return false
    return ask.tags.all { needle -> tags.any { it.lowercase().contains(needle) } }
}

/**
 * Текстовая ли это заметка — написанная здесь же, а не приложенный файл,
 * голос или картинка. У тех есть [Note.uri]; см. описание [Note].
 */
val Note.isTextNote: Boolean get() = uri == null && !isImage && !voice

/**
 * Текстовые заметки, подходящие под [ask], — в том порядке, в каком пришли
 * (из `NoteRepository.notes()` — сначала недавно правленные).
 *
 * Файлы, голос и картинки не входят: их содержимое лежит не в записи, и
 * совпадение по одному имени файла — не то, что ищут в тексте. Пустой вопрос
 * не находит ничего: «найди ничего» — не просьба отдать все заметки.
 */
fun findTextNotes(notes: List<Note>, ask: Ask): List<Note> =
    if (ask.empty) emptyList() else notes.filter { it.isTextNote && it.matches(ask) }

/** Сколько знаков занимает фрагмент — вместе с многоточиями. */
const val SNIPPET_SIZE = 300

/**
 * Кусок текста вокруг первого совпадения — не длиннее [size] знаков вместе с
 * многоточиями.
 *
 * Переводы строк, табуляция и прочие управляющие знаки становятся пробелом:
 * фрагмент — строка для чтения, а не вёрстка, и управляющий знак в JSON
 * занимает до шести знаков вместо одного.
 *
 * Совпадение — самое раннее место в тексте, где стоит любое из слов. Нет
 * слов (спросили только тег) или совпало не в тексте, а в названии и тегах —
 * фрагмент берётся с начала. Обрезанный край отмечен «…»: молча отрезанное
 * читалось бы как весь текст. По возможности режется по пробелу: обрубок
 * посреди слова читается как опечатка.
 */
fun snippetOf(text: String, ask: Ask, size: Int = SNIPPET_SIZE): String {
    require(size >= MIN_SNIPPET) { "фрагмент не короче $MIN_SNIPPET знаков" }
    val flat = text.replace(BLANKS, " ").trim()
    if (flat.length <= size) return flat

    val hit = ask.words
        .map { word -> flat.indexOf(word, ignoreCase = true).let { at -> at to word.length } }
        .filter { (at, _) -> at >= 0 }
        .minByOrNull { (at, _) -> at }
    if (hit == null) return flat.take(size - 1).trimEnd() + ELLIPSIS

    val (at, length) = hit
    // Место под два многоточия; если край окажется у начала или конца, одно
    // из них не понадобится — окно остаётся тем же, с запасом в знак.
    val window = size - 2
    var start = (at - (window - length) / 2).coerceIn(0, flat.length - window)
    var end = start + window

    // Сдвиг к пробелу только сужает окно и не заходит за совпадение.
    if (start > 0) {
        val space = flat.indexOf(' ', start).takeIf { it in start until minOf(at, start + SNAP) }
        if (space != null) start = space + 1
    }
    if (end < flat.length) {
        val space = flat.lastIndexOf(' ', end).takeIf { it >= maxOf(at + length, end - SNAP) }
        if (space != null) end = space
    }

    val head = if (start > 0) ELLIPSIS else ""
    val tail = if (end < flat.length) ELLIPSIS else ""
    return head + flat.substring(start, end).trim() + tail
}

private const val ELLIPSIS = "…"

/** На сколько знаков край можно подвинуть к пробелу. */
private const val SNAP = 20

private const val MIN_SNIPPET = 40

/** Пробельные и управляющие знаки подряд — один пробел. */
private val BLANKS = Regex("[\\s\\p{Cntrl}]+")
