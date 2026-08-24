package app.askya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Раздел профиля — то, что интервью узнало о человеке.
 *
 * Профиль хранится разделами, а не одним текстом: так его можно показать
 * списком и править по одному куску, а не редактировать простыню целиком.
 * Собрать обратно в текст для планировщика — дело одной склейки.
 *
 * [position] задаёт порядок из формата профиля: разделы там идут по смыслу,
 * а не по алфавиту, и сортировать их иначе значило бы ломать чтение.
 */
@Entity(tableName = "profile_sections")
data class ProfileSection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val title: String,
    val body: String,
)
