package app.askya.data.preferences

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Список городов хранится строкой, и разбор её — единственное место, где он
 * может молча потеряться. Отсюда проверки: круг «записали — прочитали»,
 * названия с запятыми и мусор, который не должен ронять весь список.
 */
class PlacesStoreTest {

    @Test
    fun `записанное читается обратно тем же`() {
        val places = listOf(
            ChosenPlace("Казань", 55.7963, 49.1088),
            ChosenPlace("Санкт-Петербург", 59.9386, 30.3141),
        )

        assertEquals(places, places.writePlaces().readPlaces())
    }

    @Test
    fun `запятая в названии ничего не ломает`() {
        val places = listOf(ChosenPlace("Ростов-на-Дону, Россия", 47.2313, 39.7233))

        assertEquals(places, places.writePlaces().readPlaces())
    }

    @Test
    fun `испорченная строка пропускается, а остальные остаются`() {
        val written = "Казань|55.7963|49.1088\nсовсем не место\nМосква|55.7558|37.6173"

        assertEquals(listOf("Казань", "Москва"), written.readPlaces().map { it.name })
    }

    @Test
    fun `пустое хранилище даёт пустой список`() {
        assertTrue("".readPlaces().isEmpty())
    }

    @Test
    fun `ключ места считается от координат, а не от названия`() {
        val one = ChosenPlace("Казань", 55.7963, 49.1088)
        val same = ChosenPlace("Казань, Татарстан", 55.7963, 49.1088)
        val other = ChosenPlace("Казань", 55.8963, 49.1088)

        assertEquals(one.key, same.key)
        assertTrue(one.key != other.key)
    }
}
