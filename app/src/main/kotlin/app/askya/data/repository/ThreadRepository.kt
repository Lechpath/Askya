package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.db.dao.ThreadMoney
import app.askya.data.db.dao.ThreadTask
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ThreadItem
import app.askya.data.entity.YetList
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import app.askya.domain.model.ListMark
import app.askya.domain.model.ThreadPulse
import app.askya.domain.model.ThreadState
import app.askya.domain.model.pulseOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * Нити: сама нить и всё, что к ней тянется из других разделов.
 *
 * ## Нить ничего не хранит и всё собирает
 *
 * У нити пять своих полей и ни одного посчитанного. Пульс, «что дальше», число
 * дел и потраченное складываются из чужих таблиц при каждом взгляде — по той
 * же причине, по которой в Ledger нигде не лежит остаток счёта: колонка
 * «последнее касание» стала бы вторым источником правды и разошлась бы с
 * записями в первый же раз, когда отметку с дела сняли.
 *
 * ## База целиком, а не один DAO
 *
 * Нить проходит через расписание, списки, книгу и записи, и завести дело «в
 * нить» значит написать строку в чужую таблицу. Один DAO тут не помог бы, а
 * общая транзакция нужна: строка, дописанная в список, который в ту же секунду
 * заводится, — это две записи, между которыми ничего не должно случиться.
 */
class ThreadRepository(private val db: AppDatabase) {

    private val threads = db.threadDao()
    private val schedule = db.scheduleDao()
    private val yet = db.yetDao()

    /**
     * Лента раздела: нить, её пульс и ближайший шаг.
     *
     * Четырьмя потоками, сведёнными в один: сами нити меняются редко, касания
     * — с каждой отметкой, а незакрытые строки и дела со своей скоростью.
     * Держать это одним запросом значило бы перечитывать названия нитей на
     * каждую вычеркнутую строку.
     *
     * Месяц полоски берётся у часов здесь, а не в счёте: сам счёт
     * ([pulseOf]) о «сегодня» не знает нарочно — иначе его нельзя было бы
     * проверить тестом.
     */
    fun threads(): Flow<List<ThreadRow>> = combine(
        threads.observeAll(),
        threads.observeTouches(),
        threads.observeNextLines(),
        threads.observeNextDeeds(),
    ) { all, touches, lines, deeds ->
        val byThread = touches.groupBy { it.threadId }
        // Первая незакрытая строка нити: запросы отдали их по порядку, и
        // первая встреченная — она и есть.
        val byLine = lines.groupBy { it.threadId }
        val byDeed = deeds.groupBy { it.threadId }
        val now = YearMonth.now()

        all.map { thread ->
            ThreadRow(
                thread = thread,
                pulse = pulseOf(
                    touches = byThread[thread.id].orEmpty().map { it.day },
                    through = now,
                ),
                // Строка списка вперёд дела — тот же порядок, что в раскрытой
                // карточке ([ThreadParts.next]): у нити без списка ближайший
                // шаг лежит в расписании, и лента должна говорить то же, что
                // карточка.
                next = (byLine[thread.id]?.firstOrNull() ?: byDeed[thread.id]?.firstOrNull())
                    ?.text.orEmpty(),
            )
        }
    }

    fun thread(id: Long): Flow<ThreadItem?> = threads.observe(id)

    /**
     * Из чего нить состоит — для раскрытой карточки.
     *
     * Пятью потоками: разделы живут порознь и меняются порознь, а карточка
     * показывает их вместе. Пустая нить отдаёт пустые части, а не `null`:
     * «дел нет» — такой же ответ, как «дел четыре».
     */
    fun parts(id: Long): Flow<ThreadParts> {
        val link = DeedLink(LinkKind.THREAD, id).store()
        return combine(
            threads.observeDeeds(link),
            threads.observeThreadLines(id),
            threads.observeMoney(id),
            threads.observeNoteCount(id),
            threads.observeLists(id),
        ) { deeds, lines, money, notes, lists ->
            ThreadParts(
                deeds = deeds,
                lines = lines,
                money = money,
                notes = notes,
                lists = lists,
            )
        }
    }

    suspend fun save(thread: ThreadItem): Long =
        if (thread.id == 0L) threads.insert(thread) else {
            threads.update(thread)
            thread.id
        }

    /**
     * Закрыть нить — довести или бросить.
     *
     * День закрытия записывается: «Закончена 14 июня» человек читает как
     * запись о случившемся. Открыть её обратно можно ([revive]) — передумать
     * не преступление, и день закрытия тогда стирается: он врал бы.
     */
    suspend fun close(thread: ThreadItem, state: ThreadState) {
        threads.update(thread.copy(state = state, closedAt = LocalDateTime.now()))
    }

