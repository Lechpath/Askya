package app.askya.agent

/**
 * Ответ читающего инструмента.
 *
 * Данные — словарь из того, что умеет `app.askya.data.sync.Json.write`:
 * строки, числа, `Boolean`, `null`, списки и словари. Сущностей Room здесь не
 * бывает: модель получает то, что для неё выбрано, а не строку таблицы со всеми
 * её служебными полями. Что-то другое [ToolTurn] не пропустит.
 */
sealed interface ToolResult {

    data class Ok(val data: Map<String, Any?>) : ToolResult

    /** Инструмент сам отказался — словами, которые можно передать модели. */
    data class Failed(val reason: String) : ToolResult
}

/**
 * Ответ инструмента-предложения: проверенное «что будет сделано» или отказ.
 *
 * Номера и состояния здесь нет — их даёт [ToolTurn], когда превращает
 * [Valid] в [Proposal].
 */
sealed interface ProposalCheck {

    /**
     * Годится.
     *
     * [summary] — то, что увидят человек в карточке и модель в ответе: что
     * именно будет создано, словами. [payload] — данные для того, кто позже
     * применит подтверждённое.
     */
    data class Valid(val summary: String, val payload: ProposalPayload) : ProposalCheck

    /** Не годится — словами, которые можно передать модели. */
    data class Invalid(val reason: String) : ProposalCheck
}
