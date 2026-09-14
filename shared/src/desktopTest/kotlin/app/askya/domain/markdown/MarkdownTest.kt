package app.askya.domain.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор разметки проверяется здесь: на телефоне заметку с таблицей и кодом
 * набирать ради проверки нереально, а ошибка разбора съедает текст человека.
 */
class MarkdownTest {

    @Test
    fun `заголовки по уровням`() {
        val blocks = Markdown.parse("# Раз\n## Два\n### Три")
        assertEquals(
            listOf(1 to "Раз", 2 to "Два", 3 to "Три"),
            blocks.filterIsInstance<MdBlock.Heading>().map { it.level to it.text },
        )
    }

    @Test
    fun `перенос строки в абзаце остаётся переносом`() {
        // CommonMark склеил бы строки в одну. Здесь читают прежде всего свои
        // заметки, а в заметке перенос ставят руками и он значит перенос:
        // столбик дат, склеенный в строку, — уже не то, что записали.
        val blocks = Markdown.parse("11 июля\n19 июля\n\nдругой абзац")
        assertEquals(
            listOf("11 июля\n19 июля", "другой абзац"),
            blocks.filterIsInstance<MdBlock.Paragraph>().map { it.text },
        )
    }

    @Test
    fun `список с вложенным уровнем`() {
        val blocks = Markdown.parse("- первый\n- второй\n  - уточнение")
        val items = blocks.filterIsInstance<MdBlock.Bullets>().single().items
        assertEquals(listOf("первый", "второй", "уточнение"), items.map { it.text })
        assertEquals(listOf(false, false, true), items.map { it.nested })
    }

    @Test
    fun `нумерованный список считается своим блоком`() {
        val blocks = Markdown.parse("1. открыть\n2. править\n3. сохранить")
        assertEquals(3, blocks.filterIsInstance<MdBlock.Numbers>().single().items.size)
    }

    @Test
    fun `пустая строка между пунктами не начинает нумерацию заново`() {
        // Так список и набирают: каждый пункт с новой строки через отбивку, и
        // маркер на всех «1.» — номер ставится по месту в списке. Пока пустая
        // строка рвала его на блоки, счёт начинался заново и весь список
        // показывался единицами.
        val blocks = Markdown.parse("1. открыть\n\n1. править\n\n1. сохранить")
        val items = blocks.filterIsInstance<MdBlock.Numbers>().single().items
        assertEquals(listOf("открыть", "править", "сохранить"), items.map { it.text })
    }

    @Test
    fun `абзац между пунктами список заканчивает`() {
        val blocks = Markdown.parse("1. открыть\n\nа потом подумать\n\n1. править")
        assertEquals(2, blocks.filterIsInstance<MdBlock.Numbers>().size)
    }

    @Test
    fun `чек-лист отличается от обычного списка`() {
        // Порядок правил важен: «- [x] …» подходит и под маркированный список,
        // и без проверки чек-лист превратился бы в буллиты со скобками.
        val blocks = Markdown.parse("- [x] черновик\n- [ ] вычитка")
        val tasks = blocks.filterIsInstance<MdBlock.Tasks>().single().items
        assertEquals(listOf("черновик" to true, "вычитка" to false), tasks.map { it.text to it.done })
    }

    @Test
    fun `блок кода берётся целиком, вместе с пустыми строками`() {
        val blocks = Markdown.parse("```python\ndef f():\n\n    return 1\n```")
        val code = blocks.filterIsInstance<MdBlock.Code>().single()
        assertEquals("python", code.lang)
        assertEquals("def f():\n\n    return 1", code.text)
    }

    @Test
    fun `решётка внутри кода не становится заголовком`() {
        val blocks = Markdown.parse("```\n# это комментарий\n```")
        assertTrue(blocks.filterIsInstance<MdBlock.Heading>().isEmpty())
    }

    @Test
    fun `незакрытая ограда не съедает текст`() {
        val blocks = Markdown.parse("```\nодин\nдва")
        assertEquals("один\nдва", blocks.filterIsInstance<MdBlock.Code>().single().text)
    }

    @Test
    fun `таблица с выравниванием столбцов`() {
        val table = Markdown.parse(
            "| Элемент | Когда | Частота |\n" +
                "|:--------|:------|--------:|\n" +
                "| Заголовок | структура | 100% |\n" +
                "| Сноска | источник | 5% |",
        ).filterIsInstance<MdBlock.Table>().single()

        assertEquals(listOf("Элемент", "Когда", "Частота"), table.head)
        assertEquals(listOf(MdAlign.START, MdAlign.START, MdAlign.END), table.aligns)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Сноска", "источник", "5%"), table.rows[1])
    }

    @Test
    fun `палки без разделителя остаются абзацем`() {
        // Без этого строка вроде «а | б» ломалась бы в таблицу из одной строки.
        val blocks = Markdown.parse("| это просто текст с палками |")
        assertTrue(blocks.filterIsInstance<MdBlock.Table>().isEmpty())
        assertEquals(1, blocks.filterIsInstance<MdBlock.Paragraph>().size)
    }

    @Test
    fun `цитата собирается в один блок`() {
        val quote = Markdown.parse("> первая\n> вторая\n>\n> — автор")
            .filterIsInstance<MdBlock.Quote>().single()
        assertEquals("первая\nвторая\n\n— автор", quote.text)
    }

    @Test
    fun `картинка и сноска разбираются отдельно`() {
        val blocks = Markdown.parse("![Схема сборки](./assets/build.png)\n\n[^1]: John Gruber.")
        val picture = blocks.filterIsInstance<MdBlock.Picture>().single()
        assertEquals("Схема сборки" to "./assets/build.png", picture.alt to picture.src)
        assertEquals("1", blocks.filterIsInstance<MdBlock.Footnote>().single().mark)
    }

    @Test
    fun `черта`() {
        assertTrue(Markdown.parse("---").single() is MdBlock.Rule)
        assertTrue(Markdown.parse("***").single() is MdBlock.Rule)
    }

    @Test
    fun `простой текст остаётся текстом`() {
        // Заметка без единого знака разметки должна открыться ровно так же,
        // как её написали, — это самый частый случай.
        val blocks = Markdown.parse("Купить хлеб.\n\nПозвонить в среду.")
        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is MdBlock.Paragraph })
    }
}
