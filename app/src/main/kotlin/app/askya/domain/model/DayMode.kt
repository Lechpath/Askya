package app.askya.domain.model

import app.askya.data.entity.CheckIn

/**
 * Режим дня — подсказка о допустимой нагрузке. Считается по последнему чек-ину
 * за сегодня; в базе не хранится, потому что это производная от него величина
 * и хранить её значило бы держать две версии правды.
 */
enum class DayMode(val label: String, val hint: String?) {
    RED("Красный режим", "Минимум нагрузки. Работа, Библия, МФР, одно важное дело."),
    YELLOW("Жёлтый режим", "Обычный режим. Держи ритм, не перегружайся."),
    GREEN("Зелёный режим", "Хороший ресурс. Можно добавить развитие, но без перегруза."),

    /** Подсказки нет намеренно: вместо неё экран показывает кнопку «Чек-ин». */
    UNKNOWN("Нет данных", null);

    companion object {
        fun of(checkIn: CheckIn?): DayMode = when {
            checkIn == null -> UNKNOWN
            checkIn.energy <= 4 || checkIn.bodyPain >= 6 -> RED
            checkIn.energy >= 8 && checkIn.bodyPain <= 3 -> GREEN
            else -> YELLOW
        }
    }
}
