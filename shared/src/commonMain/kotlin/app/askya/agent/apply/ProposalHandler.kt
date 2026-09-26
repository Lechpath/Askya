package app.askya.agent.apply

import app.askya.agent.ProposalPayload
import kotlin.reflect.KClass

/**
 * Как применить предложение одного вида — дело, напоминание, заметку.
 *
 * Обработчик получает только данные предложения, а не само предложение:
 * состояние ведёт [ProposalExecutor], и обработчик не может ни пропустить
 * подтверждение, ни объявить предложение применённым сам.
 */
interface ProposalHandler<P : ProposalPayload> {

    /** Какие данные он умеет применять. Один вид — один обработчик. */
    val payloadType: KClass<P>

    /**
     * Проверить заново и применить. Ошибка базы — исключение: его ловит
     * исполнитель и превращает в безопасную причину, а подробности модели не
     * уходят.
     */
    suspend fun apply(payload: P): HandlerResult
}

/** Чем кончилось применение у обработчика. */
sealed interface HandlerResult {

    /**
     * Сделано. [data] — что вернуть модели (номера созданного и т.п., только
     * простые значения), [warnings] — что сказать человеку: сделано, но не
     * совсем так, как он мог рассчитывать.
     */
    data class Done(
        val data: Map<String, Any?>,
        val warnings: List<String> = emptyList(),
    ) : HandlerResult

    /** Повторная проверка не прошла — ничего не записано. */
    data class Invalid(val reason: String) : HandlerResult
}
