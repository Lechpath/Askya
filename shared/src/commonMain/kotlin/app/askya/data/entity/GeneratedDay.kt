package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.askya.data.sync.Uid
import java.time.LocalDate

/**
 * Отметка, что день уже развёрнут из распорядка.
 *
 * Без неё нельзя отличить «день ещё не заполнялся» от «пользователь вычистил
 * его сам»: и там и там блоков нет, и распорядок разворачивался бы заново
 * при каждом открытии, возвращая удалённое.
 */
@Entity(tableName = "generated_days", indices = [Index("uid", unique = true)])
data class GeneratedDay(
    @PrimaryKey val date: LocalDate,
    /**
     * Имя строки для облака — из самой даты ([app.askya.data.sync.Uid.ofGeneratedDay]).
     * Случайное развело бы две отметки об одном дне, заведённые двумя
     * устройствами порознь, а сойтись им было бы негде.
     */
    val uid: String = Uid.ofGeneratedDay(date),
)
