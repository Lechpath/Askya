package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.askya.data.sync.Uid
import java.time.LocalDateTime

/**
 * Альбом — папка внутри «Изображений».
 *
 * Отдельно от книг ([ScrollTopic]) намеренно. Раньше картинку клали в книгу, и
 * раздел «Изображения» был лишь витриной чужой полки: подписать снимок «Крым,
 * июль» значило завести книгу и складывать туда заметки с pdf'ами. Картинки
 * собирают по-своему — по поездке, по человеку, по дню, — и этому нужны свои
 * папки.
 *
 * Своя таблица, а не поле в записи: альбом переименовывают целиком, и строкой
 * в каждой картинке это означало бы пройти по всем и переписать.
 *
 * На диске альбомы не отражаются: все файлы лежат в одной папке Askya. Иначе
 * переименование альбома двигало бы файлы, а картинка, унесённая из папки
 * чужим проводником, теряла бы и альбом заодно.
 */
@Entity(tableName = "image_albums", indices = [Index("uid", unique = true)])
data class ImageAlbum(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Имя строки, общее для всех устройств, — см. [app.askya.data.sync.Uid].
     * Проставляется само и не меняется никогда: по нему строку узнают при
     * слиянии с другим устройством.
     */
    val uid: String = Uid.new(),
    val title: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
