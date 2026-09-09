package app.askya.data.repository

import app.askya.data.db.dao.AlbumDao
import app.askya.data.db.dao.NoteDao
import app.askya.data.db.dao.TopicDao
import app.askya.data.entity.ImageAlbum
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.data.audio.VoiceStore
import app.askya.data.images.ImageStore
import app.askya.domain.model.MarkColor
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Репозиторий тонкий: прячет DAO от ViewModel и держит правила, которых в SQL
 * не выразить — например, обновление `updatedAt` при каждом сохранении.
 */
class NoteRepository(
    private val dao: NoteDao,
    private val topics: TopicDao,
    private val albums: AlbumDao,
    private val images: ImageStore,
    private val voices: VoiceStore,
) {

    fun notes(): Flow<List<Note>> = dao.observeAll()

    /** Записи, заведённые в этот день, — «Что было» в AskyaDay. */
    fun createdOn(date: java.time.LocalDate): Flow<List<Note>> =
        dao.observeCreatedBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())

    fun notes(query: String, tag: String): Flow<List<Note>> =
        dao.observeFiltered(query.trim(), tag.trim())

    fun note(id: Long): Flow<Note?> = dao.observeById(id)

    suspend fun get(id: Long): Note? = dao.getById(id)

    suspend fun create(): Long = dao.insert(Note())

    suspend fun save(note: Note) = dao.update(note.copy(updatedAt = LocalDateTime.now()))

    /**
     * Запись убирается вместе со своей копией картинки: файл в папке Askya
     * существует только ради этой записи, и без неё его никто не откроет.
     * Чужие документы (старые записи-ссылки) не трогаются — см. ImageStore.
     */
    /** Убрать запись — в корзину на сутки. См. [ScheduleRepository.remove]. */
    suspend fun remove(id: Long) = dao.setRemoved(id, LocalDateTime.now())

    suspend fun restore(id: Long) = dao.setRemoved(id, null)

    /**
     * Выбросить пролежавшее в корзине сутки.
     *
     * По одной, а не одним `DELETE`: у картинки в папке Askya лежит файл, и
     * убрать его надо вместе со строкой — иначе в папке копится то, на что уже
     * ничего не ссылается.
     */
    suspend fun purgeTrash() {
        dao.expired(LocalDateTime.now().minusDays(1)).forEach { note -> delete(note) }
    }

    /**
     * Стереть запись вместе с её файлом.
     *
     * Файл убирается тот, который Askya клала сама: копию картинки — из папки
     * с картинками, голос — из папки с голосом. Обе проверки по записи, а не
     * по папке: чужой документ (pdf, положенный ссылкой) не трогается ни той,
     * ни другой, и правильно — его правят снаружи, и он никуда не девался.
     */
    suspend fun delete(note: Note) {
        dao.delete(note)
        if (note.voice) voices.remove(note.uri) else images.delete(note.uri)
    }

    fun images(): Flow<List<Note>> = dao.observeImages()

    fun loose(): Flow<List<Note>> = dao.observeLoose()

    /** Полка голосовых заметок — подраздел Scroll «Голос». */
    fun voices(): Flow<List<Note>> = dao.observeVoices()

    /**
     * Записать наговорённое отдельной записью Scroll.
     *
     * Заголовок ставится сразу и днём с часом: заметку наговаривают на бегу, и
     * останавливать человека вопросом «как её назвать» — значит терять ту
     * мысль, ради которой он и нажал кнопку. Переименовать её можно потом, а
     * неназванная она всё равно находится — по времени.
     */
    suspend fun addVoice(uri: String, title: String, durationMs: Long): Long = dao.insert(
        Note(
            title = title,
            uri = uri,
            mime = VoiceStore.MIME,
            durationMs = durationMs,
        )
    )

    fun inTopic(topicId: Long): Flow<List<Note>> = dao.observeInTopic(topicId)

    /**
     * Соседи записи по полке — то, что листается смахиванием в карточке.
     *
     * Полка та же, на которой запись лежит: у записи в книге соседи — записи
     * той же книги, у отдельной — «Библиотека». Листать из книги в чужие
     * записи человек не ждёт: он видел полку глазами и помнит, что было рядом.
     *
     * Разовым списком, а не потоком: полка нужна в тот момент, когда карточку
     * открыли, а подписка перестраивала бы порядок под пальцем — каждая правка
     * двигает запись в начало списка по времени.
     *
     * У картинки соседей здесь нет: её листают в просмотре и по своему срезу —
     * альбому или всей сетке.
     */
    suspend fun shelfOf(id: Long): List<Long> {
        val note = dao.getById(id) ?: return emptyList()
        if (note.isImage) return emptyList()
        val topicId = note.topicId
        return if (topicId == null) dao.looseIds() else dao.idsInTopic(topicId)
    }

    fun topics(): Flow<List<ScrollTopic>> = topics.observeAll()

    fun topic(id: Long): Flow<ScrollTopic?> = topics.observeById(id)

    suspend fun addTopic(title: String, color: MarkColor? = null): Long =
        topics.insert(ScrollTopic(title = title.trim(), color = color))

    /**
     * Название и цвет корешка правятся одним разом: в карточке книги их и
     * выбирают вместе, а две записи в базу мигали бы полкой дважды.
     */
    suspend fun updateTopic(topic: ScrollTopic, title: String, color: MarkColor?) =
        topics.update(topic.copy(title = title.trim(), color = color))

    /**
     * Тема удаляется, записи остаются: они переезжают в «Файлы». Удалять
     * содержимое вместе с папкой — не то, чего ждут от переименования полки.
     */
    suspend fun deleteTopic(id: Long) {
        dao.detachFromTopic(id)
        topics.deleteById(id)
    }

    /**
     * Заводит запись-файл.
     *
     * Картинки приходят сюда уже скопированными в папку Askya (см.
     * ImageStore): раздел «Изображения» должен держать картинку сам, а не
     * зависеть от того, жив ли ещё чужой документ. Остальные файлы (pdf, md)
     * по-прежнему остаются ссылками: их правят снаружи, и копия устаревала бы
     * молча.
     *
     * У картинки книги не бывает — вместо неё альбом: [topicId] у неё всегда
     * пуст, даже если файл добавляют, стоя внутри книги.
     */
    suspend fun addFile(
        uri: String,
        name: String,
        mime: String,
        isImage: Boolean,
        topicId: Long?,
        albumId: Long? = null,
    ): Long = dao.insert(
        Note(
            title = name,
            uri = uri,
            mime = mime,
            isImage = isImage,
            topicId = if (isImage) null else topicId,
            albumId = if (isImage) albumId else null,
        )
    )

    fun inAlbum(albumId: Long): Flow<List<Note>> = dao.observeInAlbum(albumId)

    fun albums(): Flow<List<ImageAlbum>> = albums.observeAll()

    fun album(id: Long): Flow<ImageAlbum?> = albums.observeById(id)

    suspend fun addAlbum(title: String): Long = albums.insert(ImageAlbum(title = title.trim()))

    suspend fun renameAlbum(album: ImageAlbum, title: String) =
        albums.update(album.copy(title = title.trim()))

    /**
     * Альбом удаляется, картинки остаются: они возвращаются в общую сетку
     * «Изображений». Стереть снимки заодно с папкой — не то, чего ждут.
     */
    suspend fun deleteAlbum(id: Long) {
        dao.detachFromAlbum(id)
        albums.deleteById(id)
    }

    /**
     * Подпись и альбом картинки — то, что заполняют в её карточке.
     *
     * Одним сохранением, а не двумя: карточка — один разговор, и записывать её
     * по частям значило бы оставить картинку подписанной, но без альбома, если
     * второе не дошло.
     *
     * Вместе с подписью переименовывается и сам файл в папке Askya: имя одно,
     * и в папке, куда человек заходит проводником, оно должно быть тем же, что
     * в разделе. Файл переименоваться может и не успеть (чужой документ,
     * занятое имя) — подпись от этого не отменяется, она важнее.
     */
    suspend fun captionImage(note: Note, title: String, albumId: Long?) {
        val name = title.trim().ifBlank { note.title }
        val renamed = if (name == note.title) null else images.rename(note.uri, name)
        dao.update(
            note.copy(
                title = name,
                albumId = albumId,
                uri = renamed ?: note.uri,
                updatedAt = LocalDateTime.now(),
            )
        )
    }

    /** Разом переложить выбранные картинки в альбом (`null` — вынуть из всех). */
    suspend fun moveImages(ids: List<Long>, albumId: Long?) {
        if (ids.isEmpty()) return
        dao.moveToAlbum(ids, albumId, LocalDateTime.now())
    }

    /** Убрать выбранные разом — каждую вместе с её копией в папке. */
    suspend fun delete(notes: List<Note>) {
        notes.forEach { delete(it) }
    }

    /**
     * Переносит старые копии в видимую папку Askya.
     *
     * Раньше картинки лежали в папке приложения, куда с Android 11 не заходит
     * проводник. Переезд идёт по одной картинке и записывается сразу: оборвись
     * он на середине, перенесённые останутся перенесёнными, а остальные —
     * читаемыми со старого места. Повторный запуск просто не найдёт, что
     * переносить.
     */
    suspend fun moveImagesToFolder() {
        dao.allImages().forEach { note ->
            val moved = images.adopt(note.uri) ?: return@forEach
            dao.update(note.copy(uri = moved))
        }
    }

    suspend fun createNote(topicId: Long?): Long = dao.insert(Note(topicId = topicId))

    /**
     * Заметка, записанная разом, — так её заводят быстрой кнопкой из меню.
     *
     * Отдельно от [createNote], который заводит пустую и отдаёт её правке:
     * здесь текст уже написан, и промежуточная пустая запись успела бы
     * мелькнуть в «Недавнем» безымянной строкой.
     *
     * Ложится туда же, куда и всё остальное записанное, — в «Библиотеку», без
     * книги: быстрая заметка отличается тем, как её написали, а не тем, где
     * она потом лежит.
     */
    suspend fun quickNote(title: String, body: String): Long =
        dao.insert(Note(title = title.trim(), body = body.trim()))

    /**
     * Переводит запись на новый файл картинки — итог правки или коллажа.
     *
     * Старая копия удаляется только после того, как запись уже смотрит на
     * новую: оборвись что-нибудь посередине, лучше остаться с лишним файлом в
     * папке, чем с записью, у которой файла нет.
     *
     * Тип берётся у новой копии, а не у старой записи: правка могла
     * сохраниться в jpeg там, где исходник был png, и старый тип сбивал бы
     * просмотр. Спрашивается он у системы — в ссылке на файл в папке Askya
     * расширения больше нет.
     */
    suspend fun replaceImage(note: Note, uri: String) {
        val previous = note.uri
        dao.update(
            note.copy(
                uri = uri,
                mime = images.mimeOf(uri),
                isImage = true,
                updatedAt = LocalDateTime.now(),
            )
        )
        if (previous != uri) images.delete(previous)
    }
}
