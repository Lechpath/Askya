package app.askya.data.sync

import androidx.room.Transactor.SQLiteTransactionType
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import app.askya.data.db.AppDatabase
import app.askya.domain.model.DeedLink
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Обмен: своё — в облако, чужое — в базу.
 *
 * ## Один круг
 *
 * [once] это два шага. Сперва [push]: всё, что журнал ([SyncSchema]) помечает
 * неотправленным, собирается в порцию, шифруется и ложится своим файлом в свою
 * папку. Потом [pull]: чужие папки перебираются, непрочитанные порции берутся,
 * расшифровываются и применяются к базе.
 *
 * Своё пишется только в свою папку, чужое только читается. Поэтому два
 * устройства не спорят за файл никогда, и облаку не нужно уметь ничего, кроме
 * «положи» и «дай».
 *
 * ## Кто побеждает
 *
 * У каждой строки есть отметка правки — гибридные часы. Пришедшая правка
 * старше своей — не применяется вовсе; свежее — ложится поверх. Это и есть
 * «побеждает поздняя правка», и для дела, счёта или строки списка этого
 * довольно: там одна строчка, и поздняя и есть верная.
 *
 * Записи Scroll — исключение ([SyncTables.Table.keepBoth]). Их пишут долго и с
 * двух сторон, и потерянный абзац не вернуть ничем. Поэтому, когда запись
 * правили на обоих устройствах порознь, поздняя версия остаётся в записи, а
 * своя, проигравшая, ложится рядом отдельной записью — «Покупки (с компьютера,
 * 14:20)». Лишнее удаляется одним касанием; пропавшее не возвращается.
 *
 * ## Чужое не уезжает обратно
 *
 * Пока идёт приём, поднят `applying`, и триггеры журнала молчат: иначе каждая
 * принятая правка тут же считалась бы своей и уезжала назад — эхо на двоих.
 * Отметки о принятом ставятся здесь же, руками, и сразу чистыми.
 *
 * Единственное исключение — копия спорной записи: её завело это устройство, и
 * уехать она должна, поэтому ей выдаётся своя отметка времени и `dirty`.
 */
