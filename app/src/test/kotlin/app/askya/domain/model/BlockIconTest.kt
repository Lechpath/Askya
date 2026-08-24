package app.askya.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Знак дела выводится из названия, а порядок правил решает исход. Проверяется
 * тестом: на телефоне для этого пришлось бы заводить дело на каждое слово.
 */
class BlockIconTest {

    @Test
    fun `дело узнаётся по слову в названии`() {
        assertEquals(BlockIcon.WAKE, BlockIcons.of("Подъём"))
        assertEquals(BlockIcon.ROAD, BlockIcons.of("Дорога домой"))
        assertEquals(BlockIcon.SPORT, BlockIcons.of("Бег"))
        assertEquals(BlockIcon.FOOD, BlockIcons.of("Ужин"))
        assertEquals(BlockIcon.WORK, BlockIcons.of("Работа"))
        assertEquals(BlockIcon.MEET, BlockIcons.of("Время с женой"))
    }

    @Test
    fun `при двух словах в названии выигрывает первое по порядку правил`() {
        // «Душ и ужин» — про то, с чего дело начинается: сначала душ. Правило
        // гигиены стоит выше еды именно поэтому.
        assertEquals(BlockIcon.SHOWER, BlockIcons.of("Душ и ужин"))
    }

    @Test
    fun `узкое слово выигрывает у общего`() {
        // «Чтение Библии» — это чтение, а не служение, хотя «Библия»
        // встречается в обоих смыслах. Порядок правил и решает.
        assertEquals(BlockIcon.READ, BlockIcons.of("Чтение Библии"))
        assertEquals(BlockIcon.SERVICE, BlockIcons.of("Служение"))
    }

    @Test
    fun `регистр не важен`() {
        assertEquals(BlockIcon.SPORT, BlockIcons.of("ЗАРЯДКА"))
        assertEquals(BlockIcon.WORK, BlockIcons.of("работа в офисе"))
    }

    @Test
    fun `не угадалось — часы, а не случайный знак`() {
        // Неверная картинка врёт про дело, а часы просто ничего не добавляют.
        assertEquals(BlockIcon.PLAIN, BlockIcons.of("Приложения в Claude Code"))
        assertEquals(BlockIcon.PLAIN, BlockIcons.of(""))
    }
}
