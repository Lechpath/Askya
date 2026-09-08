package app.askya.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.YearMonth

/**
 * Карта замысла считает три вещи, и все три на телефоне не проверить.
 *
 * Место нового узла зависит от того, что уже лежит вокруг; догадка о состоянии
 * — от полутора месяцев записей; вес победы — от того, из чего узел вырос.
 * Чтобы поймать ошибку в любом из трёх, руками пришлось бы разложить карту в
 * двадцать узлов и подождать полгода. То же рассуждение, по которому тестами
 * покрыт пульс.
 */
class ThreadWeaveTest {

    private val through = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 8)

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    // ---- Куда встаёт новый узел ----

    @Test
    fun `первый узел нити встаёт в начало координат`() {
        assertEquals(Spot(0f, 0f), placeNear(parent = null, taken = emptyList()))
    }

    @Test
    fun `выросший узел встаёт под родителем`() {
        // Вниз, а не вбок: карта читается сверху вниз, как всё написанное.
        val spot = placeNear(parent = Spot(0f, 0f), taken = listOf(Spot(0f, 0f)))

        assertEquals(0f, spot.x)
        assertTrue(spot.y > 0f)
    }

    @Test
    fun `занятое место обходится, а не занимается вторым узлом`() {
        val parent = Spot(0f, 0f)
        val under = Spot(0f, NODE_STEP)

        val spot = placeNear(parent = parent, taken = listOf(parent, under))

        // Ровно под родителем уже кто-то лежит — новый ушёл в сторону.
        assertTrue(
            kotlin.math.abs(spot.x - under.x) >= NODE_GAP ||
                kotlin.math.abs(spot.y - under.y) >= NODE_GAP,
        )
    }

    @Test
    fun `узел без родителя уходит под всё, что уже разложено`() {
        // Отдельная ветка замысла не должна теряться в гуще старых узлов.
        val taken = listOf(Spot(0f, 0f), Spot(200f, 120f), Spot(-40f, 400f))

        val spot = placeNear(parent = null, taken = taken)

        assertTrue(spot.y > taken.maxOf { it.y })
    }

    // ---- Признаки карты ----

    @Test
    fun `свежими считаются узлы за две недели, а ветвлением — расхождение`() {
        val signs = signsOf(
            made = listOf(day(9, 6), day(9, 1), day(6, 1)),
            ties = listOf(Tie(1, 2), Tie(1, 3), Tie(2, 4)),
            today = today,
        )

        assertEquals(3, signs.nodes)
        assertEquals(2, signs.fresh)
        assertEquals(3, signs.ties)
        // Ветвится только первый узел: из него выходят две связи, из второго —
        // одна, а цепочка это не плетение.
        assertEquals(1, signs.branches)
    }

    // ---- Догадка о состоянии ----

    @Test
    fun `долгая тишина читается как тление`() {
        val pulse = pulseOf(touches = listOf(day(7, 1)), through = through)
        val signs = ThreadSigns(nodes = 5)

        assertEquals(
            ThreadState.SMOULDERING,
            suggestState(ThreadState.GROWING, pulse, signs, today),
        )
    }

    @Test
    fun `у спящей и закрытой не спрашивают ничего`() {
        val pulse = pulseOf(touches = listOf(day(7, 1)), through = through)
        val signs = ThreadSigns(nodes = 5)

        // Спящую отложили нарочно; напоминать человеку о его же решении — это
        // и есть то канючанье, которого в Askya нет.
        assertNull(suggestState(ThreadState.SLEEPING, pulse, signs, today))
        assertNull(suggestState(ThreadState.DONE, pulse, signs, today))
        assertNull(suggestState(ThreadState.DROPPED, pulse, signs, today))
    }

    @Test
    fun `пустая и нетронутая карта не даёт догадки`() {
        val pulse = pulseOf(touches = listOf(day(9, 7)), through = through)

        assertNull(suggestState(ThreadState.GROWING, pulse, ThreadSigns(), today))
        assertNull(
            suggestState(
                state = ThreadState.GROWING,
                pulse = pulseOf(emptyList(), through),
                signs = ThreadSigns(nodes = 4),
                today = today,
            ),
        )
    }

    @Test
    fun `свежие узлы и вчерашнее касание читаются как горение`() {
        val pulse = pulseOf(touches = listOf(day(9, 7)), through = through)
        val signs = ThreadSigns(nodes = 6, fresh = 3, ties = 4, branches = 1)

        assertEquals(
            ThreadState.BURNING,
            suggestState(ThreadState.GROWING, pulse, signs, today),
        )
    }

    @Test
    fun `ветвление без свежего движения читается как плетение`() {
        val pulse = pulseOf(touches = listOf(day(8, 25)), through = through)
        val signs = ThreadSigns(nodes = 9, fresh = 0, ties = 5, branches = 3)

        assertEquals(
            ThreadState.WEAVING,
            suggestState(ThreadState.GROWING, pulse, signs, today),
        )
    }

    @Test
    fun `догадка не повторяет нынешнее состояние`() {
        val pulse = pulseOf(touches = listOf(day(7, 1)), through = through)

        assertNull(
            suggestState(ThreadState.SMOULDERING, pulse, ThreadSigns(nodes = 3), today),
        )
    }

    // ---- Победы ----

    @Test
    fun `победами становятся сделанные шаги, открытия и повороты`() {
        val nodes = listOf(
            fact(1, ThreadNodeKind.STEP, made = day(9, 1), done = day(9, 2)),
            fact(2, ThreadNodeKind.STEP, made = day(9, 1)),
            fact(3, ThreadNodeKind.INSIGHT, made = day(9, 3)),
            fact(4, ThreadNodeKind.TURN, made = day(9, 4)),
            fact(5, ThreadNodeKind.THOUGHT, made = day(9, 5)),
            fact(6, ThreadNodeKind.RESULT, made = day(8, 1), done = day(9, 6)),
        )

        val wins = winsOf(nodes)

        // Несделанный шаг и мысль победами не считаются: мысль не «делают».
        assertEquals(listOf(6L, 4L, 3L, 1L), wins.map { it.nodeId })
        assertEquals(WinWeight.SMALL, wins.first { it.nodeId == 1L }.weight)
        assertEquals(WinWeight.REAL, wins.first { it.nodeId == 3L }.weight)
        assertEquals(WinWeight.BIG, wins.first { it.nodeId == 4L }.weight)
        assertEquals(WinWeight.BIG, wins.first { it.nodeId == 6L }.weight)
    }

    @Test
    fun `путь, выросший из подводного камня, весит больше обычного`() {
        val kinds = mapOf(
            1L to ThreadNodeKind.SNAG,
            2L to ThreadNodeKind.PATH,
            3L to ThreadNodeKind.PATH,
            4L to ThreadNodeKind.RESULT,
        )
        val ties = listOf(Tie(1, 2), Tie(4, 3))

        val around = snagWays(kinds, ties)
        assertEquals(setOf(2L), around)

        val wins = winsOf(
            nodes = listOf(
                fact(2, ThreadNodeKind.PATH, made = day(9, 2)),
                fact(3, ThreadNodeKind.PATH, made = day(9, 3)),
            ),
            tiedToSnag = around,
        )

        // Обычный путь победой не считается вовсе — победа это обход камня.
        assertEquals(listOf(2L), wins.map { it.nodeId })
        assertEquals(WinWeight.REAL, wins.single().weight)
    }

    @Test
    fun `победы идут свежими сверху`() {
        val wins = winsOf(
            listOf(
                fact(1, ThreadNodeKind.INSIGHT, made = day(6, 1)),
                fact(2, ThreadNodeKind.INSIGHT, made = day(9, 1)),
                fact(3, ThreadNodeKind.INSIGHT, made = day(7, 1)),
            ),
        )

        assertEquals(listOf(2L, 3L, 1L), wins.map { it.nodeId })
    }

    private fun fact(
        id: Long,
        kind: ThreadNodeKind,
        made: LocalDate,
        done: LocalDate? = null,
    ) = ThreadNodeFact(id = id, kind = kind, title = kind.title, madeOn = made, doneOn = done)
}
