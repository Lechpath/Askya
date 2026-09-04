package app.askya.ui.scroll

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.askya.app.AppContainer
import app.askya.data.entity.ImageAlbum
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.data.audio.VoiceRecorder
import app.askya.data.audio.VoiceStore
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.Trash
import app.askya.data.repository.YetRepository
import app.askya.domain.model.MarkColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Общая модель Scroll: хаба и всех его разделов.
 *
 * Одна на всех, потому что данные общие — записи и темы лежат в двух таблицах,
 * а разделы это просто разные срезы по ним. Четыре модели дали бы четыре
 * подписки на одно и то же.
 */
class ScrollViewModel(
    private val notes: NoteRepository,
    yet: YetRepository,
    private val voiceStore: VoiceStore,
    private val recorder: VoiceRecorder,
    private val trash: Trash,
) : ViewModel() {

    /**
     * Списки Yet. Они переехали в Scroll подразделом «Списки»: и то и другое —
     * записанное, а не расписание, и искать это человек приходит в одно место.
     */
    val lists: StateFlow<List<YetList>> = yet.lists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Строки списков, разложенные по списку.
     *
     * Одним потоком вместо двух прежних («сколько осталось» и «сколько всего»):
     * лента Scroll вписывает в карточку списка его первые пункты, и по ним же
     * считается «3 из 12» — две подписки ради двух чисел, выводимых из тех же
     * строк, были лишними.
     */
    val listItems: StateFlow<Map<Long, List<YetItem>>> = yet.itemsByList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val images: StateFlow<List<Note>> = notes.images()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val loose: StateFlow<List<Note>> = notes.loose()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Полка голосовых заметок — подраздел «Голос». */
    val voices: StateFlow<List<Note>> = notes.voices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val topics: StateFlow<List<ScrollTopic>> = notes.topics()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Всё, что лежит в «Библиотеке»: и записи без книги, и убранные в книги.
     *
     * Нужно поиску: человек помнит название записи, а не книгу, в которую он
     * её положил, и поиск по одной только полке не находил бы именно то, что
     * убрано аккуратнее всего. Картинки сюда не входят — у них свой раздел.
     */
    val shelf: StateFlow<List<Note>> = notes.notes()
        .map { all -> all.filterNot { it.isImage } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Одна запись — нужна правке, просмотру и карточке картинки. */
    fun note(id: Long): Flow<Note?> = notes.note(id)

    /** Срез по теме строится на её номер, поэтому это функция, а не поле. */
    fun inTopic(topicId: Long): Flow<List<Note>> = notes.inTopic(topicId)

    fun topic(topicId: Long): Flow<ScrollTopic?> = notes.topic(topicId)

    fun inAlbum(albumId: Long): Flow<List<Note>> = notes.inAlbum(albumId)

    fun album(albumId: Long): Flow<ImageAlbum?> = notes.album(albumId)

    /** Сколько записей в теме — показывается в списке тем. */
    fun topicSizes(): Flow<Map<Long, Int>> = notes.notes()
        .map { all -> all.filter { it.topicId != null }.groupingBy { it.topicId!! }.eachCount() }

    /** Альбомы «Изображений». К книгам отношения не имеют — это свои папки. */
    val albums: StateFlow<List<ImageAlbum>> = notes.albums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Сколько картинок в альбоме — подпись под его карточкой. */
    val albumSizes: StateFlow<Map<Long, Int>> = notes.images()
        .map { all -> all.mapNotNull { it.albumId }.groupingBy { it }.eachCount() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Обложка альбома — последняя положенная в него картинка.
     *
     * Отдельного поля «обложка» нет намеренно: человек складывает в альбом
     * картинки, а не назначает одну из них главной. Пустой альбом показывает
     * только название.
     */
    val albumCovers: StateFlow<Map<Long, Note>> = notes.images()
        .map { all ->
            all.filter { it.albumId != null }
                .groupBy { requireNotNull(it.albumId) }
                .mapValues { (_, images) -> images.first() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Заводит запись-файл и отдаёт её номер: сразу после выбора открывается
     * разговор о нём — «как назовём» и куда положить. Молча положить файл в
     * список значило бы оставить его с именем вроде `IMG_20260813_2231.jpg`.
     *
     * У файла это книга ([topicId]), у картинки — альбом ([albumId]): разделы
     * раскладывают своё по-своему и друг друга не касаются.
     */
    fun addFile(
        picked: PickedFile,
        topicId: Long?,
        albumId: Long? = null,
        onCreated: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            onCreated(
                notes.addFile(
                    uri = picked.uri,
                    name = picked.name,
                    mime = picked.mime,
                    isImage = picked.isImage,
                    topicId = topicId,
                    albumId = albumId,
                ),
            )
        }
    }

    /**
     * Заводит пачку записей-файлов и отдаёт их номера.
     *
     * Одним заходом, а не вызовом [addFile] на каждый файл: экрану нужны все
     * номера разом — чтобы показать пачку выбранной и спросить про неё один
     * раз, а не по разу на файл.
     */
    fun addFiles(
        picked: List<PickedFile>,
        topicId: Long?,
        albumId: Long? = null,
        onCreated: (List<Long>) -> Unit,
    ) {
        if (picked.isEmpty()) return
        viewModelScope.launch {
            onCreated(
                picked.map { file ->
                    notes.addFile(
                        uri = file.uri,
                        name = file.name,
                        mime = file.mime,
                        isImage = file.isImage,
                        topicId = topicId,
                        albumId = albumId,
                    )
                },
            )
        }
    }

    /** Заводит пустую заметку и отдаёт её номер — экран сразу её открывает. */
    fun createNote(topicId: Long?, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(notes.createNote(topicId)) }
    }

    /**
     * Заводит альбом и отдаёт его номер: альбом чаще всего создают прямо в
     * карточке картинки, и её нужно сразу туда положить.
     */
    fun addAlbum(title: String, onCreated: (Long) -> Unit = {}) {
        if (title.isBlank()) return
        viewModelScope.launch { onCreated(notes.addAlbum(title)) }
    }

    fun renameAlbum(album: ImageAlbum, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { notes.renameAlbum(album, title) }
    }

    fun deleteAlbum(id: Long) {
        viewModelScope.launch { notes.deleteAlbum(id) }
    }

    /** Подпись и альбом картинки — то, что заполняют в карточке загрузки. */
    fun captionImage(note: Note, title: String, albumId: Long?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            notes.captionImage(note, title, albumId)
            onDone()
        }
    }

    fun addTopic(title: String, color: MarkColor? = null) {
        if (title.isBlank()) return
        viewModelScope.launch { notes.addTopic(title, color) }
    }

    /** Название и цвет корешка правятся вместе — их вместе и выбирают. */
    fun updateTopic(topic: ScrollTopic, title: String, color: MarkColor?) {
        if (title.isBlank()) return
        viewModelScope.launch { notes.updateTopic(topic, title, color) }
    }

    fun deleteTopic(id: Long) {
        viewModelScope.launch { notes.deleteTopic(id) }
    }

    fun delete(note: Note) {
        viewModelScope.launch { notes.delete(note) }
    }

    // ---- Голос ----

    /**
     * Начать запись.
     *
     * Файл заводится здесь, а не в диктофоне: заводит его папка Askya, и она
     * же умеет его убрать, если записи не случилось. Диктофону достаётся уже
     * открытый дескриптор — ровно то, что просит `MediaRecorder`.
     */
    fun startRecording() {
        if (recorder.state.value.going) return
        viewModelScope.launch {
            val place = voiceStore.create("golos") ?: return@launch
            recorder.start(place)
        }
    }

    /**
     * Закончить запись и завести по ней заметку.
     *
     * Заголовок — день и час: заметку наговаривают на бегу, и вопрос «как её
     * назвать» ровно в эту минуту стоил бы той мысли, ради которой её и
     * записывали. Переименовать можно потом, из карточки.
     *
     * Зовётся и по кнопке «Готово», и просто при уходе с экрана: микрофон в
     * фоне Askya не держит, а наговорённое до ухода терять нельзя.
     */
    fun stopRecording() {
        val done = recorder.stop() ?: return
        viewModelScope.launch {
            notes.addVoice(
                uri = done.uri,
                title = VOICE_STAMP.format(LocalDateTime.now()),
                durationMs = done.durationMs,
            )
        }
    }

    /** Бросить запись: ни заметки, ни файла не остаётся. */
    fun cancelRecording() = recorder.cancel()

    fun renameVoice(note: Note, title: String) {
        viewModelScope.launch { notes.save(note.copy(title = title.trim().ifBlank { "Голос" })) }
    }

    /** Убрать заметку — в ту же корзину на сутки, что запись, дело и трату. */
    fun removeVoice(note: Note) {
        viewModelScope.launch {
            notes.remove(note.id)
            trash.remembered(Trash.Kind.NOTE, note.id)
        }
    }

    /** Убрать выбранные картинки разом — так их убирают из сетки пачкой. */
    fun delete(images: List<Note>) {
        if (images.isEmpty()) return
        viewModelScope.launch { notes.delete(images) }
    }

    /**
     * Переложить выбранные картинки в альбом. [albumId] `null` означает «без
     * альбома»: так их вынимают обратно в общую сетку.
     */
    fun moveImages(ids: Set<Long>, albumId: Long?) {
        if (ids.isEmpty()) return
        viewModelScope.launch { notes.moveImages(ids.toList(), albumId) }
    }

    /**
     * Правка сохранена: запись переводится на новый файл, старый удаляется.
     * [onDone] зовётся уже после записи в базу — экран закрывается тогда,
     * когда в сетке будет что показать.
     */
    fun replaceImage(note: Note, uri: String, onDone: () -> Unit) {
        viewModelScope.launch {
            notes.replaceImage(note, uri)
            onDone()
        }
    }

    /**
     * Заводит запись на уже лежащую в папке Askya картинку — так приходит
     * коллаж. Разговора «как назовём» здесь нет: имя собрано из того, из чего
     * коллаж склеен, и спрашивать его отдельно незачем.
     */
    fun addImage(uri: String, name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            onCreated(notes.addFile(uri, name, "image/jpeg", isImage = true, topicId = null))
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer {
                ScrollViewModel(
                    container.noteRepository,
                    container.yetRepository,
                    container.voiceStore,
                    container.voiceRecorder,
                    container.trash,
                )
            }
        }
    }
}

/**
 * День и час, которыми подписывается только что наговорённая заметка:
 * «27 августа, 14:32».
 *
 * Язык задан явно, а не взят системный: приложение написано по-русски целиком,
 * и заметка, подписанная «27 August» на телефоне с английской системой,
 * выглядела бы чужой строкой в своём же списке.
 */
private val VOICE_STAMP: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale("ru"))
