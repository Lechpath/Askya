package app.askya.domain.search

import app.askya.data.entity.Note
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Общее правило поиска записей — то, чем ищут экран Scroll и агент. */
class NoteQueryTest {

    private fun text(title: String = "", body: String = "", tags: List<String> = emptyList(), id: Long = 0) =
        Note(id = id, title = title, body = body, tags = tags)

    private fun Note.found(query: String) = matches(askOf(query))

    // --- Совпадение -----------------------------------------------------------

    @Test
    fun `обычное совпадение и его отсутствие`() {
        val note = text(title = "Покупки", body = "молоко и хлеб")
        assertTrue(note.found("хлеб"))
        assertTrue(note.found("покупки"), "название тоже ищется")
        assertFalse(note.found("сыр"))
    }

    @Test
    fun `регистр не важен — и у латиницы, и у кириллицы`() {
        val note = text(title = "Дача", body = "ПОЛИТЬ Огурцы, купить Seeds")
        listOf("дача", "ДАЧА", "дАчА", "полить", "огурцы", "seeds", "SEEDS").forEach {
            assertTrue(note.found(it), it)
        }
    }

    @Test
    fun `ё и е — разные буквы, как и на экране`() {
        // Правило перенесено без изменений: сворачивать «ё» в «е» оно не умеет.
        assertFalse(text(body = "ёлка").found("елка"))
        assertTrue(text(body = "Ёлка").found("ёлка"))
    }

    @Test
    fun `слова требуются все, в любом порядке`() {
        val note = text(body = "Позвонить маме в субботу")
        assertTrue(note.found("субботу маме"))
        assertFalse(note.found("маме воскресенье"))
    }

    @Test
    fun `тег — слово с решёткой, ищется только в тегах`() {
        val tagged = text(title = "План", body = "без слова", tags = listOf("Дача"))
        val mentioned = text(title = "План", body = "еду на дачу — дача")
        assertTrue(tagged.found("#дача"))
        assertFalse(mentioned.found("#дача"), "слово в тексте — не тег")
        assertTrue(tagged.found("дача"), "слово без решётки ищется и в тегах")
    }

    @Test
    fun `пустой вопрос`() {
        listOf("", "   ", "\n\t", "#").forEach { query -> assertTrue(askOf(query).empty, "«$query»") }
        // Пустое «все» верно — поэтому поиск на пустом вопросе ничего не отдаёт.
        assertTrue(text(body = "что угодно").matches(askOf("")))
        assertEquals(emptyList(), findTextNotes(listOf(text(body = "что угодно")), askOf("  ")))
    }

    @Test
    fun `особые знаки — просто буквы`() {
        val note = text(body = "скидка 50% на c++ и a.b")
        assertTrue(note.found("50%"))
        assertTrue(note.found("c++"))
        assertTrue(note.found("a.b"))
        assertFalse(text(body = "скидка 50 на").found("50%"), "% — не шаблон LIKE")
        assertFalse(text(body = "axb").found("a.b"), ". — не регулярное выражение")
        assertFalse(text(body = "скидка").found("_кидка"), "_ — не шаблон LIKE")
    }

    // --- Что входит в поиск ---------------------------------------------------

    @Test
    fun `ищутся только текстовые заметки, в пришедшем порядке`() {
        val first = text(id = 1, body = "дача весной")
        val file = Note(id = 2, title = "дача.pdf", uri = "content://doc/2", mime = "application/pdf")
        val voice = Note(id = 3, title = "дача", uri = "voice/3.m4a", mime = "audio/mp4")
        val image = Note(id = 4, title = "дача", uri = "images/4.jpg", mime = "image/jpeg", isImage = true)
        val second = text(id = 5, title = "Дача осенью")
        val other = text(id = 6, body = "город")

        val found = findTextNotes(listOf(first, file, voice, image, second, other), askOf("дача"))

        assertEquals(listOf(1L, 5L), found.map { it.id })
    }

    // --- Фрагмент -------------------------------------------------------------

    private val filler = "слово ".repeat(200) // 1200 знаков

    @Test
    fun `короткий текст — целиком, в одну строку`() {
        assertEquals("первая строка вторая строка", snippetOf("первая строка\n\nвторая\tстрока\u0007", askOf("вторая")))
        assertEquals("a b", snippetOf("a\r\n\u0000b", askOf("b")))
    }

    @Test
    fun `фрагмент вокруг совпадения в середине`() {
        val body = filler + "Дача у реки" + " " + filler
        val snippet = snippetOf(body, askOf("дача"))
        assertTrue(snippet.length <= SNIPPET_SIZE, "${snippet.length}")
        assertTrue("Дача у реки" in snippet)
        assertTrue(snippet.startsWith("…") && snippet.endsWith("…"))
        // Окно почти во всю длину, а не огрызок.
        assertTrue(snippet.length >= SNIPPET_SIZE - 25, "${snippet.length}")
    }

    @Test
    fun `совпадение в начале текста — без многоточия спереди`() {
        val snippet = snippetOf("Дача у реки " + filler, askOf("дача"))
        assertTrue(snippet.startsWith("Дача у реки"), snippet)
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.length <= SNIPPET_SIZE)
    }

    @Test
    fun `совпадение в конце текста — без многоточия сзади`() {
        val snippet = snippetOf(filler + "у реки дача", askOf("дача"))
        assertTrue(snippet.endsWith("у реки дача"), snippet)
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.length <= SNIPPET_SIZE)
    }

    @Test
    fun `несколько совпадений — вокруг самого раннего из слов`() {
        val body = filler + "ПЕРВОЕ река " + filler + " второе дача " + filler + " дача"
        val snippet = snippetOf(body, askOf("дача река"))
        assertTrue("ПЕРВОЕ река" in snippet, snippet)
        assertFalse("дача" in snippet)
    }

    @Test
    fun `совпало не в тексте — фрагмент с начала`() {
        val body = "Начало текста " + filler
        val byTitle = snippetOf(body, askOf("заголовок"))
        val byTag = snippetOf(body, askOf("#дача"))
        listOf(byTitle, byTag).forEach { snippet ->
            assertTrue(snippet.startsWith("Начало текста"), snippet)
            assertTrue(snippet.endsWith("…"))
            assertTrue(snippet.length <= SNIPPET_SIZE)
        }
    }

    @Test
    fun `фрагмент не длиннее предела при любом тексте`() {
        val texts = listOf(
            "ы".repeat(5000) + "дача" + "ы".repeat(5000), // без единого пробела
            "\n".repeat(1000) + "дача" + "\t".repeat(1000),
            ("\"\\" + " ").repeat(1000) + "дача",
            "дача" + "x".repeat(SNIPPET_SIZE),
            "x".repeat(SNIPPET_SIZE - 1) + "дача",
        )
        texts.forEach { body ->
            val snippet = snippetOf(body, askOf("дача"))
            assertTrue(snippet.length <= SNIPPET_SIZE, "${snippet.length}")
            assertTrue("дача" in snippet, snippet.take(40))
            assertTrue(snippet.none { it < ' ' }, "управляющих знаков нет")
        }
    }
}
