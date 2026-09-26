package app.askya.agent.llm

import app.askya.agent.ToolSpec
import java.util.Collections

/**
 * Модель, с которой разговаривает агент, — любая: на устройстве или в сети.
 *
 * Агент ([app.askya.agent.AgentSession]) видит только эти типы. Новая модель —
 * новый класс, сама сессия при этом не меняется (`AI_AGENT_ARCHITECTURE.md`,
 * §11).
 *
 * Клиент только отвечает на запрос. Разрешено ли ему что-то отправлять, решает
 * не он, а сессия — перед каждым вызовом [next] (`AgentPolicy.allowsClient`).
 */
interface LlmClient {

    /** Кто это — для журнала и экрана: «rules», «anthropic:…». */
    val id: String

    /** Уходит ли что-то с устройства. По этому признаку сессия спрашивает политику. */
    val online: Boolean

    /**
     * Следующий ответ модели на всю историю разговора. Сеть, таймаут, отказ
     * ключа — [LlmReply.Failure], а не исключение; отмена хода проходит
     * насквозь.
     */
    suspend fun next(request: LlmRequest): LlmReply
}

/** Что уходит модели: правила, вся история и описания инструментов. */
data class LlmRequest(
    val system: String,
    /** Только добавляется: прошлые реплики не переписываются. */
    val messages: List<LlmMessage>,
    val tools: List<ToolSpec>,
)

enum class Role { USER, ASSISTANT }

data class LlmMessage(val role: Role, val parts: List<LlmPart>)

/** Кусок реплики. */
sealed interface LlmPart {

    data class Text(val text: String) : LlmPart

    /** Модель просит вызвать инструмент. [id] — чтобы ответить именно на этот вызов. */
    data class ToolCall(val id: String, val name: String, val input: Map<String, Any?>) : LlmPart

    /** Ответ на вызов [callId]: JSON или слова отказа ([isError]). */
    data class ToolResult(val callId: String, val content: String, val isError: Boolean) : LlmPart

    /**
     * Служебный блок ответа провайдера, который надо вернуть ему как есть
     * (например, ход рассуждения с подписью).
     *
     * Неизменяемый снимок: при создании [raw] копируется целиком, вложенные
     * словари и списки тоже, и всё заворачивается в неизменяемые обёртки.
     * Поменять исходный словарь после создания можно — на снимок это не
     * повлияет. Внутри — только значения JSON (как их пишет
     * `app.askya.data.sync.Json`): строки, числа, `true`/`false`, `null`,
     * словари со строковыми ключами и списки. Остальное — ошибка при
     * создании: изменяемому объекту здесь не место.
     *
     * Сессия ([app.askya.agent.AgentSession]) его не читает и не толкует:
     * хранит в истории в той же реплике и отдаёт следующему запросу как есть.
     * Что лежит в [raw], знает только клиент: он создаёт блок из ответа своего
     * провайдера и сам переводит его обратно в запросе.
     */
    class Opaque(raw: Map<String, Any?>) : LlmPart {

        val raw: Map<String, Any?> = snapshot(raw)

        override fun equals(other: Any?): Boolean = other is Opaque && other.raw == raw

        override fun hashCode(): Int = raw.hashCode()

        override fun toString(): String = "Opaque(raw=$raw)"

        private companion object {

            fun snapshot(map: Map<*, *>): Map<String, Any?> {
                val copy = LinkedHashMap<String, Any?>()
                map.forEach { (key, value) ->
                    require(key is String) { "Opaque: ключ словаря должен быть строкой" }
                    copy[key] = frozen(value)
                }
                return Collections.unmodifiableMap(copy)
            }

            /** Имя типа, но не значение: в значении бывает текст разговора. */
            fun frozen(value: Any?): Any? = when (value) {
                null, is String, is Boolean, is Int, is Long, is Double, is Float -> value
                is Map<*, *> -> snapshot(value)
                is List<*> -> Collections.unmodifiableList(value.map(::frozen))
                else -> throw IllegalArgumentException("Opaque: ${value::class.simpleName} — не значение JSON")
            }
        }
    }
}

/** Чем ответила модель. */
sealed interface LlmReply {

    /** Реплика: текст, вызовы инструментов или то и другое. */
    data class Turn(val parts: List<LlmPart>) : LlmReply

    /** Ответа нет — словами, которые можно показать человеку. */
    data class Failure(val message: String) : LlmReply
}
