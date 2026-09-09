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

    @Query("SELECT * FROM notes WHERE removedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Note>>

    /**
     * Записи, заведённые в один день, — для карточки «Что было».
     *
     * По `createdAt`, а не по индексированному `updatedAt`: спрашивают
     * «что я записал в тот день», а не «что я в тот день правил». Правка
     * старой заметки — не сегодняшнее событие, сколько бы её ни правили.
     *
     * Границы полуоткрытые: `BETWEEN` захватил бы полночь следующего дня.
     */
    @Query(
        "SELECT * FROM notes WHERE removedAt IS NULL " +
            "AND createdAt >= :from AND createdAt < :until ORDER BY createdAt",
    )
    fun observeCreatedBetween(from: LocalDateTime, until: LocalDateTime): Flow<List<Note>>

    /** Раздел «Изображения» — все картинки разом, и в альбомах, и без. */
    @Query("SELECT * FROM notes WHERE isImage = 1 AND removedAt IS NULL ORDER BY updatedAt DESC")
    fun observeImages(): Flow<List<Note>>

    /** Один альбом. */
    @Query("SELECT * FROM notes WHERE isImage = 1 AND albumId = :albumId AND removedAt IS NULL ORDER BY updatedAt DESC")
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

    /**
     * Раздел «Файлы» — всё без темы, кроме картинок и голоса: у тех свои полки.
     *
     * Голос отбирается по mime, а не по своей колонке, в отличие от картинок:
     * `LIKE 'audio/%'` в SQLite есть, и заводить второй флаг ради того, что и
     * так записано, незачем. У картинок флаг появился раньше и по другой
     * причине — их отбирают в четырёх запросах, и `LIKE` в каждом стоил бы
     * полного перебора таблицы.
     */
    @Query(
        "SELECT * FROM notes WHERE isImage = 0 AND mime NOT LIKE 'audio/%' " +
            "AND topicId IS NULL AND removedAt IS NULL ORDER BY updatedAt DESC",
    )
    fun observeLoose(): Flow<List<Note>>

    /** Голосовые заметки — своя полка Scroll, самые свежие сверху. */
    @Query(
        "SELECT * FROM notes WHERE mime LIKE 'audio/%' AND removedAt IS NULL " +
            "ORDER BY createdAt DESC",
    )
    fun observeVoices(): Flow<List<Note>>

    /** Книга. Ни картинок, ни голоса в ней не бывает: у них свои полки. */
    @Query(
        "SELECT * FROM notes WHERE isImage = 0 AND mime NOT LIKE 'audio/%' " +
            "AND topicId = :topicId AND removedAt IS NULL ORDER BY updatedAt DESC",
    )
    fun observeInTopic(topicId: Long): Flow<List<Note>>

    @Query("SELECT COUNT(*) FROM notes WHERE topicId = :topicId AND removedAt IS NULL")
    fun observeCountInTopic(topicId: Long): Flow<Int>

    /**
     * Полка книги одними номерами — по ней листают карточку смахиванием.
     *
     * Номерами, а не записями: листающему нужен порядок, а сама запись всё
     * равно читается своим потоком, когда до неё дойдут. Порядок тот же, что у
     * [observeInTopic], — тот, в котором полку и видели глазами.
     */
    @Query(
        "SELECT id FROM notes WHERE isImage = 0 AND mime NOT LIKE 'audio/%' " +
            "AND topicId = :topicId AND removedAt IS NULL ORDER BY updatedAt DESC",
    )
    suspend fun idsInTopic(topicId: Long): List<Long>

    /** То же для записей без книги — их полка это «Библиотека». */
    @Query(
        "SELECT id FROM notes WHERE isImage = 0 AND mime NOT LIKE 'audio/%' " +
            "AND topicId IS NULL AND removedAt IS NULL ORDER BY updatedAt DESC",
    )
    suspend fun looseIds(): List<Long>

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
        WHERE removedAt IS NULL
          AND (:query = '' OR title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
          AND (:tag = '' OR tags LIKE '%' || :tag || '%')
        ORDER BY updatedAt DESC
        """
    )
    fun observeFiltered(query: String, tag: String): Flow<List<Note>>

    @Query("UPDATE notes SET removedAt = :at WHERE id = :id")
    suspend fun setRemoved(id: Long, at: LocalDateTime?)

    /**
     * Записи, пролежавшие убранными дольше суток.
     *
     * Отдаются, а не стираются запросом: у картинки в папке Askya лежит файл,
     * и убрать его надо вместе со строкой — иначе в папке копится то, на что
     * уже ничего не ссылается.
     */
    @Query("SELECT * FROM notes WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun expired(before: LocalDateTime): List<Note>
}
