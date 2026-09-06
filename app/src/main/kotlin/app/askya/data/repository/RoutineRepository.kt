package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.RoutineItem
import app.askya.data.entity.ScheduleItem
import app.askya.domain.plan.sameDeed
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Список дел — то, из чего собирается день, — и перенос отобранного в дату.
 *
 * База нужна здесь целиком, а не один DAO: перенос в день трогает две таблицы,
 * и без общей транзакции возможен день, помеченный как заполненный, но пустой.
 */
class RoutineRepository(private val db: AppDatabase) {

    private val routineDao = db.routineDao()
    private val scheduleDao = db.scheduleDao()
    private val deedTaskDao = db.deedTaskDao()

    fun items(): Flow<List<RoutineItem>> = routineDao.observeAll()

    suspend fun get(id: Long): RoutineItem? = routineDao.getById(id)

    suspend fun add(item: RoutineItem): Long = routineDao.insert(item)

    suspend fun save(item: RoutineItem) = routineDao.update(item)

    suspend fun delete(id: Long) = routineDao.deleteById(id)

    /**
     * Кладёт в день названные дела списка — те, что человек выбрал сам.
     *
     * Не то же, что сборка дня ([app.askya.data.repository.DayRepository]): там
     * список разворачивается целиком и одинаковые дела отсеиваются, здесь —
     * берётся отобранное по одному, ровно то и ровно столько. Выключенное в
     * списке дело сюда тоже попадает, если его выбрали: переключатель говорит,
     * чего не разворачивать самой, а не что запрещено брать руками.
     *
     * День помечается заполненным: иначе распорядок развернулся бы в него
     * целиком при следующем открытии — поверх того, что человек только что
     * отобрал вручную.
     */
    suspend fun addToDay(date: LocalDate, items: List<RoutineItem>) = db.withTransaction {
        if (items.isEmpty()) return@withTransaction
        items.forEach { item ->
            scheduleDao.insert(
                ScheduleItem(
                    date = date,
                    startTime = item.startTime,
                    endTime = item.endTime,
                    title = item.title,
                    icon = item.icon,
                )
            )
        }
        routineDao.markGenerated(GeneratedDay(date))
    }

    /**
     * Доводит правку списка до уже собранных дней — начиная с сегодняшнего.
     *
     * ## Зачем
     *
     * Список разворачивается в день один раз — когда день открывают впервые
     * ([DayRepository.ensureComposed]). Дальше день живёт сам, и заведённое
     * после этого дело в него уже не попадало: человек записывал «Зарядка в
     * семь», возвращался в сегодняшний день и не находил её там. Правило,
     * которое начинает действовать завтра, выглядит поломкой, а не правилом.
     *
     * Теперь сохранённое дело встаёт туда, где по правилу должно стоять, и
     * уходит оттуда, где стоять перестало: сняли вторник — во вторник его
     * больше нет, выключили переключателем — нет нигде впереди. Одно правило в
     * обе стороны: иначе тот же самый переключатель, который дело поставил,
     * убрать его уже не может.
     *
     * ## Чего это не трогает
     *
     * **Прошлых дней.** Расписание на позавчера — запись о том, что было, и
     * дописывать её задним числом значило бы врать.
     *
     * **Сделанного.** Отмеченное дело — тоже запись о случившемся. Поэтому же
     * дело, уже сделанное сегодня, не ставится заново после правки: человек
     * его сделал, а не отложил.
     *
     * **Дела со своим списком задач.** В нём написанное руками, и подменять
     * его правилом нельзя — то же исключение, что у пересборки
     * ([app.askya.data.db.dao.ScheduleDao.deleteUndoneOn]).
     *
     * **Поправленного руками в самом дне.** Такое дело перестаёт совпадать с
     * делом списка по названию и часу, а значит, выходит из-под правила само
     * ([sameDeed]) — здесь для этого ничего делать не нужно.
     *
     * **Дней, ещё не собранных.** Их незачем трогать: они соберутся из списка
     * сами, когда их откроют, — уже с этим делом.
     *
     * Удаление дела из списка сюда не заходит и остаётся при своём: убрать
     * правило на будущее и стереть то, что уже стоит в дне, — разные просьбы
     * (см. `AskyaDayViewModel.removeFromRoutine`).
     *
     * [before] — каким дело было до правки: по нему в дне находится уже
     * стоящая копия, которую надо подтянуть к новому виду, а не поставить
     * второй такой же. У нового дела его нет.
     *
     * Отвечает следом, оставленным в днях ([DeedTrace]): у дела в дне бывает
     * напоминание, а снять его или подвинуть вместе с будильником может только
     * тот, у кого есть Context, — репозиторий про будильники не знает (см.
     * [app.askya.reminders.dropReminders], [app.askya.reminders.moveReminder]).
     */
    suspend fun applyToDays(item: RoutineItem, before: RoutineItem? = null): DeedTrace = db.withTransaction {
        val taken = mutableListOf<Long>()
        val moved = mutableListOf<DeedMove>()
        val today = LocalDate.now()
        // Сегодня — всегда, даже если день ещё не помечен собранным: человек
        // смотрит именно в него, и дело нужно ему там сейчас.
        val dates = (listOf(today) + routineDao.generatedFrom(today)).distinct()

        dates.forEach { date ->
            val standing = scheduleDao.itemsOn(date).filter { block ->
                sameDeed(item, block) || (before != null && sameDeed(before, block))
            }
            val belongs = item.enabled && item.on(date)

            when {
                !belongs -> {
                    val busy = deedTaskDao.deedsWithTasksOn(date).toSet()
                    standing
                        .filterNot { it.done || it.id in busy }
                        // Мягко, как и всё убранное в Askya: строка живёт сутки
                        // и уходит сама.
                        .forEach {
                            scheduleDao.setRemoved(it.id, LocalDateTime.now())
                            taken += it.id
                        }
                }

                // Пусто — значит, дела в этом дне ещё нет. Если в нём лежит
                // сделанная копия, сюда не попадём вовсе: дело за этот день
                // уже сделано, и ставить его заново нечего.
                standing.isEmpty() -> scheduleDao.insert(
                    ScheduleItem(
                        date = date,
                        startTime = item.startTime,
                        endTime = item.endTime,
                        title = item.title,
                        icon = item.icon,
                    ),
                )

                else -> standing.filterNot { it.done }.forEach { block ->
                    // Переехавший час запоминается: напоминание о деле стоит
                    // «за пятнадцать минут до», и оставить его на прежнем часе
                    // значило бы звонить о деле, которого в это время уже нет.
                    if (block.startTime != item.startTime || block.endTime != item.endTime) {
                        moved += DeedMove(block.id, date)
                    }
                    scheduleDao.update(
                        block.copy(
                            title = item.title,
                            startTime = item.startTime,
                            endTime = item.endTime,
                            icon = item.icon,
                        ),
                    )
                }
            }
        }

        DeedTrace(taken = taken, moved = moved)
    }
}

/**
 * След, оставленный правкой списка в уже собранных днях.
 *
 * Нужен не самому дню — он уже поправлен, — а будильникам: репозиторий их не
 * заводит и завести не может, а тому, кто может, надо знать, о каких делах
 * речь. [taken] — убранные, их напоминания снимаются; [moved] — переехавшие на
 * другой час, их напоминания едут следом.
 */
data class DeedTrace(
    val taken: List<Long> = emptyList(),
    val moved: List<DeedMove> = emptyList(),
)

/** Дело дня, переехавшее вслед за правкой списка: где оно и в каком дне. */
data class DeedMove(val itemId: Long, val date: LocalDate)
