package app.askya.ui.theme

import app.askya.ui.components.DayPart
import java.time.Duration
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Хамелеон: во что он разворачивается и когда просыпается.
 *
 * Проверяется здесь то, чего на телефоне не увидеть, не просидев над ним
 * сутки: что каждая пора даёт свою гамму, что неподвижные гаммы правило не
 * трогает вовсе и что сон до следующей поры не выходит нулевым — иначе
 * приложение крутилось бы в пустом цикле на границе часа.
 */
class ChameleonTest {

    @Test
    fun `хамелеон берёт гамму у поры`() {
        assertEquals(AskyaPalette.AMBER, AskyaPalette.CHAMELEON.at(DayPart.MORNING))
        assertEquals(AskyaPalette.SKY, AskyaPalette.CHAMELEON.at(DayPart.DAY))
        assertEquals(AskyaPalette.PLUM, AskyaPalette.CHAMELEON.at(DayPart.EVENING))
    }

    @Test
    fun `цветок идёт теми же порами`() {
        assertEquals(FlowerColor.AMBER, FlowerColor.CHAMELEON.at(DayPart.MORNING))
        assertEquals(FlowerColor.SKY, FlowerColor.CHAMELEON.at(DayPart.DAY))
        assertEquals(FlowerColor.PLUM, FlowerColor.CHAMELEON.at(DayPart.EVENING))
    }

    @Test
    fun `неподвижная гамма остаётся собой в любую пору`() {
        DayPart.entries.forEach { part ->
            assertSame(AskyaPalette.CHERRY, AskyaPalette.CHERRY.at(part))
            assertSame(FlowerColor.FIRE, FlowerColor.FIRE.at(part))
        }
    }

    @Test
    fun `сон идёт до ближайшей границы поры`() {
        assertEquals(Duration.ofHours(2).toMillis() + 1_000L, untilNextPart(LocalTime.of(8, 0)))
        assertEquals(Duration.ofHours(1).toMillis() + 1_000L, untilNextPart(LocalTime.of(17, 0)))
    }

    @Test
    fun `на самой границе и в конце суток сон не нулевой`() {
        // Ровно в десять пора уже сменилась, и ждать надо до шести вечера, а
        // не нисколько: сон в ноль миллисекунд — это холостой цикл.
        assertEquals(Duration.ofHours(8).toMillis() + 1_000L, untilNextPart(LocalTime.of(10, 0)))
        assertTrue(untilNextPart(LocalTime.of(23, 59, 59)) > 0)
    }
}
