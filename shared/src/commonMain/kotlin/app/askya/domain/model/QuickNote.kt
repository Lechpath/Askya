package app.askya.domain.model

/**
 * Имя быстрой заметки: написанное, а если строку имени не трогали — первая
 * строчка текста, не длиннее шестидесяти знаков.
 *
 * Правило карточки быстрой заметки из меню, вынесенное как есть, чтобы
 * заметка, записанная иначе (например, агентом), называлась так же. Пустое
 * имя при пустом тексте — пустое: придумывать название за человека не из чего.
 */
fun quickNoteTitle(title: String, body: String): String = title.trim().ifBlank {
    body.trim().lineSequence().firstOrNull()?.take(QUICK_NOTE_TITLE_FROM_BODY).orEmpty()
}

/** Сколько знаков первой строки становится именем. */
const val QUICK_NOTE_TITLE_FROM_BODY = 60
