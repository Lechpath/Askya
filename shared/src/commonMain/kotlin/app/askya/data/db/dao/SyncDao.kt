package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import app.askya.data.entity.SyncState

/**
 * Журнал правок — читается синхронизацией и ею же отмечается отправленным.
 *
 * Писать в журнал отсюда нельзя и незачем: его ведут триггеры у самих таблиц
 * (`SyncSchema`). Здесь — только чтение, отметка «уехало» и две ручки часов:
 * подтянуть их к чужой отметке и замолчать на время приёма чужого.
 */
@Dao
interface SyncDao {

    /** Что ещё не уехало — по порядку правки, старое первым. */
    @Query("SELECT * FROM sync_state WHERE dirty = 1 ORDER BY hlc LIMIT :limit")
    suspend fun dirty(limit: Int = 500): List<SyncState>

    @Query("SELECT COUNT(*) FROM sync_state WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM sync_state WHERE tbl = :table AND uid = :uid")
    suspend fun state(table: String, uid: String): SyncState?

    /**
     * Уехало: снимается отметка `dirty`, но **не** сама строка журнала. Она и
     * дальше говорит, когда строку правили, — по ней сравнивают свою версию с
     * чужой.
     */
    @Query("UPDATE sync_state SET dirty = 0 WHERE tbl = :table AND uid = :uid AND hlc <= :upTo")
    suspend fun sent(table: String, uid: String, upTo: Long)

    /** Отметка чужой строки: её ставит приём, пока триггеры молчат. */
    @Query(
        "INSERT OR REPLACE INTO sync_state (tbl, uid, hlc, dead, dirty) " +
            "VALUES (:table, :uid, :hlc, :dead, 0)",
    )
    suspend fun remember(table: String, uid: String, hlc: Long, dead: Int)

    @Query("SELECT hlc FROM sync_clock WHERE id = 0")
    suspend fun clock(): Long?

    /**
     * Часы вперёд — до чужой отметки и на шаг дальше. Назад они не идут:
     * `MAX` оставит своё, если оно позже.
     */
    @Query("UPDATE sync_clock SET hlc = MAX(hlc, :seen + 1) WHERE id = 0")
    suspend fun advance(seen: Long)

    /** Молчание триггеров на время приёма чужих правок. */
    @Query("UPDATE sync_clock SET applying = :on WHERE id = 0")
    suspend fun applying(on: Int)
}
