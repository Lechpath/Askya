package app.askya.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Правило облака — все четыре сочетания «разрешено ли облако» и «уходит ли
 * что-то с устройства». Оно одно на все данные агента, заметки в том числе.
 */
class AgentPolicyTest {

    private val closed = AgentPolicy(allowCloud = false)
    private val open = AgentPolicy(allowCloud = true)

    @Test
    fun `облако закрыто, клиент на устройстве — можно`() {
        assertTrue(closed.allowsClient(online = false))
    }

    @Test
    fun `облако закрыто, клиент в сети — нельзя`() {
        assertFalse(closed.allowsClient(online = true))
    }

    @Test
    fun `облако открыто, клиент на устройстве — можно`() {
        assertTrue(open.allowsClient(online = false))
    }

    @Test
    fun `облако открыто, клиент в сети — можно`() {
        assertTrue(open.allowsClient(online = true))
    }

    @Test
    fun `по умолчанию облако закрыто`() {
        assertEquals(closed, AgentPolicy())
    }

    @Test
    fun `ответ зависит только от политики на момент вопроса`() {
        // Ничего не запоминается: разрешение, выключенное после первого
        // вопроса, действует на следующем — поэтому спрашивать перед каждой
        // отправкой имеет смысл.
        var policy = open
        assertTrue(policy.allowsClient(online = true))
        policy = policy.copy(allowCloud = false)
        assertFalse(policy.allowsClient(online = true))
        assertTrue(open.allowsClient(online = true), "прежняя политика не изменилась")
    }
}
