package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.LedgerEntry
import app.askya.data.entity.Note
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ThreadItem
import app.askya.data.entity.YetList
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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
     * Все нити: идущие первыми, потом отложенные, потом закрытые.
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
                    WHEN 'LIVE' THEN 0
                    WHEN 'PAUSED' THEN 1
                    ELSE 2
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
     * - **правка записи** Scroll: замеры и черновик пишут руками.
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
}

/** День, в который нить двигалась. */
data class ThreadTouch(val threadId: Long, val day: LocalDate)

/** Незакрытая строка списка нити — кандидат в «что дальше». */
data class ThreadLine(val threadId: Long, val text: String)

/** Строка списка нити вместе с отметкой. */
data class ThreadTask(val id: Long, val text: String, val done: Boolean)

/** Деньги нити: сколько записей и сколько по ним ушло. */
data class ThreadMoney(val count: Int = 0, val spent: Long = 0)
