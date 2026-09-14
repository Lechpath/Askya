package app.askya.domain.docs

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Перенос из чужого блокнота.
 *
 * Проверяется разбор — та половина, где нет ни одного обращения к системе.
 * Ошибка в ней тем и опасна, что на экране выглядит не поломкой, а честным
 * «столько там и было»: половина заметок молча не переносится, и заметить это
 * можно только через месяц, когда одна из них понадобится.
 *
 * Разбора выгрузки Keep здесь нет: он идёт через `org.json`, которого на
 * обычной машине нет — там он заглушка. Проверять заглушку смысла нет.
 */
class NoteImportTest {

    @Test
    fun `текстовый файл становится заметкой с именем файла`() {
        val note = NoteImport.read("Список на завтра.txt", "хлеб\nмолоко".toByteArray())

        assertEquals(1, note.size)
        assertEquals("Список на завтра", note.first().title)
        assertEquals("хлеб\nмолоко", note.first().body)
    }

    @Test
    fun `markdown с заголовком берёт имя у заголовка и не повторяет его в тексте`() {
        val note = NoteImport.read("2024-05-01.md", "# Разговор с врачом\n\nспросить про сон".toByteArray())

        assertEquals("Разговор с врачом", note.first().title)
        assertEquals("спросить про сон", note.first().body)
    }

    @Test
    fun `пустой файл заметкой не становится`() {
        assertTrue(NoteImport.read("пусто.txt", "   \n\n ".toByteArray()).isEmpty())
    }

    @Test
    fun `перевод строки в файле из Windows не оставляет лишних знаков`() {
        val note = NoteImport.read("заметка.txt", "первая\r\nвторая".toByteArray())
        assertEquals("первая\nвторая", note.first().body)
    }

    @Test
    fun `выгрузка Evernote разбирается на все записи разом`() {
        val enex = """
            <?xml version="1.0" encoding="UTF-8"?>
            <en-export>
              <note>
                <title>Первая</title>
                <content><![CDATA[<en-note><div>раз</div><div>два</div></en-note>]]></content>
              </note>
              <note>
                <title>Вторая &amp; последняя</title>
                <content><![CDATA[<en-note><div>текст</div></en-note>]]></content>
              </note>
            </en-export>
        """.trimIndent()

        val notes = NoteImport.fromEnex(enex)

        assertEquals(2, notes.size)
        assertEquals("Первая", notes[0].title)
        assertEquals("раз\nдва", notes[0].body)
        assertEquals("Вторая & последняя", notes[1].title)
    }

    @Test
    fun `обрубленная выгрузка Evernote отдаёт то, что в ней цело`() {
        // Записи, у которой нет закрывающего тега, нет — но первые две есть, и
        // терять их из-за третьей нельзя.
        val enex = "<en-export>" +
            "<note><title>Раз</title><content>один</content></note>" +
            "<note><title>Два</title><content>два</content></note>" +
            "<note><title>Три</title><content>обрыв"

        val notes = NoteImport.fromEnex(enex)

        assertEquals(listOf("Раз", "Два"), notes.map { it.title })
    }

    @Test
    fun `страница отдаёт текст без разметки и имя из заголовка`() {
        val html = """
            <html><head><title>План</title>
            <style>body { color: red }</style></head>
            <body><p>первый абзац</p><p>второй&nbsp;абзац</p>
            <script>alert(1)</script></body></html>
        """.trimIndent()

        val note = NoteImport.fromHtml("archive.html", html)

        assertNotNull(note)
        assertEquals("План", note.title)
        assertTrue(note.body.contains("первый абзац"))
        assertTrue(note.body.contains("второй абзац"))
        assertTrue(!note.body.contains("alert"), "скрипт попал в текст")
        assertTrue(!note.body.contains("color: red"), "оформление попало в текст")
    }

    @Test
    fun `абзацы не слипаются в одну строку`() {
        val text = NoteImport.stripTags("<p>раз</p><p>два</p><br>три")
        assertEquals(listOf("раз", "два", "три"), text.lines().filter { it.isNotBlank() })
    }

    @Test
    fun `мнемоники разворачиваются, а написанный нарочно амперсанд не съедается`() {
        assertEquals("<", NoteImport.unescape("&lt;"))
        assertEquals("«да»", NoteImport.unescape("&laquo;да&raquo;"))
        assertEquals("&lt;", NoteImport.unescape("&amp;lt;"))
        assertEquals("—", NoteImport.unescape("&#8212;"))
        assertEquals("—", NoteImport.unescape("&#x2014;"))
    }

    @Test
    fun `архив разбирается насквозь, а служебное в нём пропускается`() {
        val zip = zipOf(
            "Takeout/Keep/первая.txt" to "текст первой".toByteArray(),
            "Takeout/Keep/вторая.md" to "# Вторая\nтекст второй".toByteArray(),
            "Takeout/Keep/" to ByteArray(0),
            "Takeout/.DS_Store" to "мусор".toByteArray(),
        )

        val notes = NoteImport.read("takeout.zip", zip)

        assertEquals(2, notes.size)
        assertEquals(setOf("первая", "Вторая"), notes.map { it.title }.toSet())
    }

    @Test
    fun `архив в архиве в архиве не уводит разбор вглубь без конца`() {
        val inner = zipOf("глубоко.txt" to "текст".toByteArray())
        val middle = zipOf("inner.zip" to inner)
        // Третий уровень уже за пределом: важно, что разбор возвращается, а не
        // ходит вглубь, пока хватает памяти.
        val notes = NoteImport.read("outer.zip", middle)
        assertTrue(notes.size <= 1)
    }

    @Test
    fun `заметка без имени называется первой строчкой текста`() {
        val note = NoteImport.fromPlain(".txt", "первая строчка\nвторая")
        assertNotNull(note)
        assertEquals("первая строчка", note.title)
    }

    @Test
    fun `имя из ста слов обрезается, а не растягивает полку`() {
        val long = "а".repeat(300)
        val note = NoteImport.fromPlain(".txt", long)
        assertNotNull(note)
        assertTrue(note.title.length <= 60, "имя длиной ${note.title.length}")
    }

    @Test
    fun `в выгрузке Evernote пустая запись не становится безымянной заметкой`() {
        val notes = NoteImport.fromEnex("<note><title></title><content></content></note>")
        assertTrue(notes.isEmpty())
    }

    @Test
    fun `страница без текста заметкой не становится`() {
        assertNull(NoteImport.fromHtml("пусто.html", "<html><body></body></html>"))
    }

    /** Архив в памяти: имя — содержимое. */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(body)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
