package app.askya.domain.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Разбор документов проверяется обычным тестом на машине — потому он и написан
 * своими руками, без `XmlPullParser`: системный разборщик живёт в Android, и
 * ошибку в чтении epub пришлось бы искать на телефоне.
 */
class MarkupTest {

    @Test
    fun `текст между тегами приходит буквами`() {
        assertEquals(listOf("Привет", "мир"), textsOf("<p>Привет</p><p>мир</p>"))
    }

    @Test
    fun `сущности разбираются по именам и числами`() {
        assertEquals(
            listOf("«тише» — & < > ✓"),
            textsOf("<p>&laquo;тише&raquo; &mdash; &amp; &lt; &gt; &#x2713;</p>"),
        )
    }

    @Test
    fun `неизвестная сущность остаётся как была`() {
        assertEquals(listOf("&nope; конец"), textsOf("<p>&nope; конец</p>"))
    }

    @Test
    fun `свойства читаются в любых кавычках и без них`() {
        val found = HashMap<String, String>()
        scanMarkup(
            "<item id='a' href=\"b.xhtml\" width=100 hidden/>",
            object : MarkupSink {
                override fun open(name: String, attributes: Map<String, String>) {
                    found += attributes
                }
            },
        )
        assertEquals("a", found["id"])
        assertEquals("b.xhtml", found["href"])
        assertEquals("100", found["width"])
        assertEquals("", found["hidden"])
    }

    @Test
    fun `одиночный тег закрывается сам`() {
        val closed = ArrayList<String>()
        scanMarkup(
            "<br/>",
            object : MarkupSink {
                override fun close(name: String) {
                    closed += name
                }
            },
        )
        assertEquals(listOf("br"), closed)
    }

    @Test
    fun `комментарии, пролог и CDATA не мешают`() {
        val text = textsOf(
            "<?xml version=\"1.0\"?><!DOCTYPE html><!-- прочь --><p><![CDATA[как <есть>]]></p>",
        )
        assertEquals(listOf("как <есть>"), text)
    }

    @Test
    fun `незакрытый тег не роняет разбор`() {
        assertEquals(listOf("хвост"), textsOf("<p>хвост"))
    }

    private fun textsOf(source: String): List<String> {
        val parts = ArrayList<String>()
        scanMarkup(
            source,
            object : MarkupSink {
                override fun text(value: String) {
                    if (value.isNotBlank()) parts += value.trim()
                }
            },
        )
        return parts
    }
}

class HtmlTest {

    @Test
    fun `страница разбирается на заголовки и абзацы`() {
        val blocks = htmlBlocks(
            """
            <html><head><title>прочь</title><style>p{color:red}</style></head>
            <body><h2>Глава первая</h2><p>Первый абзац.</p><div>Второй абзац.</div></body></html>
            """.trimIndent(),
        )

        assertEquals(BookBlock.Heading(2, "Глава первая"), blocks[0])
        assertEquals(BookBlock.Paragraph("Первый абзац."), blocks[1])
        assertEquals(BookBlock.Paragraph("Второй абзац."), blocks[2])
    }

    @Test
    fun `оформление и пустые абзацы выбрасываются`() {
        val blocks = htmlBlocks("<p>  </p><p><em>Курсив</em> и <b>жирный</b></p><p></p>")
        assertEquals(listOf(BookBlock.Paragraph("Курсив и жирный")), blocks)
    }

    @Test
    fun `список выходит строками с точкой`() {
        val blocks = htmlBlocks("<ul><li>раз</li><li>два</li></ul>")
        assertEquals(listOf(BookBlock.Paragraph("• раз"), BookBlock.Paragraph("• два")), blocks)
    }
}

class EpubTest {

    @Test
    fun `книга собирается по описи, а имена глав берутся из оглавления`() {
        val book = assertNotNull(parseEpub(epubParts()))

        assertEquals("Тихий дом", book.title)
        assertEquals("А. Автор", book.author)
        assertEquals(listOf("Начало", "Конец"), book.chapters.map { it.title })
        assertEquals(
            BookBlock.Paragraph("Дом стоял тихо."),
            book.chapters.first().blocks.last(),
        )
    }

    @Test
    fun `пустые страницы главами не становятся`() {
        val parts = epubParts().toMutableMap()
        parts["OEBPS/cover.xhtml"] = "<html><body><img src=\"c.jpg\"/></body></html>".toByteArray()
        parts["OEBPS/content.opf"] = OPF.replace(
            "<itemref idref=\"one\"/>",
            "<itemref idref=\"cover\"/><itemref idref=\"one\"/>",
        ).replace(
            "<item id=\"one\"",
            "<item id=\"cover\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"one\"",
        ).toByteArray()

        val book = assertNotNull(parseEpub(parts))
        assertEquals(2, book.chapters.size)
    }

