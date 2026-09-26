package app.askya.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Имя быстрой заметки — правило карточки из меню, вынесенное как было.
 * Проверяются его края: пустое, пробелы, первая строка и её длина.
 */
class QuickNoteTitleTest {

    @Test
    fun `написанное имя остаётся`() {
        assertEquals("Покупки", quickNoteTitle("  Покупки ", "хлеб"))
    }

    @Test
    fun `пустое имя — первая строка текста`() {
        assertEquals("Первая строка", quickNoteTitle("", "Первая строка\nВторая строка"))
    }

    @Test
    fun `имя из пробелов — тоже первая строка`() {
        assertEquals("Первая строка", quickNoteTitle("   ", "Первая строка\nВторая строка"))
    }

    @Test
    fun `многострочный текст с пустыми строками в начале`() {
        // Текст срезается по краям целиком, а не построчно: пустые строки сверху
        // уходят, и именем становится первая настоящая.
        assertEquals("Первая строка", quickNoteTitle("", "\n\n  Первая строка\nВторая\nТретья"))
    }

    @Test
    fun `пустой текст — пустое имя`() {
        assertEquals("", quickNoteTitle("", ""))
    }

    @Test
    fun `текст из пробелов — пустое имя`() {
        assertEquals("", quickNoteTitle(" ", "  \n \t "))
    }

    @Test
    fun `длинная первая строка обрезается до шестидесяти знаков`() {
        val line = "а".repeat(100)
        assertEquals("а".repeat(QUICK_NOTE_TITLE_FROM_BODY), quickNoteTitle("", line + "\nвторая"))
    }
}
