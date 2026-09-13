package app.askya.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import app.askya.ui.components.DayPart
import kotlinx.coroutines.delay
import java.time.LocalTime

/**
 * Хамелеон — краска, которая идёт за временем суток.
 *
 * ## Зачем отдельным правилом, а не седьмой парой чисел
 *
 * Все остальные гаммы — это шесть красок, записанных в перечислении. Хамелеон
 * записать так нельзя: он и есть смена. Поэтому он не заводит своих красок, а
 * указывает на три уже готовые гаммы — янтарь, небо, слива, — и всё, что о нём
 * нужно знать, живёт здесь одной таблицей, а не размазано по местам, где цвет
 * спрашивают.
 *
 * ## Почему именно эти три поры
 *
 * Сутки делятся там же, где их делит расписание AskyaDay: до десяти — утро,
 * до шести вечера — день, дальше — вечер ([DayPart]). Своих границ хамелеон не
 * заводит намеренно: цвет приложения меняется ровно тогда, когда в расписании
 * начинается следующая часть дня, и это видно глазом — заголовок «Вечер» и
 * лиловый цветок приходят вместе.
 *
 * ## Чего он не делает
 *
 * Не трогает ни бумаги, ни ночи: меняется акцент, а не лист. И не спорит с
 * тёмной темой — у каждой из трёх гамм своя ночная половина, и хамелеон
 * вечером в темноте берёт ночную сливу, а не дневную.
 */
fun AskyaPalette.at(part: DayPart): AskyaPalette =
    if (this != AskyaPalette.CHAMELEON) this else when (part) {
        DayPart.MORNING -> AskyaPalette.AMBER
        DayPart.DAY -> AskyaPalette.SKY
        DayPart.EVENING -> AskyaPalette.PLUM
    }

/** То же для цветка — теми же порами, чтобы знак и гамма не разъезжались. */
fun FlowerColor.at(part: DayPart): FlowerColor =
    if (this != FlowerColor.CHAMELEON) this else when (part) {
        DayPart.MORNING -> FlowerColor.AMBER
        DayPart.DAY -> FlowerColor.SKY
        DayPart.EVENING -> FlowerColor.PLUM
    }

/** Пора прямо сейчас — тем, кто спрашивает цвет вне разметки. */
fun dayPartNow(): DayPart = DayPart.of(LocalTime.now())

/**
 * Пора, которая сама сменится, когда придёт время.
 *
 * Просыпается не по минутам, а ровно на границе поры: цвет меняется трижды в
 * сутки, и будить приложение шестьсот раз ради трёх — расточительство, которое
 * видно по батарее. Пока экрана не видно, поток остановлен вместе с
 * композицией.
 *
 * Нужен даже тому, кто выбрал неподвижную гамму: [at] вернёт ему его же
 * гамму, а лишней перерисовки не будет — значение при смене поры то же самое.
 */
@Composable
fun rememberDayPart(): DayPart = produceState(initialValue = dayPartNow()) {
    while (true) {
        val now = LocalTime.now()
        value = DayPart.of(now)
        delay(untilNextPart(now))
    }
}.value

/**
 * Сколько миллисекунд до следующей поры.
 *
 * Полночь считается границей наравне с десятью и шестью: вечер за ней
 * продолжается, но ждать до утра одним сном в четырнадцать часов значит
 * проспать перевод часов и уход телефона в глубокий сон. Секунда сверху — чтобы
 * проснувшийся ровно на границе не увидел прежнюю пору из-за округления и не
 * ушёл спать на нулевой срок.
 */
internal fun untilNextPart(now: LocalTime): Long {
    val turns = listOf(LocalTime.of(10, 0), LocalTime.of(18, 0))
    val next = turns.firstOrNull { it > now }
    val millis = if (next != null) {
        java.time.Duration.between(now, next).toMillis()
    } else {
        java.time.Duration.between(now, LocalTime.MAX).toMillis() + 1
    }
    return millis + 1_000L
}