    @Test
    fun `без описи книги нет`() {
        assertNull(parseEpub(mapOf("META-INF/container.xml" to "<container/>".toByteArray())))
    }

    private fun epubParts(): Map<String, ByteArray> = mapOf(
        "META-INF/container.xml" to (
            "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\"/>" +
                "</rootfiles></container>"
            ).toByteArray(),
        "OEBPS/content.opf" to OPF.toByteArray(),
        "OEBPS/toc.ncx" to NCX.toByteArray(),
        "OEBPS/one.xhtml" to (
            "<html><body><h1>Первая</h1><p>Дом стоял тихо.</p></body></html>"
            ).toByteArray(),
        "OEBPS/two.xhtml" to "<html><body><p>И замолчал.</p></body></html>".toByteArray(),
    )

    private companion object {
        val OPF = """
            <package><metadata><dc:title>Тихий дом</dc:title><dc:creator>А. Автор</dc:creator></metadata>
            <manifest>
              <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
              <item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
              <item id="two" href="two.xhtml" media-type="application/xhtml+xml"/>
            </manifest>
            <spine toc="ncx"><itemref idref="one"/><itemref idref="two"/></spine></package>
        """.trimIndent()

        val NCX = """
            <ncx><navMap>
              <navPoint><navLabel><text>Начало</text></navLabel><content src="one.xhtml"/></navPoint>
              <navPoint><navLabel><text>Конец</text></navLabel><content src="two.xhtml#top"/></navPoint>
            </navMap></ncx>
        """.trimIndent()
    }
}

class Fb2Test {

    @Test
    fun `разделы становятся главами, а описание — именем и автором`() {
        val book = assertNotNull(
            parseFb2(
                """
                <FictionBook><description><title-info>
                  <book-title>Дорога</book-title>
                  <author><first-name>Иван</first-name><last-name>Петров</last-name></author>
                </title-info></description>
                <body>
                  <section><title><p>Первая</p></title><p>Шли долго.</p><empty-line/><p>Пришли.</p></section>
                  <section><title><p>Вторая</p></title><p>Дальше некуда.</p></section>
                </body></FictionBook>
                """.trimIndent(),
            ),
        )

        assertEquals("Дорога", book.title)
        assertEquals("Иван Петров", book.author)
        assertEquals(listOf("Первая", "Вторая"), book.chapters.map { it.title })
        assertTrue(book.chapters.first().blocks.contains(BookBlock.Divider))
    }

    @Test
    fun `картинки в текст не попадают`() {
        val book = assertNotNull(
            parseFb2(
                "<FictionBook><body><section><p>Текст.</p></section></body>" +
                    "<binary id=\"cover\">iVBORw0KGgoAAAANSUhEUg==</binary></FictionBook>",
            ),
        )
        assertEquals(listOf(BookBlock.Paragraph("Текст.")), book.chapters.single().blocks)
    }
}

class DocxTest {

    @Test
    fun `заголовки, списки и абзацы выходят разметкой`() {
        val text = assertNotNull(
            parseDocx(
                mapOf(
                    "word/document.xml" to (
                        "<w:document><w:body>" +
                            "<w:p><w:pPr><w:pStyle w:val=\"Heading2\"/></w:pPr><w:r><w:t>Отчёт</w:t></w:r></w:p>" +
                            "<w:p><w:r><w:t>Первая </w:t></w:r><w:r><w:t>строка.</w:t></w:r></w:p>" +
                            "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/></w:numPr></w:pPr>" +
                            "<w:r><w:t>пункт</w:t></w:r></w:p>" +
                            "</w:body></w:document>"
                        ).toByteArray(),
                ),
            ),
        )

        assertEquals("## Отчёт\n\nПервая строка.\n\n- пункт", text)
    }

    @Test
    fun `таблица получает черту под шапкой`() {
        val text = assertNotNull(
            parseDocx(
                mapOf(
                    "word/document.xml" to (
                        "<w:document><w:body><w:tbl>" +
                            "<w:tr><w:tc><w:p><w:r><w:t>Имя</w:t></w:r></w:p></w:tc>" +
                            "<w:tc><w:p><w:r><w:t>Число</w:t></w:r></w:p></w:tc></w:tr>" +
                            "<w:tr><w:tc><w:p><w:r><w:t>Хлеб</w:t></w:r></w:p></w:tc>" +
                            "<w:tc><w:p><w:r><w:t>2</w:t></w:r></w:p></w:tc></w:tr>" +
                            "</w:tbl></w:body></w:document>"
                        ).toByteArray(),
                ),
            ),
        )

        assertTrue(text.contains("| Имя | Число |"), text)
        assertTrue(text.contains("| --- | --- |"), text)
        assertTrue(text.contains("| Хлеб | 2 |"), text)
    }