    suspend fun revive(thread: ThreadItem, state: ThreadState = ThreadState.LIVE) {
        threads.update(thread.copy(state = state, closedAt = null))
    }

    /**
     * Стереть нить совсем. Отвечает тем, вышло ли.
     *
     * Нить, через которую что-то прошло, не стирается — на ней висят месяцы, в
     * которые она шла. Её бросают: «Брошена» это честный конец, а не
     * поражение, и история при нём остаётся. То же правило, что у счёта в
     * книге, и по той же причине.
     *
     * Пустую стирают молча: заведённая по ошибке нить без единой строки —
     * описка, а не запись о жизни.
     */
    suspend fun delete(id: Long): Boolean = db.withTransaction {
        val link = DeedLink(LinkKind.THREAD, id).store()
        if (threads.countAttached(link, id) > 0) return@withTransaction false

        // Привязки у пустой нити взяться неоткуда, но снимаются они всё равно:
        // привязка, показывающая в никуда, хуже её отсутствия.
        threads.unlinkDeeds(link)
        threads.unlinkRoutine(link)
        threads.unlinkLists(id)
        threads.unlinkEntries(id)
        threads.unlinkNotes(id)
        threads.deleteById(id)
        true
    }

    /**
     * Поставить дело этой нити в день — «занести в нужный раздел» одним
     * нажатием, не уходя из нити.
     *
     * Дело ложится в расписание обычной строкой, ничем не отличаясь от
     * записанного руками: у нити нет своих дел, у неё есть дела дня, которые
     * её тянут. Час — тот, что назвали; не назвали — девять утра, обычное
     * начало дня в Askya.
     */
    suspend fun addDeed(
        threadId: Long,
        title: String,
        date: LocalDate = LocalDate.now(),
        at: LocalTime = LocalTime.of(9, 0),
    ): Long {
        val clean = title.trim()
        if (clean.isEmpty()) return 0L
        return schedule.insert(
            ScheduleItem(
                date = date,
                startTime = at,
                title = clean,
                link = DeedLink(LinkKind.THREAD, threadId).store(),
            ),
        )
    }

    /**
     * Дописать строку в список нити.
     *
     * Списка ещё нет — он заводится здесь же и называется именем нити.
     * Спрашивать «в какой список?» у человека, у которого списков ноль, значит
     * задавать вопрос без ответа; а два списка на одну нить он заведёт сам,
     * когда они ему понадобятся.
     */
    suspend fun addLine(thread: ThreadItem, text: String) = db.withTransaction {
        val clean = text.trim()
        if (clean.isEmpty()) return@withTransaction

        val listId = threads.firstListOf(thread.id)
            ?: yet.insertList(
                YetList(
                    title = thread.title.ifBlank { "Нить" },
                    mark = ListMark.SQUARE,
                    threadId = thread.id,
                ),
            )
        yet.insertItem(
            app.askya.data.entity.YetItem(listId = listId, text = clean),
        )
        yet.touchList(listId, LocalDateTime.now())
    }
}

/** Нить в ленте раздела: она сама, её пульс и ближайший шаг. */
data class ThreadRow(
    val thread: ThreadItem,
    val pulse: ThreadPulse = ThreadPulse(),
    /** Первая незакрытая строка её списков. Пусто — шага не записано. */
    val next: String = "",
)

/**
 * Из чего нить состоит. Не её содержимое, а срезы чужих разделов — см.
 * рассуждение при [ThreadRepository].
 */
data class ThreadParts(
    val deeds: List<ScheduleItem> = emptyList(),
    val lines: List<ThreadTask> = emptyList(),
    val money: ThreadMoney = ThreadMoney(),
    val notes: Int = 0,
    val lists: List<YetList> = emptyList(),
) {
    val deedsDone: Int get() = deeds.count { it.done }
    val linesDone: Int get() = lines.count { it.done }

    /** Ближайший незакрытый шаг: строка списка, а если их нет — дело. */
    val next: String
        get() = lines.firstOrNull { !it.done }?.text
            ?: deeds.firstOrNull { !it.done }?.title.orEmpty()

    /** Есть ли к чему тянуться вообще — от этого зависит вид пустой карточки. */
    val empty: Boolean
        get() = deeds.isEmpty() && lines.isEmpty() && money.count == 0 && notes == 0
}
