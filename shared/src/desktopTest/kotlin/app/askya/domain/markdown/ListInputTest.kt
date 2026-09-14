package app.askya.domain.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Разбор написанного в строки списка. Проверяется здесь, а не на телефоне: в
 * список чаще всего вставляют чужой текст — из письма, из сообщения, — и на
 * экране такую вставку не наберёшь, а ошибка разбора теряет строки человека.
 */
class ListInputTest {

    @Test
    fun `строки без разметки — просто строки`() {
        assertEquals(
            listOf("молоко", "хлеб", "батарейки"),
            ListInput.parse("молоко\nхлеб\nбатарейки").map { it.text },
        )
    }

    @Test
    fun `маркер снимается, а не остаётся буквами`() {
        val lines = ListInput.parse("- хлеб\n* сыр\n+ соль\n1. позвонить\n2) написать")
        assertEquals(listOf("хлеб", "сыр", "соль", "позвонить", "написать"), lines.map { it.text })
    }

    @Test
    fun `отступ делает строку подпунктом`() {
        val lines = ListInput.parse("- поездка\n  - билеты\nсборы\n    вещи")
        assertEquals(listOf(false, true, false, true), lines.map { it.nested })
    }

    @Test
    fun `отмеченное приходит отмеченным`() {
        // «- [x] …» подходит и под маркированный список: правило чек-листа
        // должно сработать первым, иначе в списке появится строка «[x] хлеб».
        val lines = ListInput.parse("- [x] хлеб\n- [ ] сыр")
        assertEquals(listOf("хлеб" to true, "сыр" to false), lines.map { it.text to it.done })
    }

    @Test
    fun `галочка в конце строки становится отметкой`() {
        // Так отмечали, пока строку нельзя было отметить: знак в конце. Он
        // должен стать отметкой, а не остаться буквой в тексте.
        val lines = ListInput.parse("Того (про собаку) ✓\nАэронафты")
        assertEquals(listOf("Того (про собаку)" to true, "Аэронафты" to false), lines.map { it.text to it.done })
    }

    @Test
    fun `пустые строки не превращаются в пустые записи`() {
        assertEquals(2, ListInput.parse("\n\nхлеб\n\n   \n- \nсыр\n").size)
    }

    @Test
    fun `перевод строки любой — вставленное из другого места не слипается`() {
        assertEquals(listOf("хлеб", "сыр"), ListInput.parse("хлеб\r\nсыр").map { it.text })
    }
}
