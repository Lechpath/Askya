package app.askya.weather

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Разбор ответа геокодера.
 *
 * Проверяется обычным JVM-тестом: `parsePlaces` — чистая функция над строкой,
 * и ни телефона, ни сети ей не нужно. `org.json` в тестах подложен отдельной
 * зависимостью — системный класс в JVM-сборке заглушка (см. `build.gradle.kts`).
 */
class WeatherPlacesTest {

    @Test
    fun `имя, координаты и подпись из страны с областью`() {
        val places = WeatherService.parsePlaces(
            """
            {"results":[
              {"name":"Казань","latitude":55.78874,"longitude":49.12214,
               "country":"Россия","admin1":"Татарстан"}
            ]}
            """.trimIndent(),
        )

        assertEquals(1, places.size)
        assertEquals("Казань", places[0].name)
        assertEquals("Россия, Татарстан", places[0].region)
        assertEquals(55.78874, places[0].latitude)
        assertEquals(49.12214, places[0].longitude)
    }

    @Test
    fun `область, совпавшая со страной, не повторяется`() {
        // Города-государства и федеральные города приходят с admin1, равным
        // стране; «Сингапур, Сингапур» — подпись ни о чём.
        val places = WeatherService.parsePlaces(
            """
            {"results":[
              {"name":"Сингапур","latitude":1.28967,"longitude":103.85007,
               "country":"Сингапур","admin1":"Сингапур"}
            ]}
            """.trimIndent(),
        )

        assertEquals("Сингапур", places[0].region)
    }

    @Test
    fun `место без координат выбрасывается`() {
        val places = WeatherService.parsePlaces(
            """
            {"results":[
              {"name":"Ниоткуда","country":"Россия"},
              {"name":"Тверь","latitude":56.85836,"longitude":35.90057,"country":"Россия"}
            ]}
            """.trimIndent(),
        )

        assertEquals(listOf("Тверь"), places.map { it.name })
        assertEquals("Россия", places[0].region)
    }

    @Test
    fun `ничего не нашлось — пустой список, а не поломка`() {
        // Геокодер на несуществующее слово отвечает объектом без `results`.
        assertTrue(WeatherService.parsePlaces("""{"generationtime_ms":0.2}""").isEmpty())
        assertTrue(WeatherService.parsePlaces("""{"results":[]}""").isEmpty())
    }
}
