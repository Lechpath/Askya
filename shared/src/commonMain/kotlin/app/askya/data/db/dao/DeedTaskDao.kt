package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.DeedTask
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface DeedTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DeedTask): Long

    @Update
    suspend fun update(task: DeedTask)

    @Query("SELECT * FROM deed_tasks WHERE id = :id")
    suspend fun getById(id: Long): DeedTask?

    /**
     * Строки одного дела. Сделанное уходит вниз прямо в запросе — как в Yet:
     * сортировать в Kotlin значило бы пересобирать порядок на каждой отметке.
     *
     * Номер строки третьим ключом: вставленный целиком список ложится в базу
     * за одно мгновение, и по одному только времени создания строки могли бы
     * перепутаться местами.
     */
    @Query(
        "SELECT * FROM deed_tasks WHERE deedId = :deedId AND removedAt IS NULL " +
            "ORDER BY done, createdAt, id",
    )
    fun observeOf(deedId: Long): Flow<List<DeedTask>>

    /**
     * Строки всех дел названного дня — одним запросом.
     *
     * Так их читает страница дня: маленькая карточка показывает «3 из 7», и
     * спрашивать базу по строке на каждую из полутора десятков карточек
     * значило бы завести полтора десятка подписок ради одной цифры.
     */
    @Query(
        "SELECT t.* FROM deed_tasks t JOIN schedule_items d ON d.id = t.deedId " +
            "WHERE d.date = :date AND d.removedAt IS NULL AND t.removedAt IS NULL " +
            "ORDER BY t.done, t.createdAt, t.id",
    )
    fun observeOnDate(date: LocalDate): Flow<List<DeedTask>>

    /**
     * То же разовым чтением. Нужно шторке: она собирается в приёмнике
     * будильника и в фоне, где подписываться не на что.
     */
    @Query(
        "SELECT t.* FROM deed_tasks t JOIN schedule_items d ON d.id = t.deedId " +
            "WHERE d.date = :date AND d.removedAt IS NULL AND t.removedAt IS NULL " +
            "ORDER BY t.done, t.createdAt, t.id",
    )
    suspend fun onDate(date: LocalDate): List<DeedTask>

    /** У каких дел этого дня список есть — по ним пересобирается день. */
    @Query(
        "SELECT DISTINCT t.deedId FROM deed_tasks t JOIN schedule_items d ON d.id = t.deedId " +
            "WHERE d.date = :date AND t.removedAt IS NULL",
    )
    suspend fun deedsWithTasksOn(date: LocalDate): List<Long>

    @Query("UPDATE deed_tasks SET removedAt = :at WHERE id = :id")
    suspend fun setRemoved(id: Long, at: LocalDateTime?)

    @Query("DELETE FROM deed_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Дело ушло из базы совсем — строки уходят следом. */
    @Query("DELETE FROM deed_tasks WHERE deedId = :deedId")
    suspend fun deleteOf(deedId: Long)

    @Query("DELETE FROM deed_tasks WHERE deedId = :deedId AND done = 1")
    suspend fun deleteDoneOf(deedId: Long)

    @Query("DELETE FROM deed_tasks WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun purge(before: LocalDateTime)

    /**
     * Строки, у которых дела больше нет.
     *
     * Дело убирают из разных мест — очисткой дня, пересборкой, уборкой
     * корзины, — и ловить каждое из них значило бы забыть одно. Уборка при
     * запуске подчищает всё разом.
     */
    @Query("DELETE FROM deed_tasks WHERE deedId NOT IN (SELECT id FROM schedule_items)")
    suspend fun purgeOrphans()
}
