package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Запись о практике: что делал, сколько минут. Счётчики считаются по этим записям. */
@Entity(tableName = "practice_logs", indices = [Index("date"), Index("practice")])
data class PracticeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val practice: String,
    val minutes: Int,
    val comment: String = "",
)
