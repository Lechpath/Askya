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
 * Сборка конкретного дня: собрать всё, что о дне известно, попросить разложить
 * и записать результат.
 *
 * Единственное место, где день собирается. Отсюда работают оба случая, которые
 * прежде жили порознь: нажатие на цветок ([recompose]) и разворачивание списка
 * в только что открытый день ([ensureComposed]). Разными они были ровно до тех
 * пор, пока день собирала модель; сейчас это одно действие, и делать его надо
 * одинаково — иначе в дне, заполненном самом собой, оказывается не то же
 * самое, что в собранном по просьбе.
 *
 * [RoutineRepository] остался при своём: он ведёт сам список дел и кладёт в
 * день отобранное по одному. Разворачивать список целиком он больше не умеет.
 */
class DayRepository(
    private val db: AppDatabase,
    private val composer: DayComposer,
) {

    private val scheduleDao = db.scheduleDao()
    private val routineDao = db.routineDao()

    /**
     * Разворачивает список дел в день при первом его открытии.
     *
     * Тем же сборщиком, что и цветок в шапке: «собрать» — одно действие, и
     * неважно, попросили о нём пальцем или оно случилось само. Раньше здесь
     * была вторая, своя дорога (`RoutineRepository.ensureGenerated`), и день,
     * заполненный сам, отличался от собранного по нажатию — например, тем,
     * что одинаковые дела списка попадали в него дважды.
     *
     * Прошлые дни не заполняются: расписание на позавчера — это запись о том,
     * что было, и дорисовывать её задним числом значило бы врать.
     *
     * Пустой список делом не считается: иначе день, открытый до того, как
     * список завели, остался бы помеченным и пустым навсегда.
     */
    suspend fun ensureComposed(date: LocalDate) {
        if (date.isBefore(LocalDate.now())) return
        if (routineDao.isGenerated(date) > 0) return
        if (routineDao.enabled().isEmpty()) return
        recompose(date)
    }

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
            existing = scheduleDao.itemsOn(date),
        )

        val layout = composer.compose(request)

        db.withTransaction {
            // Сделанное переживает пересборку, и вместе с ним — дело со своим
            // списком. День это ещё и запись о том, что уже произошло, и
            // планом её переписывать нельзя; список задач внутри дела — такая
            // же запись, только незаконченная (см. [ScheduleDao.deleteUndoneOn]).
            scheduleDao.deleteUndoneOn(date)
            val kept = scheduleDao.itemsOn(date)
            layout.items
                // Дело, которое уже стоит в дне, второй раз не ставится:
                // сборщик о нём знал и мог честно оставить его в плане.
                .filterNot { planned ->
                    kept.any { it.title.equals(planned.title, ignoreCase = true) }
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
}
