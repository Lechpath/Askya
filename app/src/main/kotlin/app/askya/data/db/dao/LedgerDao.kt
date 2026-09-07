package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.LedgerAccount
import app.askya.data.entity.LedgerCategory
import app.askya.data.entity.LedgerEntry
import app.askya.domain.model.EntryKind
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface LedgerDao {

    // ---- Счета ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: LedgerAccount): Long

    @Update
    suspend fun updateAccount(account: LedgerAccount)

    /**
     * Закрытые уходят вниз: ими не пользуются, но история на них висит.
     *
     * Дальше — порядок, разложенный руками ([LedgerAccount.position]), и
     * только потом дата: номера бывают с повторами у счетов, заведённых до
     * того, как порядок появился, и дата разбивает ничью тем же способом, что
     * и раньше.
     */
    @Query("SELECT * FROM ledger_accounts ORDER BY closed, position, createdAt")
    fun observeAccounts(): Flow<List<LedgerAccount>>

    @Query("SELECT COUNT(*) FROM ledger_accounts")
    suspend fun countAccounts(): Int

    @Query("UPDATE ledger_accounts SET position = :position WHERE id = :id")
    suspend fun setAccountPosition(id: Long, position: Int)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM ledger_accounts")
    suspend fun nextAccountPosition(): Int

    /**
     * Сколько на каждом счету набежало записями — без начального остатка: его
     * прибавляет репозиторий, потому что лежит он в самой строке счёта.
     *
     * Одним запросом на все счета, а не по запросу на строку: счетов пять, а
     * подписок на список было бы пять же, и каждая будила бы экран отдельно.
     *
     * Перевод считается дважды и в разные стороны: с одного счёта ушло, на
     * другой пришло. Первая половина запроса даёт минус на счёте-источнике
     * (перевод — не доход), вторая — плюс на счёте-получателе.
     *
     * Возврат прибавляется, как доход: для счёта он и есть приход, а тем, что
     * это не заработок, а вернувшаяся чужая трата, заняты итоги месяца — см.
     * [app.askya.domain.model.EntryKind.BACK].
     */
    @Query(
        """
        SELECT id, SUM(delta) AS delta FROM (
            SELECT accountId AS id,
                   CASE WHEN kind IN ('EARN', 'BACK') THEN amount ELSE -amount END AS delta
              FROM ledger_entries WHERE removedAt IS NULL
            UNION ALL
            SELECT toAccountId AS id, amount AS delta
              FROM ledger_entries
             WHERE removedAt IS NULL AND kind = 'MOVE' AND toAccountId IS NOT NULL
        ) GROUP BY id
        """
    )
    fun observeDeltas(): Flow<List<AccountDelta>>

    /** Есть ли на счету хоть одна запись — от этого зависит, можно ли его стереть. */
    @Query(
        "SELECT COUNT(*) FROM ledger_entries " +
            "WHERE accountId = :accountId OR toAccountId = :accountId",
    )
    suspend fun countEntriesOn(accountId: Long): Int

    @Query("DELETE FROM ledger_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    // ---- Статьи ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: LedgerCategory): Long

    @Update
    suspend fun updateCategory(category: LedgerCategory)

    @Query("SELECT * FROM ledger_categories ORDER BY kind, title")
    fun observeCategories(): Flow<List<LedgerCategory>>

    @Query("SELECT COUNT(*) FROM ledger_categories")
    suspend fun countCategories(): Int

    /** Статью убирают — записи остаются и становятся «без статьи». */
    @Query("UPDATE ledger_entries SET categoryId = NULL WHERE categoryId = :id")
    suspend fun detachFromCategory(id: Long)

    @Query("DELETE FROM ledger_categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    // ---- Записи ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LedgerEntry): Long

    @Update
    suspend fun updateEntry(entry: LedgerEntry)

    /**
     * Записи месяца — и лента, и все его итоги.
     *
     * Итоги считаются из этого же списка, а не отдельными `SUM`: записей в
     * месяце сотня-другая, сложить их в Kotlin — доли миллисекунды, зато
     * лента и цифры над ней приходят из одного места и не могут разойтись.
     *
     * Свежие сверху, а внутри дня — позже записанные первыми: вечернюю трату
     * ищут глазами раньше утренней.
     */
    @Query(
        "SELECT * FROM ledger_entries WHERE removedAt IS NULL " +
            "AND date BETWEEN :from AND :to ORDER BY date DESC, id DESC",
    )
    fun observeIn(from: LocalDate, to: LocalDate): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun entry(id: Long): LedgerEntry?

    /**
     * Дата самой поздней записи — докуда пускать листание месяцев.
     *
     * Обычно она в нынешнем месяце или раньше, и вопрос этот праздный: вперёд
     * листать нечего. Но запись, попавшая в будущее — опиской в дате или тем
     * самым годом, который книга когда-то подставляла вперёд, — иначе не
     * достаётся вовсе: в остаток счёта и в статистику она входит, а открыть
     * её месяц нельзя, и стереть её человек не может ничем.
     *
     * `null` — записей нет вовсе.
     */
    @Query("SELECT MAX(date) FROM ledger_entries WHERE removedAt IS NULL")
    fun observeLastDate(): Flow<LocalDate?>

    /**
     * Вся книга, сложенная по месяцам и статьям разом.
     *
     * Один запрос на всю статистику, а не по запросу на каждый её вопрос.
     * Спрашивают у неё разное — сколько по месяцам, куда уходит, сколько в
     * среднем, — но всё это складывается из одной и той же горстки чисел:
     * месяц, статья, вид, сумма. Строк выходит столько, сколько месяцев
     * помножить на статьи, — за пять лет ведения это пара сотен, и дальше их
     * складывает Kotlin. Три отдельных запроса считали бы одно и то же трижды
     * и могли бы разойтись между собой на глазах у человека.
     *
     * Месяц берётся первыми семью буквами даты: она лежит строкой ISO
     * («2026-08-28»), и «2026-08» из неё вырезается без разбора даты. Тем же
     * порядком она и сортируется.
     *
     * Переводов здесь нет: перекладывание из кармана в карман — не доход и не
     * расход, и в статистике ему делать нечего ровно так же, как в итогах
     * месяца (см. [app.askya.domain.model.EntryKind.MOVE]).
     *
     * Возвраты здесь есть, и отдельным видом: вычесть их из расхода — дело
     * репозитория, а база отдаёт то, что записано, не складывая разное.
     */
    @Query(
        """
        SELECT substr(date, 1, 7) AS month, categoryId, kind, SUM(amount) AS amount
          FROM ledger_entries
         WHERE removedAt IS NULL AND kind != 'MOVE'
         GROUP BY month, categoryId, kind
         ORDER BY month
        """
    )
    fun observeTotals(): Flow<List<LedgerTotal>>

    @Query("UPDATE ledger_entries SET removedAt = :at WHERE id = :id")
    suspend fun setEntryRemoved(id: Long, at: LocalDateTime?)

    @Query("DELETE FROM ledger_entries WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun purgeEntries(before: LocalDateTime)
}

/** Сколько на счету набежало записями. Начальный остаток сюда не входит. */
data class AccountDelta(val id: Long, val delta: Long)

/**
 * Клетка статистики: месяц, статья, вид записи и сумма по ним.
 *
 * [month] — «2026-08»: год и месяц строкой, как их вырезали из даты. Разбирать
 * её в `YearMonth` — дело репозитория, DAO отдаёт то, что вернул SQLite.
 * [categoryId] пуст у записей без статьи, и это не пропуск, а ответ.
 */
data class LedgerTotal(
    val month: String,
    val categoryId: Long?,
    val kind: EntryKind,
    val amount: Long,
)
