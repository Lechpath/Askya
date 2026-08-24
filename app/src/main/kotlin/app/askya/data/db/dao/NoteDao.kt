package app.askya.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import app.askya.data.entity.Note
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    /** Раздел «Изображения» — все картинки разом, и в альбомах, и без. */
    @Query("SELECT * FROM notes WHERE isImage = 1 ORDER BY updatedAt DESC")
    fun observeImages(): Flow<List<Note>>

    /** Один альбом. */
    @Query("SELECT * FROM notes WHERE isImage = 1 AND albumId = :albumId ORDER BY updatedAt DESC")
    fun observeInAlbum(albumId: Long): Flow<List<Note>>

    /**
     * Разом переложить выбранные картинки в альбом. Одним запросом, а не
     * записью по одной: перекладывают пачкой, и десять правок подряд десять
     * раз дёрнули бы все подписанные на сетку экраны.
     */
    @Query("UPDATE notes SET albumId = :albumId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveToAlbum(ids: List<Long>, albumId: Long?, now: LocalDateTime)

    /** Альбом удаляют — картинки остаются и переезжают в общую сетку. */
    @Query("UPDATE notes SET albumId = NULL WHERE albumId = :albumId")
    suspend fun detachFromAlbum(albumId: Long)

    /** Все картинки разом — нужны переезду копий в видимую папку Askya. */
    @Query("SELECT * FROM notes WHERE isImage = 1")
    suspend fun allImages(): List<Note>

    /** Раздел «Файлы» — всё без темы, кроме картинок: у тех свой раздел. */
    @Query("SELECT * FROM notes WHERE isImage = 0 AND topicId IS NULL ORDER BY updatedAt DESC")
    fun observeLoose(): Flow<List<Note>>

    /** Книга. Картинок в ней не бывает: у них свой раздел и свои альбомы. */
    @Query("SELECT * FROM notes WHERE isImage = 0 AND topicId = :topicId ORDER BY updatedAt DESC")
    fun observeInTopic(topicId: Long): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE topicId = :topicId")
    fun observeCountInTopic(topicId: Long): Flow<Int>

    /** Тему удаляют — записи остаются и переезжают в «Файлы». */
    @Query("UPDATE notes SET topicId = NULL WHERE topicId = :topicId")
    suspend fun detachFromTopic(topicId: Long)

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): Note?

    /**
     * Пустой [query] или [tag] означает «без фильтра». Тег ищется по вхождению,
     * потому что теги лежат в одной колонке через перевод строки.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
          AND (:tag = '' OR tags LIKE '%' || :tag || '%')
        ORDER BY updatedAt DESC
        """
    )
    fun observeFiltered(query: String, tag: String): Flow<List<Note>>
}
