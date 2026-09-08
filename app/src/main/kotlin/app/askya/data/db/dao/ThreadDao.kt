package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.LedgerEntry
import app.askya.data.entity.Note
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ThreadEdge
import app.askya.data.entity.ThreadItem
import app.askya.data.entity.ThreadNode
import app.askya.data.entity.YetList
import app.askya.domain.model.Spot
import app.askya.domain.model.ThreadNodeKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Нити и всё, что к ним тянется из других таблиц.
 *
 * Запросы здесь читают чужие таблицы — расписание, списки, книгу, записи, — и
 * это нарочно: нить не хранит своего содержимого, она его собирает. Отдельного
 * DAO на «то, что относится к нити» заводить незачем — вопрос один и тот же,
 * и задавать его надо в одном месте.
 *
 * Дело находится по привязке, а не по колонке: она лежит строкой «thread:12»
 * ([app.askya.domain.model.DeedLink]), и в SQL номер вырезается из неё —
 * `substr(link, 8)`, потому что «thread:» это семь букв. Так же, как месяц
 * вырезается из даты в статистике книги.
 */
@Dao
interface ThreadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(thread: ThreadItem): Long

    @Update
    suspend fun update(thread: ThreadItem)

    @Query("DELETE FROM threads WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Все нити: горящие первыми, дальше растущие и плетущиеся, потом
     * затихшие, и последними закрытые.
     *
     * Порядок задан перечислением состояний прямо в запросе, а не полем
     * `position`: нитей у человека три-четыре, и раскладывать их руками не
     * над чем. Внутри состояния — свежие сверху: заведённая вчера нить ещё
     * помнится, а прошлогоднюю ищут глазами реже.
     */
    @Query(
        """
        SELECT * FROM threads
         ORDER BY CASE state
                    WHEN 'BURNING' THEN 0
                    WHEN 'GROWING' THEN 1
                    WHEN 'WEAVING' THEN 2
                    WHEN 'SMOULDERING' THEN 3
                    WHEN 'SLEEPING' THEN 4
                    ELSE 5
                  END,
                  createdAt DESC
        """,
    )
    fun observeAll(): Flow<List<ThreadItem>>

    @Query("SELECT * FROM threads WHERE id = :id")
    fun observe(id: Long): Flow<ThreadItem?>

    @Query("SELECT * FROM threads WHERE id = :id")
    suspend fun get(id: Long): ThreadItem?

    // ---- Пульс ----

    /**
     * Дни, в которые нити двигались, — по всем нитям разом.
     *
     * Одним запросом на весь раздел, а не по запросу на нить: подписок было бы
     * столько же, сколько карточек, и каждая будила бы экран отдельно. То же
     * решение, что у остатков счетов в книге.
     *
     * Касанием считается день, в который по нити что-то **произошло**:
     *
     * - **отмеченное дело** — не поставленное, а сделанное: план это ещё не
     *   движение, и нить, которой каждый понедельник расписывают дела и ни
     *   одного не делают, живой не притворяется;
     * - **трата** — она случается в тот день, которым записана;
     * - **строка, дописанная в список** нити: собрать список — тоже работа;
     * - **правка записи** Scroll: замеры и черновик пишут руками;
     * - **узел карты** — заведённый или отмеченный сделанным: думать над
     *   замыслом это и есть работа по нити, и день, в который человек
     *   разложил на карте три пути, ничем не хуже дня, в который он что-то
     *   купил.
     *
     * Повторы не отсеиваются — их складывает [app.askya.domain.model.pulseOf],
     * и складывает по дням: день, в который отметили дело и записали трату,
     * это один день работы, а не два.
     */
    @Query(
        """
        SELECT threadId, day FROM (
            SELECT CAST(substr(link, 8) AS INTEGER) AS threadId, date AS day
              FROM schedule_items
             WHERE removedAt IS NULL AND done = 1 AND link LIKE 'thread:%'
            UNION ALL
            SELECT threadId, date AS day
              FROM ledger_entries
             WHERE removedAt IS NULL AND threadId IS NOT NULL
            UNION ALL
            SELECT l.threadId AS threadId, substr(i.createdAt, 1, 10) AS day
              FROM yet_items i JOIN yet_lists l ON l.id = i.listId
             WHERE i.removedAt IS NULL AND l.threadId IS NOT NULL
            UNION ALL
            SELECT threadId, substr(updatedAt, 1, 10) AS day
              FROM notes
             WHERE removedAt IS NULL AND threadId IS NOT NULL
            UNION ALL
            SELECT threadId, substr(createdAt, 1, 10) AS day FROM thread_nodes
            UNION ALL
            SELECT threadId, substr(doneAt, 1, 10) AS day
              FROM thread_nodes WHERE doneAt IS NOT NULL
        )
        WHERE threadId IS NOT NULL AND threadId > 0
        """,
    )
    fun observeTouches(): Flow<List<ThreadTouch>>

    /**
     * Незакрытые строки списков всех нитей — из них берётся «что дальше».
     *
     * Первой строкой, а не всеми: карточке нити в ленте нужен один ближайший
     * шаг. Отбирает первую сам репозиторий — «первая в группе» в SQLite
     * пишется оконной функцией, которой на старых сборках может не быть, а
     * строк тут сотни, и отобрать их в Kotlin дешевле, чем гадать о версии.
     */
    @Query(
        """
        SELECT l.threadId AS threadId, i.text AS text
          FROM yet_items i JOIN yet_lists l ON l.id = i.listId
         WHERE i.removedAt IS NULL AND i.done = 0 AND l.threadId IS NOT NULL
         ORDER BY i.createdAt, i.id
         LIMIT 600
        """,
    )
    fun observeNextLines(): Flow<List<ThreadLine>>

    /**
     * Незакрытые дела всех нитей — второй источник «что дальше».
     *
     * Нить бывает без списка вовсе: у неё расписаны дела, и ближайший шаг
     * лежит в расписании. Раскрытая карточка это учитывала, а лента нет, и
     * одна и та же нить говорила в ней и в карточке разное.
     *
     * Ближайшие сверху: то, что стоит на сегодня, ближе того, что на пятницу.
     */
    @Query(
        """
        SELECT CAST(substr(link, 8) AS INTEGER) AS threadId, title AS text
          FROM schedule_items
         WHERE removedAt IS NULL AND done = 0 AND link LIKE 'thread:%'
         ORDER BY date, startTime, id
         LIMIT 400
        """,
    )
    fun observeNextDeeds(): Flow<List<ThreadLine>>

    // ---- Из чего нить состоит ----

    @Query(
        "SELECT * FROM schedule_items WHERE removedAt IS NULL AND link = :link " +
            "ORDER BY date DESC, startTime",
    )
    fun observeDeeds(link: String): Flow<List<ScheduleItem>>

    @Query("SELECT * FROM yet_lists WHERE threadId = :threadId ORDER BY updatedAt DESC")
    fun observeLists(threadId: Long): Flow<List<YetList>>

    @Query(
        "SELECT * FROM ledger_entries WHERE removedAt IS NULL AND threadId = :threadId " +
            "ORDER BY date DESC, id DESC",
    )
    fun observeEntries(threadId: Long): Flow<List<LedgerEntry>>

    @Query(
        "SELECT * FROM notes WHERE removedAt IS NULL AND threadId = :threadId " +
            "ORDER BY updatedAt DESC",
    )
    fun observeNotes(threadId: Long): Flow<List<Note>>

    /** Строки списков одной нити — «что дальше» и счёт «6 из 19» в раскрытой. */
    @Query(
        """
        SELECT i.id AS id, i.text AS text, i.done AS done
          FROM yet_items i JOIN yet_lists l ON l.id = i.listId
         WHERE i.removedAt IS NULL AND l.threadId = :threadId
         ORDER BY i.done, i.createdAt, i.id
        """,
    )
    fun observeThreadLines(threadId: Long): Flow<List<ThreadTask>>

    /**
     * Деньги нити: сколько по ней прошло и сколькими записями.
     *
     * Возврат вычитается, как и везде в книге: вернули за купленное — этой
     * траты у нити как бы и не было (см. `EntryKind.BACK`). Перевод и доход в
     * смету не идут: смета отвечает на «сколько уже вложено», а не на «сколько
     * денег ходило».
     *
     * Только главная валюта: смета одно число, а сложить рубль с долларом
     * книге нечем — то же правило, что в итогах месяца
     * ([app.askya.domain.model.Currency]).
     */
    @Query(
        """
        SELECT COUNT(*) AS count,
               COALESCE(SUM(CASE kind WHEN 'SPEND' THEN amount
                                      WHEN 'BACK' THEN -amount
                                      ELSE 0 END), 0) AS spent
          FROM ledger_entries
         WHERE removedAt IS NULL AND threadId = :threadId
           AND accountId IN (SELECT id FROM ledger_accounts WHERE currency = 'RUB')
        """,
    )
    fun observeMoney(threadId: Long): Flow<ThreadMoney>

    @Query("SELECT COUNT(*) FROM notes WHERE removedAt IS NULL AND threadId = :threadId")
    fun observeNoteCount(threadId: Long): Flow<Int>

    /** Первый список нити — в него дописывается строка, заведённая из нити. */
    @Query("SELECT id FROM yet_lists WHERE threadId = :threadId ORDER BY createdAt LIMIT 1")
    suspend fun firstListOf(threadId: Long): Long?

    /**
     * Сколько всего к нити привязано — по всем разделам разом.
     *
     * Спрашивает стирание: нить, через которую что-то прошло, не стирается, а
     * бросается. Считаются и убранные в корзину строки: сутки они ещё могут
     * вернуться, и стереть нить у них из-под ног нельзя.
     */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM schedule_items WHERE link = :link)
             + (SELECT COUNT(*) FROM routine_items WHERE link = :link)
             + (SELECT COUNT(*) FROM yet_lists WHERE threadId = :threadId)
             + (SELECT COUNT(*) FROM ledger_entries WHERE threadId = :threadId)
             + (SELECT COUNT(*) FROM notes WHERE threadId = :threadId)
             + (SELECT COUNT(*) FROM thread_nodes WHERE threadId = :threadId)
        """,
    )
    suspend fun countAttached(link: String, threadId: Long): Int

    // ---- Уборка при закрытии нити ----

    /**
     * Отвязать всё от стёртой нити.
     *
     * Стирают нить редко и только пустую (см. `ThreadRepository.delete`), но
     * если стёрли — привязка, показывающая в никуда, хуже её отсутствия: дело
     * говорило бы «тянет нить», а нити нет.
     */
    @Query("UPDATE schedule_items SET link = NULL WHERE link = :link")
    suspend fun unlinkDeeds(link: String)

    @Query("UPDATE routine_items SET link = NULL WHERE link = :link")
    suspend fun unlinkRoutine(link: String)

    @Query("UPDATE yet_lists SET threadId = NULL WHERE threadId = :threadId")
    suspend fun unlinkLists(threadId: Long)

    @Query("UPDATE ledger_entries SET threadId = NULL WHERE threadId = :threadId")
    suspend fun unlinkEntries(threadId: Long)

    @Query("UPDATE notes SET threadId = NULL WHERE threadId = :threadId")
    suspend fun unlinkNotes(threadId: Long)

    // ---- Карта замысла ----

    /**
     * Узлы одной нити — все разом, без разбора на видимые и нет.
     *
     * Карта не листается страницами: её отодвигают и приближают целиком, и
     * узел, не попавший в запрос, оказался бы дыркой в замысле. Узлов у нити
     * десятки, не тысячи, — читать их все дешевле, чем считать, какие сейчас
     * на экране.
     *
     * По времени появления: порядок в списке не значит ничего (место узла
     * хранится числами), но постоянный порядок нужен разметке, чтобы карточки
     * не переставлялись местами при каждом обновлении.
     */
    @Query("SELECT * FROM thread_nodes WHERE threadId = :threadId ORDER BY createdAt, id")
    fun observeNodes(threadId: Long): Flow<List<ThreadNode>>

    @Query("SELECT * FROM thread_edges WHERE threadId = :threadId ORDER BY id")
    fun observeEdges(threadId: Long): Flow<List<ThreadEdge>>

    @Query("SELECT * FROM thread_nodes WHERE id = :id")
    suspend fun node(id: Long): ThreadNode?

    /**
     * Занятые места на карте — вопросом, а не потоком.
     *
     * Спрашивается изнутри транзакции, в которой заводится новый узел, и
     * подписка тут не годится вовсе: поток Room ждёт своего соединения, а
     * соединение занято той же транзакцией, — так добывают не список, а
     * зависание. Отсюда `suspend` и два столбца вместо целых строк.
     */
    @Query("SELECT x, y FROM thread_nodes WHERE threadId = :threadId")
    suspend fun spotsOf(threadId: Long): List<Spot>

    @Insert
    suspend fun insertNode(node: ThreadNode): Long

    @Update
    suspend fun updateNode(node: ThreadNode)

    /**
     * Передвинуть узел — отдельным запросом, а не правкой всей строки.
     *
     * Пока карточку тянут пальцем, место меняется десятки раз в секунду.
     * Переписывать при этом название и текст значило бы гонять по базе то, чего
     * никто не трогал, и рисковать затереть правку, сделанную в другом окне.
     */
    @Query("UPDATE thread_nodes SET x = :x, y = :y WHERE id = :id")
    suspend fun moveNode(id: Long, x: Float, y: Float)

    @Query("DELETE FROM thread_nodes WHERE id = :id")
    suspend fun deleteNode(id: Long)

    @Insert
    suspend fun insertEdge(edge: ThreadEdge): Long

    @Query("DELETE FROM thread_edges WHERE id = :id")
    suspend fun deleteEdge(id: Long)

    /** Связи стёртого узла. Линия в пустоту — хуже отсутствия линии. */
    @Query("DELETE FROM thread_edges WHERE fromId = :nodeId OR toId = :nodeId")
    suspend fun deleteEdgesOf(nodeId: Long)

    /**
     * Связаны ли уже эти двое — в любую сторону.
     *
     * В любую нарочно: человек, тянущий связь от шага к пути, и человек,
     * тянущий её от пути к шагу, говорят одно и то же. Второй линии между теми
     * же узлами на карте не появится.
     */
    @Query(
        """
        SELECT COUNT(*) FROM thread_edges
         WHERE (fromId = :a AND toId = :b) OR (fromId = :b AND toId = :a)
        """,
    )
    suspend fun tied(a: Long, b: Long): Int

    /**
     * Узлы всех нитей коротко — для ленты раздела.
     *
     * Без текста и без места: ленте нужно знать, сколько узлов, какие из них
     * свежие и что за ближайший несделанный шаг. Тянуть ради этого полные
     * карточки всех нитей значило бы читать весь раздел на каждый взгляд.
     *
     * Дело шага приходит слева-присоединением: шаг, отмеченный не здесь, а
     * утром в дне, лента обязана считать сделанным — иначе Askya знала бы про
     * одно событие два разных ответа.
     */
    @Query(
        """
        SELECT n.threadId AS threadId, n.id AS id, n.kind AS kind, n.title AS title,
               n.createdAt AS createdAt, n.doneAt AS doneAt,
               d.done AS deedDone, d.date AS deedDay
          FROM thread_nodes n
          LEFT JOIN schedule_items d ON d.id = n.deedId AND d.removedAt IS NULL
         ORDER BY n.createdAt, n.id
        """,
    )
    fun observeNodeMarks(): Flow<List<ThreadNodeMark>>

    /** Связи всех нитей — из них видно, плетётся нить или тянется в длину. */
    @Query("SELECT threadId, fromId, toId FROM thread_edges")
    fun observeEdgeMarks(): Flow<List<ThreadEdgeMark>>
}

/** День, в который нить двигалась. */
data class ThreadTouch(val threadId: Long, val day: LocalDate)

/** Незакрытая строка списка нити — кандидат в «что дальше». */
data class ThreadLine(val threadId: Long, val text: String)

/** Строка списка нити вместе с отметкой. */
data class ThreadTask(val id: Long, val text: String, val done: Boolean)

/** Деньги нити: сколько записей и сколько по ним ушло. */
data class ThreadMoney(val count: Int = 0, val spent: Long = 0)

/** Узел, каким его видит лента: без текста, места и привязок. */
data class ThreadNodeMark(
    val threadId: Long,
    val id: Long,
    val kind: ThreadNodeKind,
    val title: String,
    val createdAt: LocalDateTime,
    val doneAt: LocalDateTime?,
    /** Отметка дела, поставленного из этого шага. `null` — дела не ставили. */
    val deedDone: Boolean? = null,
    /** День того дела: им и датируется победа, если отметку ставили в дне. */
    val deedDay: LocalDate? = null,
) {
    /**
     * Когда шаг сделан: своей отметкой или отметкой его дела.
     *
     * Двумя источниками нарочно. Свой [doneAt] ставят на карте, отметку дела —
     * в дне, и оба означают одно и то же событие. Спрашивать человека, где
     * «настоящая» отметка, приложение не вправе: он отметил один раз.
     */
    val doneOn: LocalDate?
        get() = doneAt?.toLocalDate() ?: deedDay?.takeIf { deedDone == true }
}

/** Связь, какой её видит лента. */
data class ThreadEdgeMark(val threadId: Long, val fromId: Long, val toId: Long)