class SyncEngine(
    private val database: AppDatabase,
    private val store: SyncStore,
    /** Ключ данных — тот, что отпирается паролем в [CloudAccount]. */
    private val key: ByteArray,
    /** Своя папка в облаке. */
    private val device: String,
    /** Чем подписывается своя проигравшая копия: «с компьютера», «с телефона». */
    private val from: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Чем кончился круг. Не для красоты: «синхронизировано» без чисел ничем не
     * отличается от «сделано вид», и в строке «Облако» человек увидит именно
     * эти числа.
     */
    data class Report(
        /** Строк уехало. */
        val sent: Int = 0,
        /** Строк принято. */
        val taken: Int = 0,
        /** Спорных записей отложено копией. */
        val kept: Int = 0,
        /** Строк не положено: не нашлось того, к чему они привязаны. */
        val lost: Int = 0,
        /** Порций не прочитано: порча, чужой ключ или схема из будущего. */
        val broken: Int = 0,
    ) {
        operator fun plus(other: Report) = Report(
            sent + other.sent,
            taken + other.taken,
            kept + other.kept,
            lost + other.lost,
            broken + other.broken,
        )

        val quiet: Boolean get() = sent == 0 && taken == 0
    }

    /**
     * Сперва принять чужое, потом отправить своё. Порядок не случайный: своя
     * правка, отправленная первой, уехала бы, не зная о чужой, и спор двух
     * устройств разошёлся бы ещё на круг. Приняв сперва, Askya отправляет уже
     * сведённое — вместе с копией спорной записи, если она понадобилась.
     */
    suspend fun once(): Report = pull() + push()

    /** Своё — в облако. */
    suspend fun push(): Report {
        var report = Report()
        // Круг за кругом, пока журнал не опустеет: первая синхронизация везёт
        // всё, что накопилось до неё, и в одну порцию это может не уместиться.
        while (true) {
            val rows = collect()
            if (rows.isEmpty()) return report
            val pack = Pack(
                schema = AppDatabase.VERSION,
                device = device,
                from = from,
                made = now(),
                rows = rows,
            )
            val number = nextNumber()
            val path = Pack.DEVICES + "/" + device + "/" + Pack.nameOf(number)
            store.write(path, Crypt.seal(key, pack.bytes(), path))
            store.write(Pack.DEVICES + "/" + device + "/" + Pack.HEAD, number.toString().toByteArray())
            mark(rows)
            report += Report(sent = rows.size)
            if (rows.size < PORTION) return report
        }
    }

    /** Чужое — в базу. */
    suspend fun pull(): Report {
        val seen = seen().toMutableMap()
        var report = Report()
        val taken = mutableListOf<Pack>()

        store.list(Pack.DEVICES).filter { it != device }.forEach { folder ->
            val read = seen[folder] ?: 0
            val numbers = store.list(Pack.DEVICES + "/" + folder)
                .mapNotNull(Pack::numberOf)
                .filter { it > read }
                .sorted()
            for (number in numbers) {
                val path = Pack.DEVICES + "/" + folder + "/" + Pack.nameOf(number)
                val sealed = store.read(path)
                val pack = sealed
                    ?.let { Crypt.open(key, it, path) }
                    ?.let { Pack.of(it) }
                // Непрочитанная порция не пропускается, а останавливает эту
                // папку: пропустить её значило бы потерять правки молча.
                // Чаще всего это порция от сборки новее нашей — обновятся, и
                // она прочтётся.
                if (pack == null || pack.schema > AppDatabase.VERSION) {
                    report += Report(broken = 1)
                    break
                }
                taken += pack
                seen[folder] = number
            }
        }

        if (taken.isEmpty()) return report
        report += apply(taken)
        store.write(Pack.DEVICES + "/" + device + "/" + Pack.SEEN, Json.write(seen).toByteArray())
        // Открытые окна ничего не знают о приезде чужих правок: их запросы
        // просыпаются от того, что база изменилась, а меняли её мимо Room.
        database.invalidationTracker.refreshAsync()
        return report
    }

    // --- своё ---------------------------------------------------------------

    /** Что ждёт отправки — строками, готовыми лечь в порцию. */
    private suspend fun collect(): List<Pack.Row> = database.useReaderConnection { connection ->
        val rows = Rows(connection)
        val waiting = rows.ask(
            "SELECT `tbl`, `uid`, `hlc`, `dead`, `base` FROM `sync_state` WHERE `dirty` = 1 " +
                "ORDER BY `hlc` LIMIT $PORTION",
        )
        waiting.mapNotNull { state ->
            val name = state["tbl"] as? String ?: return@mapNotNull null
            val uid = state["uid"] as? String ?: return@mapNotNull null
            val hlc = state["hlc"] as? Long ?: 0L
            val table = SyncTables.of(name) ?: return@mapNotNull null
            val base = state["base"] as? Long ?: 0L
            if ((state["dead"] as? Long ?: 0L) != 0L) {
                Pack.Row(table = name, uid = uid, hlc = hlc, base = base, dead = true)
            } else {
                val values = rows.read(table, uid) ?: return@mapNotNull null
                Pack.Row(name, uid, hlc, base, dead = false, values = outward(table, values, rows))
            }
        }
    }

    /**
     * Строка, как она уезжает: номера чужих строк заменены их именами.
     * Ссылка в никуда уезжает пустой — на другом устройстве её всё равно не на
     * что положить.
     */
    private suspend fun outward(
        table: SyncTables.Table,
        values: Map<String, Any?>,
        rows: Rows,
    ): Map<String, Any?> {
        val out = LinkedHashMap(values)
        table.refs.forEach { (column, target) ->
            if (!out.containsKey(column)) return@forEach
            val id = out[column] as? Long
            out[column] = id?.let { rows.uidOf(target, it) }
        }
        table.link?.let { column ->
            val link = DeedLink.of(out[column] as? String)
            val target = link?.let { SyncTables.LINKED[it.kind.key] }
            if (link != null && target != null) {
                val uid = rows.uidOf(target, link.id)
                out[column] = uid?.let { link.kind.key + ":" + it }
            }
        }
        return out
    }

    /**
     * Отмечает уехавшее. Отсюда же берётся отметка «на чём сошлись»: то, что
     * уехало, другое устройство теперь увидит, и следующая правка поверх
     * спором уже не будет. Правка, случившаяся после сбора, остаётся ждать —
     * её отметка времени больше, и условие по `hlc` её не тронет.
     */
    private suspend fun mark(rows: List<Pack.Row>) {
        database.useWriterConnection { connection ->
            connection.withTransaction(SQLiteTransactionType.IMMEDIATE) {
                val write = Rows(this)
                rows.forEach { row ->
                    write.run(
                        "UPDATE `sync_state` SET `dirty` = 0, `base` = ? " +
                            "WHERE `tbl` = ? AND `uid` = ? AND `hlc` <= ?",
                        row.hlc,
                        row.table,
                        row.uid,
                        row.hlc,
                    )
                }
            }
        }
    }

    private suspend fun nextNumber(): Int {
        val head = store.read(Pack.DEVICES + "/" + device + "/" + Pack.HEAD)
            ?.let { String(it).trim().toIntOrNull() }
        if (head != null) return head + 1
        // Нет отметки — считаем по папке: файлы важнее отметки о них.
        val last = store.list(Pack.DEVICES + "/" + device).mapNotNull(Pack::numberOf).maxOrNull()
        return (last ?: 0) + 1
    }

    private suspend fun seen(): Map<String, Int> {
        val raw = store.read(Pack.DEVICES + "/" + device + "/" + Pack.SEEN) ?: return emptyMap()
        val map = runCatching { Json.read(String(raw)) as Map<*, *> }.getOrNull() ?: return emptyMap()
        return map.entries.mapNotNull { (folder, number) ->
            val name = folder as? String ?: return@mapNotNull null
            val at = (number as? Long)?.toInt() ?: return@mapNotNull null
            name to at
        }.toMap()
    }

    // --- чужое --------------------------------------------------------------

    private suspend fun apply(packs: List<Pack>): Report =
        database.useWriterConnection { connection ->
            connection.withTransaction(SQLiteTransactionType.IMMEDIATE) {
                val rows = Rows(this)
                rows.run("UPDATE `sync_clock` SET `applying` = 1 WHERE `id` = 0")
                try {
                    merge(packs, rows)
                } finally {
                    rows.run("UPDATE `sync_clock` SET `applying` = 0 WHERE `id` = 0")
                }
            }
        }

    private suspend fun merge(packs: List<Pack>, rows: Rows): Report {
        var report = Report()
        val order = SyncTables.ALL.withIndex().associate { (at, table) -> table.name to at }
        // Родители раньше детей, и внутри таблицы — по времени правки: так у
        // ссылки почти всегда уже есть на что указывать.
        var queue = packs
            .flatMap { pack -> pack.rows.map { it to pack.device } }
            .sortedWith(compareBy({ order[it.first.table] ?: order.size }, { it.first.hlc }))

        // Если родитель всё же не доехал в свой черёд — круг повторяется, пока
        // кладётся хоть что-то. Оставшееся кладётся последним кругом как есть:
        // мягкая ссылка обнуляется, строка без обязательной ссылки теряется.
        while (true) {
            val left = mutableListOf<Pair<Pack.Row, String>>()
            var moved = false
            for (item in queue) {
                val done = row(item.first, item.second, rows, strict = true) { report += it }
                if (done) moved = true else left += item
            }
            if (left.isEmpty()) break
            if (!moved) {
                left.forEach { row(it.first, it.second, rows, strict = false) { report += it } }
                break
            }
            queue = left
        }

        val newest = packs.flatMap { it.rows }.maxOfOrNull { it.hlc } ?: 0L
        // Часы вперёд, до самой поздней чужой отметки: порядок правок должен
        // остаться общим у всех устройств.
        rows.run("UPDATE `sync_clock` SET `hlc` = MAX(`hlc`, ?) WHERE `id` = 0", newest + 1)
        return report
    }

    /** Одна чужая строка. `false` — не легла, потому что не нашлось родителя. */
    private suspend fun row(
        row: Pack.Row,
        theirs: String,
        rows: Rows,
        strict: Boolean,
        report: (Report) -> Unit,
    ): Boolean {
        val table = SyncTables.of(row.table) ?: return true
        val state = rows.one(
            "SELECT `hlc`, `dead`, `base` FROM `sync_state` WHERE `tbl` = ? AND `uid` = ?",
            row.table,
            row.uid,
        )
        val mine = state?.get("hlc") as? Long
        if (mine != null && !wins(row, theirs, mine, state["base"] as? Long ?: 0L)) return true

        if (row.dead) {
            rows.drop(row.table, row.uid)
            remember(rows, row.table, row.uid, row.hlc, dead = 1, dirty = 0, base = row.hlc)
            report(Report(taken = 1))
            return true
        }

        val values = inward(table, row.values.orEmpty(), rows, strict)
        if (values == null) {
            if (strict) return false
            report(Report(lost = 1))
            return true
        }

        // Спор виден по отметке: чужая правка оттолкнулась не от той версии,
        // что лежит здесь, — значит, правили порознь и не зная друг о друге.
        val fought = mine != null && mine != row.base && (state["dead"] as? Long ?: 0L) == 0L
        if (fought && table.keepBoth) {
            if (aside(table, row.uid, mine ?: 0L, rows)) report(Report(kept = 1))
        }

        rows.put(table, row.uid, values)
        remember(rows, row.table, row.uid, row.hlc, dead = 0, dirty = 0, base = row.hlc)
        report(Report(taken = 1))
        return true
    }

    /**
     * Чужая строка, как она ложится: имена превращаются в здешние номера.
     *
     * `null` — строка не кладётся: нет того, к чему она привязана. В строгий
     * круг это «подождём, вдруг родитель едет следом», в последний — «потеряно».
     */
    private suspend fun inward(
        table: SyncTables.Table,
        values: Map<String, Any?>,
        rows: Rows,
        strict: Boolean,
    ): Map<String, Any?>? {
        val out = LinkedHashMap(values)
        for ((column, target) in table.refs) {
            if (!out.containsKey(column)) continue
            val uid = out[column] as? String
            if (uid == null) {
                if (column in table.musts) return null
                out[column] = null
                continue
            }
            val id = rows.idOf(target, uid)
            if (id == null) {
                if (strict || column in table.musts) return null
                out[column] = null
            } else {
                out[column] = id
            }
        }
        table.link?.let { column ->
            val raw = out[column] as? String
            val kind = raw?.substringBefore(':')
            val target = kind?.let { SyncTables.LINKED[it] }
            if (raw != null && target != null) {
                val id = rows.idOf(target, raw.substringAfter(':', ""))
                if (id == null && strict) return null
                out[column] = id?.let { kind + ":" + it }
            }
        }
        return out
    }

    /**
     * Чья правка ложится: пришедшая или та, что лежит здесь.
     *
     * Обычно решает время. Но отметка времени у двух устройств может совпасть
     * до тысячной доли секунды — часы гибридные, и в младших битах у каждого
     * свой счёт, — а правки при этом разные. Оставить в таком случае каждому
     * своё значило бы развести устройства навсегда: обе стороны отвергали бы
     * чужое, и разошедшиеся записи не сошлись бы уже никогда.
     *
     * Поэтому при равном времени спор решается именем папки устройства —
     * произвольно, зато **одинаково на обеих сторонах**. Проигравшая версия не
     * пропадает: та сторона, чьё имя меньше, кладёт свою копию рядом и увозит
     * её следующей же порцией.
     *
     * Равное время и `base`, равный своему времени, — это не спор, а та же
     * самая версия, которая здесь уже лежит: её однажды приняли отсюда же.
     */
    private fun wins(row: Pack.Row, theirs: String, mine: Long, base: Long): Boolean = when {
        row.hlc > mine -> true
        row.hlc < mine -> false
        base == mine -> false
        else -> theirs > device
    }

    /**
     * Своя проигравшая версия — рядом, отдельной строкой. Заводит её это
     * устройство, поэтому она получает своё время правки и уезжает в облако
     * как всякая другая: на том устройстве обе версии тоже встанут рядом.
     */
    private suspend fun aside(
        table: SyncTables.Table,
        uid: String,
        hlc: Long,
        rows: Rows,
    ): Boolean {
        val mine = rows.read(table, uid) ?: return false
        val uidNew = Uid.new()
        val titled = table.title?.let { column ->
            mine + (column to sign(mine[column] as? String ?: "", hlc))
        } ?: mine
        rows.put(table, uidNew, titled)
        rows.run(
            "UPDATE `sync_clock` SET `hlc` = MAX(`hlc` + 1, ? * 4096) WHERE `id` = 0",
            now(),
        )
        val stamp = rows.one("SELECT `hlc` FROM `sync_clock` WHERE `id` = 0")?.get("hlc") as? Long
        remember(rows, table.name, uidNew, stamp ?: 0L, dead = 0, dirty = 1, base = 0)
        return true
    }

    /** «Покупки (с компьютера, 14:20)» — чтобы обе версии различались глазом. */
    private fun sign(title: String, hlc: Long): String {
        val millis = if (hlc > 0) hlc shr 12 else now()
        val at = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return title + " (" + from + ", " + at.format(WHEN) + ")"
    }

    private suspend fun remember(
        rows: Rows,
        table: String,
        uid: String,
        hlc: Long,
        dead: Int,
        dirty: Int,
        base: Long,
    ) {
        rows.run(
            "INSERT OR REPLACE INTO `sync_state` (`tbl`, `uid`, `hlc`, `dead`, `dirty`, `base`) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            table,
            uid,
            hlc,
            dead,
            dirty,
            base,
        )
    }

    private companion object {
        /** Сколько строк уезжает одной порцией. */
        const val PORTION = 2000

        val WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
