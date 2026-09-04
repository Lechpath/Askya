package app.askya.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Сравнение версий.
 *
 * Проверяется оно, а не запрос к сети: ошибка здесь означает либо вечное
 * «обновлений нет», либо предложение поставить то, что уже стоит, — и обе
 * замечают через полгода, а не сразу.
 */
class UpdatesTest {

    @Test
    fun `следующая версия новее`() {
        assertTrue(newer("1.5", "1.4"))
        assertTrue(newer("2.0", "1.9"))
    }

    @Test
    fun `буква v в начале ничего не значит`() {
        assertTrue(newer("v1.5", "1.4"))
        assertFalse(newer("v1.4", "1.4"))
    }

    @Test
    fun `десятая версия новее четвёртой`() {
        // Строкой «1.10» меньше «1.4» — на этом обновления и переставали
        // находиться после десятого выпуска.
        assertTrue(newer("1.10", "1.4"))
        assertFalse(newer("1.4", "1.10"))
    }

    @Test
    fun `та же версия не новее`() {
        assertFalse(newer("1.4", "1.4"))
        assertFalse(newer("1.4.0", "1.4"))
    }

    @Test
    fun `недостающие части считаются нулями`() {
        assertTrue(newer("1.4.1", "1.4"))
        assertFalse(newer("1.4", "1.4.1"))
    }

    @Test
    fun `хвост после числа отбрасывается`() {
        assertFalse(newer("1.4-beta", "1.4"))
        assertTrue(newer("1.5-beta", "1.4"))
    }

    @Test
    fun `версия ниоткуда не считается новее`() {
        assertFalse(newer("", "1.4"))
        assertFalse(newer("сборка", "1.4"))
    }

    @Test
    fun `когда своей версии не знают — новее любая`() {
        assertTrue(newer("1.0", ""))
    }
}
