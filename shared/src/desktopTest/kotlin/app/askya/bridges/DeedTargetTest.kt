package app.askya.bridges

import app.askya.data.entity.Bridge
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Три слоя привязки — правило, которое ошибается молча: человек нажимает на
 * дело и попадает не туда, а почему — не видно ниоткуда.
 */
class DeedTargetTest {

    private val reader = Bridge(id = 1, name = "Читалка", target = "x", icon = BlockIcon.READ)
    private val call = Bridge(id = 2, name = "Созвон", target = "y", icon = BlockIcon.CALL)
    private val loose = Bridge(id = 3, name = "Заметки по работе", target = "z")
    private val all = listOf(reader, call, loose)

    @Test
    fun `мост на знаке ведёт из всех дел с этим знаком`() {
        val target = deedTarget(all, link = null, routineLink = null, title = "Чтение Библии", icon = null)
        assertEquals(DeedTarget.Outside(reader), target)
    }

    @Test
    fun `знак угадывается по названию, а не берётся из колонки`() {
        // «Читать Достоевского» и «Чтение Библии» — одно и то же READ.
        val target = deedTarget(all, link = null, routineLink = null, title = "Читать Достоевского", icon = null)
        assertEquals(DeedTarget.Outside(reader), target)
    }

    @Test
    fun `выбранный руками знак главнее угаданного`() {
        val target = deedTarget(all, link = null, routineLink = null, title = "Чтение Библии", icon = BlockIcon.CALL)
        assertEquals(DeedTarget.Outside(call), target)
    }

    @Test
    fun `строка списка перебивает знак`() {
        val target = deedTarget(
            all,
            link = null,
            routineLink = "bridge:3",
            title = "Чтение Библии",
            icon = null,
        )
        assertEquals(DeedTarget.Outside(loose), target)
    }

    @Test
    fun `привязка самого дела перебивает оба`() {
        val target = deedTarget(
            all,
            link = "book:12",
            routineLink = "bridge:3",
            title = "Чтение Библии",
            icon = null,
        )
        assertEquals(DeedTarget.Inside(DeedLink(LinkKind.BOOK, 12)), target)
    }

    @Test
    fun `привязка на убранный мост пропускается, а не обрывает поиск`() {
        // Мост убрали, а строка осталась: следующий слой лучше, чем ничего.
        val target = deedTarget(
            all,
            link = "bridge:99",
            routineLink = null,
            title = "Чтение Библии",
            icon = null,
        )
        assertEquals(DeedTarget.Outside(reader), target)
    }

    @Test
    fun `дело без привязки и без знакового моста никуда не ведёт`() {
        assertNull(deedTarget(all, link = null, routineLink = null, title = "Завтрак", icon = null))
    }

    @Test
    fun `мост без знака сам собой ни к чему не подключается`() {
        // «Заметки по работе» не привязаны к знаку, и найти их можно только
        // назвав прямо: иначе один мост без знака перехватывал бы все дела.
        assertNull(deedTarget(listOf(loose), link = null, routineLink = null, title = "Работа", icon = null))
    }
}
