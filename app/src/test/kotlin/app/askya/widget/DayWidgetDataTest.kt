package app.askya.widget

import app.askya.data.entity.ScheduleItem
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Отбор дел для виджета проверяется здесь: на телефоне «сейчас» не подкрутить,
 * а вся суть виджета именно в том, что он отсчитывает от текущего момента.
 */
class DayWidgetDataTest {

    private val date = LocalDate.of(2026, 8, 13)

    private fun item(title: String, start: String, end: String? = null) = ScheduleItem(
        date = date,
        startTime = LocalTime.parse(start),
        endTime = end?.let(LocalTime::parse),
        title = title,
    )

    private val day = listOf(
        item("Подъём", "07:00", "07:15"),
        item("Работа", "10:00", "15:00"),
        item("Бег", "17:30", "18:15"),
    )

    @Test
    fun `прошедшее не показывается`() {
        val state = DayWidgetData.select(date.atTime(16, 0), day, emptyList())
        assertEquals(listOf("Бег"), state.items.map { it.title })
    }

    @Test
    fun `идущее сейчас остаётся первым`() {
        val state = DayWidgetData.select(date.atTime(11, 0), day, emptyList())
        assertEquals(listOf("Работа", "Бег"), state.items.map { it.title })
    }

    @Test
    fun `сегодняшнее днём не помечается`() {
        // Пометка нужна только завтрашнему: подписывать «сегодня» у дела,
        // которое идёт прямо сейчас, значит объяснять очевидное.
        assertFalse(DayWidgetData.select(date.atTime(6, 0), day, emptyList()).tomorrow)
        assertFalse(DayWidgetData.select(date.atTime(11, 0), day, emptyList()).tomorrow)
    }

    @Test
    fun `впереди отдаётся всё — иначе прокручивать было бы нечего`() {
        // На экран помещаются три, но список не режется: остальные достаются
        // прокруткой, и обрезка здесь оставила бы виджет без будущего.
        val long = day + listOf(
            item("Ужин", "19:00", "19:40"),
            item("Чтение", "21:00", "21:40"),
        )
        val state = DayWidgetData.select(date.atTime(6, 0), long, emptyList())
        assertEquals(long.size, state.items.size)
    }

    @Test
    fun `порядок отсчитывается от текущего момента, а не от начала дня`() {
        val long = day + listOf(
            item("Ужин", "19:00", "19:40"),
            item("Чтение", "21:00", "21:40"),
        )
        // 16:00 — «Подъём» и «Работа» позади; первыми на экране те три, что
        // впереди, а не начало дня.
        val state = DayWidgetData.select(date.atTime(16, 0), long, emptyList())
        assertEquals(
            listOf("Бег", "Ужин", "Чтение"),
            state.items.take(WIDGET_VISIBLE_ROWS).map { it.title },
        )
    }

    @Test
    fun `у дела без конца берётся час`() {
        val open = listOf(item("Чтение", "20:00"))
        // 20:40 — дело ещё идёт; 21:10 — уже нет.
        assertEquals(1, DayWidgetData.select(date.atTime(20, 40), open, emptyList()).items.size)
        assertEquals(0, DayWidgetData.select(date.atTime(21, 10), open, emptyList()).items.size)
    }

    @Test
    fun `когда на сегодня всё — показывается завтра`() {
        val tomorrow = listOf(
            item("Подъём", "07:00", "07:15"),
            item("Чтение Библии", "07:15", "08:15"),
            item("Завтрак", "08:15", "09:00"),
            item("Работа", "10:00", "15:00"),
        )
        val state = DayWidgetData.select(date.atTime(23, 0), day, tomorrow)
        // Пометка дня: без неё в одиннадцать вечера «07:00 Подъём» читалось бы
        // как то, что идёт сейчас.
        assertTrue(state.tomorrow)
        // Завтрашний день тоже отдаётся целиком, а на экране первые три.
        assertEquals(tomorrow.size, state.items.size)
        assertEquals(
            listOf("Подъём", "Чтение Библии", "Завтрак"),
            state.items.take(WIDGET_VISIBLE_ROWS).map { it.title },
        )
    }

    @Test
    fun `пусто и сегодня и завтра — виджет говорит об этом`() {
        val state = DayWidgetData.select(date.atTime(23, 0), emptyList(), emptyList())
        assertTrue(state.items.isEmpty())
        assertTrue(state.empty.isNotEmpty())
        assertFalse(state.tomorrow)
    }

    @Test
    fun `идущее дело узнаётся по своему времени, а не по месту в списке`() {
        val work = day[1]
        assertFalse(DayWidgetData.isNow(work, LocalTime.of(9, 59)))
        assertTrue(DayWidgetData.isNow(work, LocalTime.of(10, 0)))
        assertTrue(DayWidgetData.isNow(work, LocalTime.of(14, 59)))
        assertFalse(DayWidgetData.isNow(work, LocalTime.of(15, 0)))
    }

    @Test
    fun `наложенные дела оба считаются идущими`() {
        // Длинное дело с утра и короткое посреди него: пока идущим считалось
        // первое в списке, короткое не подсвечивалось вовсе.
        val short = item("Звонок", "11:00", "11:30")
        val moment = LocalTime.of(11, 15)
        assertTrue(DayWidgetData.isNow(day[1], moment))
        assertTrue(DayWidgetData.isNow(short, moment))
    }

    @Test
    fun `дело без конца идёт час`() {
        val open = item("Чтение", "20:00")
        assertTrue(DayWidgetData.isNow(open, LocalTime.of(20, 40)))
        assertFalse(DayWidgetData.isNow(open, LocalTime.of(21, 10)))
    }

    @Test
    fun `дела идут по времени начала, а не по порядку записи`() {
        val messy = listOf(item("Бег", "17:30", "18:15"), item("Работа", "10:00", "15:00"))
        val state = DayWidgetData.select(date.atTime(9, 0), messy, emptyList())
        assertEquals(listOf("Работа", "Бег"), state.items.map { it.title })
    }

    @Test
    fun `дело с концом раньше начала не считается прошедшим весь вечер`() {
        // Конец «за полночь» обрезается концом суток, иначе в 23:30 дело
        // выглядело бы законченным.
        val late = listOf(item("Смена", "22:00", "02:00"))
        val state = DayWidgetData.select(date.atTime(23, 30), late, emptyList())
        assertEquals(listOf("Смена"), state.items.map { it.title })
    }
}
