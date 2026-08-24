package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Чек-ин состояния. [mood], [energy] и [bodyPain] — по шкале 1..10; по энергии
 * и боли считается режим дня, поэтому шкала общая и достаточно подробная,
 * чтобы пороги режима (4, 6, 8) вообще различались.
 *
 * На день допускается несколько записей: утром и вечером состояние разное,
 * режим дня берётся по последней.
 */
@Entity(tableName = "check_ins", indices = [Index("date")])
data class CheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mood: Int,
    val energy: Int,
    val bodyPain: Int,
    val comment: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
