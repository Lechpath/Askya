package app.askya.data.repository

import androidx.room.withTransaction
import app.askya.data.db.AppDatabase
import app.askya.data.db.dao.ThreadMoney
import app.askya.data.db.dao.ThreadTask
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ThreadEdge
import app.askya.data.entity.ThreadItem
import app.askya.data.entity.ThreadNode
import app.askya.data.entity.YetList
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import app.askya.domain.model.ListMark
import app.askya.domain.model.ThreadNodeFact
import app.askya.domain.model.ThreadNodeKind
import app.askya.domain.model.ThreadPulse
import app.askya.domain.model.ThreadSigns
import app.askya.domain.model.ThreadState
import app.askya.domain.model.ThreadWin
import app.askya.domain.model.Tie
import app.askya.domain.model.placeNear
import app.askya.domain.model.pulseOf
import app.askya.domain.model.signsOf
import app.askya.domain.model.snagWays
import app.askya.domain.model.winsOf
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
        // Строки и дела сведены в пару заранее: `combine` берёт пять потоков,
        // а их шесть. Пара честнее шестого combine — оба источника отвечают на
        // один и тот же вопрос «что дальше».
        combine(threads.observeNextLines(), threads.observeNextDeeds()) { l, d -> l to d },
        threads.observeNodeMarks(),
        threads.observeEdgeMarks(),
    ) { all, touches, next, marks, ties ->
        val byThread = touches.groupBy { it.threadId }
        // Первая незакрытая строка нити: запросы отдали их по порядку, и
        // первая встреченная — она и есть.
        val byLine = next.first.groupBy { it.threadId }
        val byDeed = next.second.groupBy { it.threadId }
        val byNode = marks.groupBy { it.threadId }
        val byTie = ties.groupBy { it.threadId }
        val now = YearMonth.now()
        val today = LocalDate.now()

        all.map { thread ->
            val nodes = byNode[thread.id].orEmpty()
            val edges = byTie[thread.id].orEmpty()
            ThreadRow(
                thread = thread,
                pulse = pulseOf(
                    touches = byThread[thread.id].orEmpty().map { it.day },
                    through = now,
                ),
                // Несделанный шаг карты идёт первым: он и есть замысел,
                // доведённый до дела, а строка списка и дело дня — то, что уже
                // ушло в другие разделы. Дальше прежний порядок: строка
                // списка, потом дело, — тот же, что в раскрытой карточке.
                next = nodes.firstOrNull { it.kind == ThreadNodeKind.STEP && it.doneOn == null }
                    ?.title
                    ?: (byLine[thread.id]?.firstOrNull() ?: byDeed[thread.id]?.firstOrNull())
                        ?.text.orEmpty(),
                signs = signsOf(
                    made = nodes.map { it.createdAt.toLocalDate() },
                    ties = edges.map { Tie(it.fromId, it.toId) },
                    today = today,
                ),
                wins = winsOf(
                    nodes = nodes.map { mark ->
                        ThreadNodeFact(
                            id = mark.id,
                            kind = mark.kind,
                            title = mark.title,
                            madeOn = mark.createdAt.toLocalDate(),
                            doneOn = mark.doneOn,
                        )
                    },
                    tiedToSnag = snagWays(
                        kinds = nodes.associate { it.id to it.kind },
                        ties = edges.map { Tie(it.fromId, it.toId) },
                    ),
                ),
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
     * Сменить состояние нити — любое на любое.
     *
     * Одним действием, а не «закрыть» и «оживить» порознь: состояний семь, и
     * переходить между ними можно в любую сторону. Нить, которая тлела и вдруг
     * загорелась, не «оживает из закрытых» — она просто горит.
     *
     * День закрытия записывается только при закрытии: «Завершена 14 июня»
     * человек читает как запись о случившемся. Открыли обратно — день
     * стирается, потому что он стал бы врать.
     */
    suspend fun setState(thread: ThreadItem, state: ThreadState) {
        threads.update(
            thread.copy(
                state = state,
                closedAt = if (state.closed) thread.closedAt ?: LocalDateTime.now() else null,
            ),
        )
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

    // ---- Карта замысла ----

    /**
     * Карта одной нити: узлы, связи и дела, поставленные из шагов.
     *
     * Дела здесь не для показа списком — они уже показаны в раскрытой карточке.
     * Они нужны, чтобы шаг на карте знал про свою отметку: человек, отметивший
     * дело утром в дне, вечером открывает карту и должен увидеть шаг сделанным,
     * а не гадать, почему приложение помнит про это дважды.
     */
    fun web(id: Long): Flow<ThreadWeb> {
        val link = DeedLink(LinkKind.THREAD, id).store()
        return combine(
            threads.observeNodes(id),
            threads.observeEdges(id),
            threads.observeDeeds(link),
        ) { nodes, edges, deeds ->
            ThreadWeb(
                nodes = nodes,
                edges = edges,
                deeds = deeds.associateBy { it.id },
            )
        }
    }

    /**
     * Завести узел — сам по себе или выросшим из другого.
     *
     * Место ищется здесь, а не в разметке: разметка знает, где палец, а куда
     * узел встанет по-человечески, знает счёт ([placeNear]). Выросший
     * привязывается к родителю той же транзакцией — узел, появившийся без
     * связи, читался бы как отдельная мысль, а он ответ на соседнюю.
     */
    suspend fun addNode(
        threadId: Long,
        kind: ThreadNodeKind,
        title: String,
        note: String = "",
        from: ThreadNode? = null,
    ): Long = db.withTransaction {
        val spot = placeNear(parent = from?.spot, taken = threads.spotsOf(threadId))

        val id = threads.insertNode(
            ThreadNode(
                threadId = threadId,
                kind = kind,
                title = title.trim(),
                note = note.trim(),
                x = spot.x,
                y = spot.y,
            ),
        )
        if (from != null) {
            threads.insertEdge(ThreadEdge(threadId = threadId, fromId = from.id, toId = id))
        }
        id
    }

    suspend fun saveNode(node: ThreadNode) {
        threads.updateNode(node.copy(title = node.title.trim(), note = node.note.trim()))
    }

    /** Передвинуть узел. Зовётся, когда карточку отпустили, а не пока тянут. */
    suspend fun moveNode(id: Long, x: Float, y: Float) = threads.moveNode(id, x, y)

    /**
     * Стереть узел вместе с его связями.
     *
     * Узел стирается совсем, а не «бросается», как нить: это одна карточка, а
     * не месяцы жизни. Связи уходят с ним — линия, ведущая в никуда, хуже
     * отсутствия линии.
     */
    suspend fun deleteNode(id: Long) = db.withTransaction {
        threads.deleteEdgesOf(id)
        threads.deleteNode(id)
    }

    /**
     * Связать два узла. Отвечает тем, появилась ли связь.
     *
     * Сам с собой и дважды — нельзя: первое бессмысленно, второе нарисовало бы
     * вторую линию поверх первой.
     */
    suspend fun tie(threadId: Long, from: Long, to: Long): Boolean = db.withTransaction {
        if (from == to || from <= 0 || to <= 0) return@withTransaction false
        if (threads.tied(from, to) > 0) return@withTransaction false
        threads.insertEdge(ThreadEdge(threadId = threadId, fromId = from, toId = to))
        true
    }

    suspend fun untie(edgeId: Long) = threads.deleteEdge(edgeId)

    /**
     * Отметить шаг сделанным — или снять отметку.
     *
     * Если из шага было поставлено дело, отметка ставится и ему: иначе Askya
     * знала бы про одно и то же событие два разных ответа. Обратное неверно и
     * не нужно — дело, отмеченное в дне, узел видит и так ([ThreadWeb.done]).
     */
    suspend fun markNode(node: ThreadNode, done: Boolean) = db.withTransaction {
        threads.updateNode(node.copy(doneAt = if (done) LocalDateTime.now() else null))
        node.deedId?.let { schedule.setDone(it, done) }
    }

    /**
     * Поставить шаг делом в день — из самой карты, не уходя из неё.
     *
     * Дело ложится в расписание обычной строкой с привязкой к нити, а узел
     * запоминает его номер: второй раз то же дело из того же шага не заведётся,
     * и отметка у них будет одна на двоих.
     */
    suspend fun stepToDeed(node: ThreadNode, date: LocalDate = LocalDate.now()): Long =
        db.withTransaction {
            if (node.deedId != null) return@withTransaction node.deedId
            val id = addDeed(threadId = node.threadId, title = node.title, date = date)
            if (id > 0) threads.updateNode(node.copy(deedId = id))
            id
        }

    /** Дописать шаг строкой в список нити — второй его адрес, «когда-нибудь». */
    suspend fun stepToLine(thread: ThreadItem, node: ThreadNode) = addLine(thread, node.title)

    /** Привязать узел к тому, что уже лежит в Askya, — или снять привязку. */
    suspend fun linkNode(node: ThreadNode, link: String?) {
        threads.updateNode(node.copy(link = link))
    }
}

/**
 * Нить в ленте раздела: она сама, её пульс, ближайший шаг и то, что видно по
 * её карте.
 */
data class ThreadRow(
    val thread: ThreadItem,
    val pulse: ThreadPulse = ThreadPulse(),
    /** Ближайшее несделанное: шаг карты, строка списка или дело. */
    val next: String = "",
    /** Признаки по карте: сколько узлов, сколько свежих, как они связаны. */
    val signs: ThreadSigns = ThreadSigns(),
    /** Победы нити, свежие сверху. Не очки — память о том, как она двигалась. */
    val wins: List<ThreadWin> = emptyList(),
)

/**
 * Карта замысла: узлы, связи и дела, поставленные из шагов.
 *
 * Считанного не хранит: победы и признаки складываются из тех же узлов при
 * каждом взгляде — то же правило, по которому у нити нет колонки «последнее
 * касание», а у счёта в книге нет колонки остатка.
 */
data class ThreadWeb(
    val nodes: List<ThreadNode> = emptyList(),
    val edges: List<ThreadEdge> = emptyList(),
    /** Дела шагов по номеру дела. */
    val deeds: Map<Long, ScheduleItem> = emptyMap(),
) {
    val empty: Boolean get() = nodes.isEmpty()

    fun node(id: Long): ThreadNode? = nodes.firstOrNull { it.id == id }

    /** Дело, поставленное из этого шага. `null` — не ставили. */
    fun deedOf(node: ThreadNode): ScheduleItem? = node.deedId?.let { deeds[it] }

    /**
     * Когда узел сделан: своей отметкой или отметкой его дела.
     *
     * Та же пара источников, что в ленте ([app.askya.data.db.dao.ThreadNodeMark]),
     * и по той же причине: отметить шаг можно и на карте, и утром в дне.
     */
    fun doneOn(node: ThreadNode): LocalDate? = node.doneAt?.toLocalDate()
        ?: deedOf(node)?.takeIf { it.done }?.date

    fun done(node: ThreadNode): Boolean = doneOn(node) != null

    private val ties: List<Tie> get() = edges.map { Tie(it.fromId, it.toId) }

    /** С кем этот узел связан — в обе стороны: линия не имеет «своей» стороны. */
    fun tiedTo(id: Long): List<Long> = edges.mapNotNull { edge ->
        when (id) {
            edge.fromId -> edge.toId
            edge.toId -> edge.fromId
            else -> null
        }
    }

    /** Связь между этими двумя, если она есть, — её и снимают. */
    fun edgeBetween(a: Long, b: Long): ThreadEdge? = edges.firstOrNull {
        (it.fromId == a && it.toId == b) || (it.fromId == b && it.toId == a)
    }

    fun signs(today: LocalDate): ThreadSigns =
        signsOf(nodes.map { it.createdAt.toLocalDate() }, ties, today)

    val wins: List<ThreadWin>
        get() = winsOf(
            nodes = nodes.map { node ->
                ThreadNodeFact(
                    id = node.id,
                    kind = node.kind,
                    title = node.title,
                    madeOn = node.createdAt.toLocalDate(),
                    doneOn = doneOn(node),
                )
            },
            tiedToSnag = snagWays(nodes.associate { it.id to it.kind }, ties),
        )
}

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
