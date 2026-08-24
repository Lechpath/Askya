package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import kotlinx.coroutines.flow.Flow

@Dao
interface YetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: YetList): Long

    @Update
    suspend fun updateList(list: YetList)

    @Query("DELETE FROM yet_lists WHERE id = :id")
    suspend fun deleteListById(id: Long)

    @Query("SELECT * FROM yet_lists ORDER BY createdAt")
    fun observeLists(): Flow<List<YetList>>

    @Query("SELECT * FROM yet_lists WHERE id = :id")
    fun observeList(id: Long): Flow<YetList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: YetItem): Long

    @Update
    suspend fun updateItem(item: YetItem)

    @Query("DELETE FROM yet_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM yet_items WHERE listId = :listId")
    suspend fun deleteItemsOf(listId: Long)

    @Query("DELETE FROM yet_items WHERE listId = :listId AND done = 1")
    suspend fun deleteDoneOf(listId: Long)

    /**
     * Сделанное уходит вниз прямо в запросе: сортировать в Kotlin значило бы
     * пересобирать порядок на каждой перерисовке, а список меняется от каждой
     * отметки.
     *
     * Номер строки — третьим ключом: вставленный целиком список ложится в базу
     * за одно мгновение, и по одному только времени создания подпункт мог бы
     * встать выше своего пункта.
     */
    @Query("SELECT * FROM yet_items WHERE listId = :listId ORDER BY done, createdAt, id")
    fun observeItems(listId: Long): Flow<List<YetItem>>

    /** Все строки сразу — из них считается, сколько «ещё» в каждом списке. */
    @Query("SELECT * FROM yet_items")
    fun observeAllItems(): Flow<List<YetItem>>
}
