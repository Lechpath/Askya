package app.askya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.askya.domain.model.MarkColor
import java.time.LocalDateTime

/**
 * Тема в Scroll — папка, в которую складываются записи.
 *
 * Отдельная таблица, а не тег в самой записи: тему переименовывают целиком, и
 * с тегом-строкой это означало бы пройти по всем записям и переписать каждую.
 * Теги в записи остаются и живут своей жизнью — они про поиск, а тема про то,
 * где запись лежит.
 */
@Entity(tableName = "scroll_topics")
data class ScrollTopic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /**
     * Цвет корешка. `null` — «не выбирали»: цвет тогда выводится из названия,
     * и книга всё равно окрашена. См. [MarkColor].
     */
    val color: MarkColor? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
