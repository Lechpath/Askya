package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Запись в Scroll. Бывает двух видов, и различает их [uri]:
 *
 * - `uri == null` — текстовая заметка: заголовок и текст пишутся здесь же;
 * - `uri != null` — приложенный файл (pdf, md, картинка): содержимое лежит
 *   у системы, а здесь только ссылка на него и то, чем его открыть.
 *
 * Одной сущностью, а не двумя: в разделах они лежат вперемешку и сортируются
 * общим списком, а всё, чем они отличаются, — есть ли ссылка на файл.
 *
 * Файл не копируется внутрь приложения: хранится URI документа с постоянным
 * разрешением на чтение. Копия занимала бы место второй раз и устаревала бы
 * молча, когда человек правит файл в другом приложении.
 *
 * [topicId] `null` означает «без темы» — такие записи лежат в разделе «Файлы».
 * У картинок темы не бывает вовсе: «Изображения» отвязаны от «Книг» и собраны
 * своими папками — см. [albumId] и [ImageAlbum].
 *
 * Теги лежат списком строк прямо в записи, а не отдельной таблицей: для
 * фильтра по тегу этого достаточно, а join-таблица добавила бы сущность,
 * DAO и миграции без выигрыша.
 */
@Entity(
    tableName = "notes",
    indices = [Index("updatedAt"), Index("topicId"), Index("albumId")],
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val topicId: Long? = null,
    /**
     * Альбом картинки. `null` — «без альбома»: такая картинка видна в общей
     * сетке «Изображений» и больше нигде. Поле только для картинок: у заметок
     * и файлов есть книга, и складывать их ещё и в альбомы значило бы завести
     * второй способ раскладывать одно и то же.
     */
    val albumId: Long? = null,
    val uri: String? = null,
    val mime: String = "",
    /**
     * Картинка ли это. Считается один раз при добавлении и хранится: раздел
     * «Изображения» выбирает по этому полю, а разбирать mime в каждом запросе
     * SQL не умеет.
     */
    val isImage: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
