package app.askya.agent.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Служебный блок провайдера — неизменяемый снимок, а не ссылка на чужой словарь. */
class OpaqueTest {

    @Test
    fun `правка исходного словаря снимок не меняет`() {
        val inner = mutableListOf<Any?>("а")
        val nested = mutableMapOf<String, Any?>("k" to "v")
        val source = mutableMapOf<String, Any?>("list" to inner, "nested" to nested, "n" to 1L)
        val block = LlmPart.Opaque(source)

        source["n"] = 2L
        source["new"] = "x"
        inner += "б"
        nested["k"] = "другое"

        assertEquals(mapOf("list" to listOf("а"), "nested" to mapOf("k" to "v"), "n" to 1L), block.raw)
    }

    @Test
    fun `снимок не поменять и изнутри`() {
        val block = LlmPart.Opaque(mapOf("list" to listOf(1L), "nested" to mapOf("k" to "v")))
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (block.raw as MutableMap<String, Any?>)["x"] = 1L }
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (block.raw["list"] as MutableList<Any?>).add(2L) }
        @Suppress("UNCHECKED_CAST")
        assertFailsWith<UnsupportedOperationException> { (block.raw["nested"] as MutableMap<String, Any?>).clear() }
    }

    @Test
    fun `только значения JSON, и ошибка без содержимого`() {
        val error = assertFailsWith<IllegalArgumentException> {
            LlmPart.Opaque(mapOf("buffer" to StringBuilder("секрет")))
        }
        assertTrue("секрет" !in error.message.orEmpty(), error.message)
        assertFailsWith<IllegalArgumentException> { LlmPart.Opaque(mapOf("nested" to mapOf(1 to "v"))) }
    }

    @Test
    fun `равенство по содержимому`() {
        assertEquals(LlmPart.Opaque(mapOf("a" to 1L)), LlmPart.Opaque(mapOf("a" to 1L)))
        assertEquals(LlmPart.Opaque(mapOf("a" to 1L)).hashCode(), LlmPart.Opaque(mapOf("a" to 1L)).hashCode())
    }
}
