package app.askya.domain.plan

import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Сборка дня. Проверяется одно правило и его края: из дел, которые человек
 * завёл дважды, в день встаёт то, что раньше.
 */
class RoutineDayComposerTest {

    private val composer = RoutineDayComposer()

    private fun deed(title: String, hour: Int, minute: Int = 0) =
        RoutineItem(title = title, startTime = LocalTime.of(hour, minute))

    private suspend fun compose(
        routine: List<RoutineItem>,
        existing: List<ScheduleItem> = emptyList(),
    ) = composer.compose(
        DayRequest(
            date = LocalDate.of(2026, 8, 24),
            routine = routine,
            existing = existing,
        )
    ).items

    @Test
    fun `одинаковые по названию сводятся к самому раннему`() = runTest {
        val items = compose(
            listOf(
                deed("Зарядка", 8, 30),
                deed("Завтрак", 9),
                deed("Зарядка", 7),
            )
        )

        assertEquals(listOf("Зарядка", "Завтрак"), items.map { it.title })
        assertEquals(LocalTime.of(7, 0), items.first().startTime)
    }

    @Test
    fun `регистр и пробелы не делают дело другим`() = runTest {
        val items = compose(listOf(deed(" зарядка ", 9), deed("Зарядка", 7)))

        assertEquals(1, items.size)
        assertEquals(LocalTime.of(7, 0), items.first().startTime)
    }

    @Test
    fun `одинаковое описание сводит дела так же, как одинаковое название`() = runTest {
        val items = compose(
            routine = emptyList(),
            existing = listOf(
                ScheduleItem(
                    date = LocalDate.of(2026, 8, 24),
                    startTime = LocalTime.of(19, 0),
                    title = "Бег",
                    note = "пять километров вдоль реки",
                ),
                ScheduleItem(
                    date = LocalDate.of(2026, 8, 24),
                    startTime = LocalTime.of(7, 30),
                    title = "Бег 5 км",
                    note = "пять километров вдоль реки",
                ),
            ),
        )

        assertEquals(1, items.size)
        assertEquals(LocalTime.of(7, 30), items.first().startTime)
    }

    @Test
    fun `пустые описания друг друга не отсеивают`() = runTest {
        val items = compose(listOf(deed("Зарядка", 7), deed("Завтрак", 9)))

        assertEquals(2, items.size)
    }

    @Test
    fun `записанное руками остаётся рядом со списком и день идёт по времени`() = runTest {
        val items = compose(
            routine = listOf(deed("Зарядка", 7), deed("Ужин", 19)),
            existing = listOf(
                ScheduleItem(
                    date = LocalDate.of(2026, 8, 24),
                    startTime = LocalTime.of(13, 0),
                    title = "Врач",
                ),
            ),
        )

        assertEquals(listOf("Зарядка", "Врач", "Ужин"), items.map { it.title })
    }

    @Test
    fun `дело списка не задваивается тем, что уже стоит в дне`() = runTest {
        val items = compose(
            routine = listOf(deed("Зарядка", 7)),
            existing = listOf(
                ScheduleItem(
                    date = LocalDate.of(2026, 8, 24),
                    startTime = LocalTime.of(7, 0),
                    title = "зарядка",
                ),
            ),
        )

        assertEquals(1, items.size)
    }
}
