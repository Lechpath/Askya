package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.ScheduleItem
import app.askya.domain.plan.DayComposer
import app.askya.domain.plan.DayLayout
import app.askya.domain.plan.DayRequest
import java.time.LocalDate

/**
 * Пересборка конкретного дня: собрать всё, что о дне известно, попросить
 * разложить и записать результат.
 *
 * Отдельно от [RoutineRepository], потому что задачи разные. Тот разворачивает
 * шаблон обычного дня — механическое копирование. Этот собирает день из списка
 * дел с оглядкой на то, что в дне уже стоит.
 */
class DayRepository(
    private val db: AppDatabase,
    private val composer: DayComposer,
) {

    private val scheduleDao = db.scheduleDao()
    private val routineDao = db.routineDao()
    private val checkInDao = db.checkInDao()

    /**
     * Собирает день заново и записывает результат.
     *
     * Возвращает [DayLayout], а не Unit: экран показывает, кто собрал день и
     * что Askya про него сказала, — без этого пересборка выглядела бы как
     * список, который сам собой перетасовался.
     */
    suspend fun recompose(date: LocalDate): DayLayout {
        val request = DayRequest(
            date = date,
            routine = routineDao.enabled(),
            checkIn = checkInDao.latestOn(date),
            existing = scheduleDao.itemsOn(date),
        )

        val layout = composer.compose(request)

        db.withTransaction {
            // Сделанное переживает пересборку. День — это ещё и запись о том,
            // что уже произошло, и планом её переписывать нельзя.
            scheduleDao.deleteUndoneOn(date)
            val done = scheduleDao.itemsOn(date)
            layout.items
                // Дело, которое человек уже сделал, второй раз не ставится:
                // модель о нём знала и могла честно оставить его в плане.
                .filterNot { planned ->
                    done.any { it.title.equals(planned.title, ignoreCase = true) }
                }
                .forEach { planned ->
                    scheduleDao.insert(
                        ScheduleItem(
                            date = date,
                            startTime = planned.startTime,
                            endTime = planned.endTime,
                            title = planned.title,
                            note = planned.note,
                        )
                    )
                }
            // День помечается собранным, чтобы автоматическое разворачивание
            // распорядка не добавило к нему шаблон сверху.
            routineDao.markGenerated(GeneratedDay(date))
        }

        return layout
    }

    /**
     * Собрать несколько дней вперёд, начиная с [from].
     *
     * Дни считаются по одному и подряд: каждый следующий запрос уже видит
     * записанный предыдущий, поэтому модель не ставит одно и то же дело три
     * дня подряд, не зная об этом. Одним запросом на три дня получился бы
     * общий шаблон вместо трёх разных дней.
     *
     * Первый же сбой останавливает работу: собранные дни остаются, а молча
     * недосчитать день хуже, чем сказать, что дальше не вышло.
     */
    suspend fun composeAhead(from: LocalDate, days: Int): List<DayLayout> =
        (0 until days).map { offset -> recompose(from.plusDays(offset.toLong())) }
}
