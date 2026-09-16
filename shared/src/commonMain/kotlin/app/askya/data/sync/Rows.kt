package app.askya.data.sync

import androidx.room.PooledConnection
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement

/**
 * Строки таблиц — как они читаются и кладутся при синхронизации: колонками, а
 * не сущностями.
 *
 * ## Почему не через DAO
 *
 * Таблиц тринадцать, и у каждой своя сущность, свои преобразователи и свой
 * DAO. Пройти их типами — это тринадцать переводов «сущность → набор полей» и
 * обратно, по полтора десятка полей в каждом; и каждая новая колонка в любой из
 * таблиц требовала бы вспомнить про это место. Синхронизации сущность не нужна
 * вовсе: ей всё равно, что лежит в колонке, — она везёт значение как есть.
 *
 * Поэтому здесь сырой SQL по именам колонок, а имена спрашиваются у самой базы
 * ([columns]). Колонка, добавленная миграцией, уезжает в облако сама, без
 * правки этого файла.
 *
 * ## Откуда берётся соединение
 *
 * Всё чтение и запись синхронизации идут одним соединением на запись
 * ([androidx.room.useWriterConnection]) и одной транзакцией: применённая
 * наполовину порция хуже непринятой. Room раздаёт соединение и на телефоне, и
 * на компьютере одинаково.
 *
 * Значения ходят теми же тремя видами, какими их знает SQLite: [Long], [Double]
 * и [String] (плюс `null`). Дат и перечислений здесь нет — они уже строки,
 * какими их записали преобразователи Room.
 */
internal class Rows(private val connection: PooledConnection) {

    private val known = mutableMapOf<String, List<String>>()

    /** Колонки таблицы — у самой базы, а не из списка в коде. */
    suspend fun columns(table: String): List<String> = known.getOrPut(table) {
        ask("PRAGMA table_info(`$table`)").mapNotNull { it["name"] as? String }
    }

    /** Есть ли такая строка. */
    suspend fun has(table: String, uid: String): Boolean =
        one("SELECT 1 FROM `$table` WHERE `uid` = ?", uid) != null

    /**
     * Строка целиком, без ключа и без имени: ключ на другом устройстве будет
     * свой, а имя едет в заголовке порции.
     */
    suspend fun read(table: SyncTables.Table, uid: String): Map<String, Any?>? {
        val row = one("SELECT * FROM `${table.name}` WHERE `uid` = ?", uid) ?: return null
        return row.filterKeys { it != "uid" && !(table.born && it == table.key) }
    }

    /** Номер строки по имени — чтобы переложить в него чужую ссылку. */
    suspend fun idOf(table: String, uid: String): Long? =
        one("SELECT `id` FROM `$table` WHERE `uid` = ?", uid)?.get("id") as? Long

    /** Имя строки по номеру — чтобы увезти ссылку именем, а не номером. */
    suspend fun uidOf(table: String, id: Long): String? =
        one("SELECT `uid` FROM `$table` WHERE `id` = ?", id)?.get("uid") as? String

    /**
     * Положить строку: правкой, если такая уже есть, иначе новой.
     *
     * Колонки, которых в этой базе нет, отбрасываются молча: так порция от
     * устройства с другой сборкой не роняет приём. Ключ не ставится никогда —
     * его раздаёт база.
     */
    suspend fun put(table: SyncTables.Table, uid: String, values: Map<String, Any?>): Boolean {
        val allowed = columns(table.name).toSet()
        val fields = values.filterKeys {
            it in allowed && it != "uid" && !(table.born && it == table.key)
        }
        if (has(table.name, uid)) {
            if (fields.isEmpty()) return true
            val set = fields.keys.joinToString(", ") { "`$it` = ?" }
            run("UPDATE `${table.name}` SET $set WHERE `uid` = ?", *fields.values.toTypedArray(), uid)
        } else {
            val names = (fields.keys + "uid").joinToString(", ") { "`$it`" }
            val holes = (fields.keys + "uid").joinToString(", ") { "?" }
            run(
                "INSERT INTO `${table.name}` ($names) VALUES ($holes)",
                *fields.values.toTypedArray(),
                uid,
            )
        }
        return true
    }

    suspend fun drop(table: String, uid: String) {
        run("DELETE FROM `$table` WHERE `uid` = ?", uid)
    }

    suspend fun ask(sql: String, vararg args: Any?): List<Map<String, Any?>> =
        connection.usePrepared(sql) { statement ->
            bind(statement, args)
            val out = mutableListOf<Map<String, Any?>>()
            while (statement.step()) out += row(statement)
            out
        }

    suspend fun one(sql: String, vararg args: Any?): Map<String, Any?>? =
        connection.usePrepared(sql) { statement ->
            bind(statement, args)
            if (statement.step()) row(statement) else null
        }

    suspend fun run(sql: String, vararg args: Any?) {
        connection.usePrepared(sql) { statement ->
            bind(statement, args)
            statement.step()
        }
    }

    private fun row(statement: SQLiteStatement): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>(statement.getColumnCount())
        for (at in 0 until statement.getColumnCount()) {
            out[statement.getColumnName(at)] = when (statement.getColumnType(at)) {
                SQLITE_DATA_INTEGER -> statement.getLong(at)
                SQLITE_DATA_FLOAT -> statement.getDouble(at)
                SQLITE_DATA_TEXT -> statement.getText(at)
                else -> null
            }
        }
        return out
    }

    private fun bind(statement: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { at, value ->
            val place = at + 1
            when (value) {
                null -> statement.bindNull(place)
                is String -> statement.bindText(place, value)
                is Long -> statement.bindLong(place, value)
                is Int -> statement.bindLong(place, value.toLong())
                is Boolean -> statement.bindLong(place, if (value) 1 else 0)
                is Double -> statement.bindDouble(place, value)
                else -> statement.bindText(place, value.toString())
            }
        }
    }
}
