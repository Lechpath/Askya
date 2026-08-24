package app.askya.domain.model

/**
 * Шкала чек-ина: в базе лежит число 1..10, а словесная подпись живёт здесь —
 * это вопрос представления, а не хранения, и в сущности Room ему не место.
 *
 * Границы совпадают с порогами режима дня (см. [DayMode]), чтобы подпись
 * и цвет карточки не расходились.
 */
enum class Level(val range: IntRange, val label: String) {
    LOW(1..4, "Низко"),
    MEDIUM(5..7, "Средне"),
    HIGH(8..10, "Высоко");

    companion object {
        const val MIN = 1
        const val MAX = 10

        fun of(value: Int): Level = entries.firstOrNull { value in it.range } ?: MEDIUM
    }
}
