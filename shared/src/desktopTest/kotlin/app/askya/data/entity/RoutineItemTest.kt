package app.askya.data.entity

import app.askya.domain.model.Priority
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Какое дело списка встаёт в день само.
 *
 * Правило одно и решает много: по нему дело попадает в расписание в обход
 * выключателя «Заполняет новый день автоматически». Ошибись здесь в одну
 * сторону — и человек, выключивший заполнение, получает весь список в каждом
 * дне; в другую — «Пн Ср Пт» опять не доходит до понедельника.
 */
class RoutineItemTest {

    private fun deed(days: String? = null, priority: Priority = Priority.NORMAL) =
        RoutineItem(title = "Бег", startTime = LocalTime.of(7, 0), priority = priority, days = days)

    @Test
    fun `дело с выбранными днями встаёт само`() {
        assertTrue(deed(days = "1,3,5").standsAlone)
        assertTrue(deed(days = "7").standsAlone)
    }

    @Test
    fun `важное дело встаёт само и без дней`() {
        assertTrue(deed(priority = Priority.HIGH).standsAlone)
    }

    @Test
    fun `каждый день само не встаёт`() {
        // Пустая колонка — не выбор человека, а значение по умолчанию: принять
        // её за «все семь дней» значило бы разворачивать весь список в обход
        // выключателя.
        assertFalse(deed().standsAlone)
        assertFalse(deed(days = "").standsAlone)
        assertFalse(deed(days = "1,2,3,4,5,6,7").standsAlone)
    }
}
