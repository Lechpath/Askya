package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.ScheduleItem
import app.askya.domain.plan.DayComposer
import app.askya.domain.plan.DayLayout
import app.askya.domain.plan.DayRequest
import app.askya.domain.plan.sameDeed
import app.askya.domain.model.Priority
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
        // Дела этого дня недели, а не все подряд: список, в котором на среду
        // нет ничего, средой и не заполняется — иначе среда навсегда осталась
        // бы помеченной и пустой.
        if (routineDao.enabled().none { it.on(date) }) return
        recompose(date)
    }

    /**
     * Ставит в день важные дела списка — те, что помечены «Важно».
     *
     * ## Зачем
     *
     * Список разворачивается в день один раз — при первом его открытии
     * ([ensureComposed]), — и дальше день живёт сам. Для обычного дела это
     * правильно: день не должен переписываться под правило задним числом.
     * Но с важным делом выходило иначе. Собрали день утром, а вечером человек
     * завёл в списке «Позвонить в банк» и пометил важным — и в сегодняшний
     * день оно попадало только через «Взять из списка дел», окно, которое
     * приходилось открывать каждое утро заново. Важное дело, о котором надо
     * помнить отдельно, — это ровно то дело, которое не должно требовать
     * ежедневного выбора.
     *
     * Поэтому важное дело встаёт в день само и при каждом открытии: не только
     * в тот день, который ещё не собирали, а в любой сегодняшний и будущий,
     * где его нет.
     *
     * ## Чего это не делает
     *
     * **Не трогает прошлого.** Расписание на позавчера — запись о том, что
     * было, и дописывать её задним числом значило бы врать.
     *
     * **Не спорит с человеком.** Дело, убранное из этого дня, обратно не
     * встаёт: корзина видна ([ScheduleDao.allOn]), и убранное значит «сегодня
     * не надо». Иначе вычеркнуть важное дело из одного дня стало бы нельзя.
     *
     * **Не ставит дважды.** Признак тот же, что везде: совпали название и час
     * — дело то же самое ([sameDeed]). Поправленное руками в самом дне из-под
     * правила выходит само.
     *
     * **Не разворачивает список.** Важные дела и только они: всё остальное
     * по-прежнему приходит сборкой.
     *
     * **Не помечает день собранным.** День, в который важное дело встало
     * до сборки, соберётся своим чередом — и уже с ним.
     */
    suspend fun ensureImportant(date: LocalDate) {
        if (date.isBefore(LocalDate.now())) return

        val important = routineDao.enabled()
            .filter { it.priority == Priority.HIGH && it.on(date) }
        if (important.isEmpty()) return

        db.withTransaction {
            val standing = scheduleDao.allOn(date)
            important
                .filterNot { deed -> standing.any { sameDeed(deed, it) } }
                .forEach { deed ->
                    scheduleDao.insert(
                        ScheduleItem(
                            date = date,
                            startTime = deed.startTime,
                            endTime = deed.endTime,
                            title = deed.title,
                            icon = deed.icon,
                        ),
                    )
                }
        }
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
            // Повторяющееся дело попадает только в свои дни недели: «Пн Ср
            // Пт» на вторник не разворачивается (см. [DeedDays]).
            routine = routineDao.enabled().filter { it.on(date) },
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
