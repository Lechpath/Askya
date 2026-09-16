package app.askya.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Часы синхронизации — одна строка на базу.
 *
 * [hlc] гибридные: миллисекунды, сдвинутые на 12 бит, плюс счётчик в младших.
 * Сравнивать по ним можно как по числу, а сбитые часы телефона не отправляют
 * правку в прошлое: новая отметка всегда больше прошлой.
 *
 * [applying] поднят, пока применяются чужие правки: триггеры журнала на это
 * время замолкают, иначе принятое тут же считалось бы своим и уезжало назад.
 *
 * Правит их сама база — триггерами (`SyncSchema`); отсюда их читают и здесь же
 * подтягивают часы к чужой отметке.
 */
@Entity(tableName = "sync_clock")
data class SyncClock(
    @PrimaryKey val id: Int = 0,
    val hlc: Long = 0,
    val applying: Int = 0,
)

/**
 * Строка журнала: что известно об одной строке одной таблицы.
 *
 * [tbl] и [uid] — имя строки в облаке; [hlc] — когда её правили в последний
 * раз; [dead] — что её убрали (сама строка из таблицы уже исчезла, а знать об
 * удалении нужно, иначе другое устройство вернёт её обратно); [dirty] — что
 * правка ещё не уехала.
 */
@Entity(
    tableName = "sync_state",
    primaryKeys = ["tbl", "uid"],
    indices = [Index("dirty")],
)
data class SyncState(
    val tbl: String,
    val uid: String,
    val hlc: Long,
    val dead: Int = 0,
    val dirty: Int = 1,
)