    @Test
    fun `удалённое при правке в текст не идёт`() {
        val text = parseDocx(
            mapOf(
                "word/document.xml" to (
                    "<w:document><w:body><w:p>" +
                        "<w:r><w:t>Осталось</w:t></w:r>" +
                        "<w:del><w:r><w:delText> и стёрто</w:delText></w:r></w:del>" +
                        "</w:p></w:body></w:document>"
                    ).toByteArray(),
            ),
        )
        assertEquals("Осталось", text)
    }
}

class XlsxTest {

    @Test
    fun `лист выходит таблицей, а пропущенные ячейки остаются пустыми`() {
        val text = assertNotNull(
            parseXlsx(
                mapOf(
                    "xl/sharedStrings.xml" to (
                        "<sst><si><t>Товар</t></si><si><t>Цена</t></si><si><t>Хлеб</t></si></sst>"
                        ).toByteArray(),
                    "xl/workbook.xml" to (
                        "<workbook><sheets><sheet name=\"Склад\" r:id=\"rId1\"/></sheets></workbook>"
                        ).toByteArray(),
                    "xl/_rels/workbook.xml.rels" to (
                        "<Relationships><Relationship Id=\"rId1\" Target=\"worksheets/sheet1.xml\"/>" +
                            "</Relationships>"
                        ).toByteArray(),
                    "xl/worksheets/sheet1.xml" to (
                        "<worksheet><sheetData>" +
                            "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                            "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"C2\"><v>40.0</v></c></row>" +
                            "</sheetData></worksheet>"
                        ).toByteArray(),
                ),
            ),
        )

        assertTrue(text.contains("## Склад"), text)
        assertTrue(text.contains("| Товар | Цена |"), text)
        assertTrue(text.contains("| Хлеб |  | 40 |"), text)
    }
}

class FormatTest {

    @Test
    fun `формат узнаётся по имени, даже когда система молчит`() {
        assertEquals(DocFormat.BOOK, documentFormat("Толстой.epub", ""))
        assertEquals(DocFormat.BOOK, documentFormat("сказка.fb2.zip", "application/zip"))
        assertEquals(DocFormat.WORD, documentFormat("Договор.DOCX", "application/octet-stream"))
        assertEquals(DocFormat.EXCEL, documentFormat("смета.xlsx", ""))
        assertEquals(DocFormat.TEXT, documentFormat("список.txt", ""))
        assertEquals(DocFormat.PDF, documentFormat("билет.pdf", ""))
    }

    @Test
    fun `без расширения решает тип из системы`() {
        assertEquals(DocFormat.IMAGE, documentFormat("снимок", "image/jpeg"))
        assertEquals(DocFormat.WORD, documentFormat("документ", WORD_MIME))
        assertEquals(DocFormat.OTHER, documentFormat("нечто", "application/octet-stream"))
    }

    private companion object {
        const val WORD_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }
}

/**
 * Проверка целиком: настоящий zip — как книга приходит с телефона — читается
 * до глав. Разбор архива и разбор описи легко расходятся порознь, а ломается
 * от этого именно связка.
 */
class ArchiveTest {

    @Test
    fun `книга читается из настоящего архива`() {
        val zipped = zip(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to
                "<container><rootfiles><rootfile full-path=\"OEBPS/content.opf\"/>" +
                "</rootfiles></container>",
            "OEBPS/content.opf" to (
                "<package><metadata><dc:title>Проба пера</dc:title></metadata>" +
                    "<manifest><item id=\"c1\" href=\"ch1.xhtml\" " +
                    "media-type=\"application/xhtml+xml\"/></manifest>" +
                    "<spine><itemref idref=\"c1\"/></spine></package>"
                ),
            "OEBPS/ch1.xhtml" to
                "<html><body><h1>Утро</h1><p>Дом стоял на краю деревни.</p></body></html>",
            // Картинка в текст попасть не должна и в память тоже.
            "OEBPS/cover.jpg" to "не картинка, но и не разметка",
        )

        val parts = readArchive(zipped.inputStream()) { name ->
            name.endsWith(".xml") || name.endsWith(".opf") || name.endsWith(".xhtml")
        }
        assertFalse(parts.containsKey("OEBPS/cover.jpg"))

        val book = assertNotNull(parseEpub(parts))
        assertEquals("Проба пера", book.title)
        assertEquals("Утро", book.chapters.single().title)
        assertEquals(
            BookBlock.Paragraph("Дом стоял на краю деревни."),
            book.chapters.single().blocks.last(),
        )
    }

    private fun zip(vararg parts: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            parts.forEach { (name, body) ->
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
