package app.askya.agent

/**
 * Инструмент агента — то, что модель может попросить сделать.
 *
 * Интерфейс запечатан: инструмент бывает ровно [ReadTool] или [ProposeTool], и
 * третьего рода не заведёшь, не поправив этот файл. Сами инструменты при этом
 * пишутся где угодно — запечатан род, а не список инструментов.
 *
 * Модель видит от инструмента только описание ([ToolSpec]): имя, смысл и
 * схему аргументов. Сам объект с его репозиториями остаётся у приложения.
 *
 * Аргументы приходят словарём — так их отдаёт разбор JSON
 * (`app.askya.data.sync.Json.read`): строки, `Long`, `Double`, `Boolean`,
 * `null`, списки и словари. Разобрать их и отказать словами, если они не
 * годятся, — дело самого инструмента.
 */
sealed interface AgentTool {

    /** Имя для модели: латиница, цифры, `_` и `-`, до 64 знаков (см. [ToolRegistry]). */
    val name: String

    /** Что инструмент делает — модель выбирает по этому тексту. */
    val description: String

    /**
     * JSON Schema аргументов в виде словаря, пригодного для
     * `app.askya.data.sync.Json.write`. Провайдер модели переводит её в свой
     * формат сам.
     */
    val inputSchema: Map<String, Any?>
}

/**
 * Читает и ничего не меняет. Возвращает данные уже готовыми для модели — не
 * сущности Room, а словарь из простых значений (см. [ToolResult]).
 */
interface ReadTool : AgentTool {
    suspend fun read(input: Map<String, Any?>, context: AgentContext): ToolResult
}

/**
 * Собирает предложение и больше ничего.
 *
 * Не пишет в базу, не ставит будильников, не зовёт репозиториев на запись.
 * Проверяет аргументы и отвечает [ProposalCheck]: годится — вот что будет
 * сделано, не годится — вот почему. Номер, состояние и время предложению даёт
 * [ToolTurn], а не инструмент: иначе инструмент мог бы выдать предложение за
 * уже подтверждённое.
 */
interface ProposeTool : AgentTool {
    suspend fun propose(input: Map<String, Any?>, context: AgentContext): ProposalCheck
}

/**
 * Род инструмента — по тому, что он есть, а не по тому, что он о себе говорит.
 *
 * Объект, который прикинулся бы и тем и другим, в реестр не попадает
 * ([ToolRegistry] отказывает ему при сборке), поэтому порядок веток здесь
 * ничего не решает.
 */
val AgentTool.kind: ToolKind
    get() = when (this) {
        is ReadTool -> ToolKind.READ
        is ProposeTool -> ToolKind.PROPOSE
    }

/**
 * Что модель знает об инструменте. Ни репозиториев, ни самого инструмента —
 * только слова и схема.
 */
data class ToolSpec(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?>,
    val kind: ToolKind,
)
