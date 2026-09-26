package app.askya.agent

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Общий разбор аргументов: обязательное, необязательное, вид, пределы и
 * лишние ключи. Значения аргументов в ошибки не попадают.
 */
class ToolArgsTest {

    private val keys = setOf("query", "limit", "date", "exact")

    private fun <T> ok(input: Map<String, Any?>, read: ToolArgs.() -> T): T =
        assertIs<ArgsResult.Ok<T>>(readArgs(input, keys, read)).value

    private fun <T> bad(input: Map<String, Any?>, read: ToolArgs.() -> T): String =
        assertIs<ArgsResult.Bad>(readArgs(input, keys, read)).reason

    @Test
    fun `обязательный аргумент есть`() {
        assertEquals("дача", ok(mapOf("query" to "дача")) { requireString("query") })
    }

    @Test
    fun `обязательного нет, он null или пустой — ошибка с его именем`() {
        listOf(emptyMap(), mapOf("query" to null), mapOf("query" to "  ")).forEach { input ->
            assertEquals("query обязателен", bad(input) { requireString("query") })
        }
    }

    @Test
    fun `необязательные могут отсутствовать`() {
        val read = ok(emptyMap()) { Triple(string("query"), int("limit", 10), boolean("exact")) }
        assertEquals(Triple(null, 10, false), read)
        assertEquals(null, ok(emptyMap()) { date("date") })
        assertEquals(true, ok(mapOf("exact" to null)) { boolean("exact", default = true) })
    }

    @Test
    fun `не тот вид — ошибка`() {
        assertEquals("query должен быть строкой", bad(mapOf("query" to 42L)) { string("query") })
        assertEquals("exact должен быть true или false", bad(mapOf("exact" to "yes")) { boolean("exact") })
        assertEquals("limit должен быть целым числом", bad(mapOf("limit" to "10")) { int("limit", 10) })
        assertEquals("limit должен быть целым числом", bad(mapOf("limit" to 3.0)) { int("limit", 10) })
        assertTrue(bad(mapOf("date" to 20300510L)) { date("date") }.startsWith("date"))
    }

    @Test
    fun `целое приходит Long из разбора JSON`() {
        assertEquals(15, ok(mapOf("limit" to 15L)) { int("limit", 10, 1..20) })
    }

    @Test
    fun `пределы`() {
        assertEquals("limit должен быть от 1 до 20", bad(mapOf("limit" to 0L)) { int("limit", 10, 1..20) })
        assertEquals("limit должен быть от 1 до 20", bad(mapOf("limit" to 21L)) { int("limit", 10, 1..20) })
        assertEquals("limit вне допустимых пределов", bad(mapOf("limit" to Long.MAX_VALUE)) { int("limit", 10) })
    }

    @Test
    fun `дата — только ISO`() {
        assertEquals(LocalDate.of(2030, 5, 10), ok(mapOf("date" to " 2030-05-10 ")) { date("date") })
        listOf("завтра", "10.05.2030", "2030-13-01").forEach { raw ->
            assertEquals("date должна быть в виде ГГГГ-ММ-ДД", bad(mapOf("date" to raw)) { date("date") })
        }
    }

    @Test
    fun `неизвестный аргумент — ошибка, до разбора остального`() {
        var read = false
        val reason = bad(mapOf("query" to "дача", "delete" to true, "all" to 1L)) {
            read = true
            requireString("query")
        }
        assertEquals("неизвестные аргументы: all, delete", reason)
        assertEquals(false, read)
    }

    @Test
    fun `значение аргумента в ошибку не попадает`() {
        val reason = bad(mapOf("date" to "пароль от почты 1234")) { date("date") }
        assertTrue("1234" !in reason && "пароль" !in reason, reason)
    }

    @Test
    fun `своя причина отказа`() {
        assertEquals("так нельзя", bad(emptyMap()) { reject("так нельзя") })
    }
}
