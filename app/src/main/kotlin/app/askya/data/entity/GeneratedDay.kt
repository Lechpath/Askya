package app.askya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Отметка, что день уже развёрнут из распорядка.
 *
 * Без неё нельзя отличить «день ещё не заполнялся» от «пользователь вычистил
 * его сам»: и там и там блоков нет, и распорядок разворачивался бы заново
 * при каждом открытии, возвращая удалённое.
 */
@Entity(tableName = "generated_days")
data class GeneratedDay(
    @PrimaryKey val date: LocalDate,
)
