package app.askya.data.sync

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.askya.data.db.AppDatabase
import app.askya.data.db.MIGRATION_46_47_DESKTOP
import app.askya.data.db.MIGRATION_47_48_DESKTOP
import app.askya.data.db.SYNC_CALLBACK_DESKTOP
import app.askya.data.entity.Note
import app.askya.data.entity.ScheduleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Журнал правок — на настоящей базе, а не на выдуманной.
 *
 * Проверять здесь надо не «вызвался ли метод»: журнал ведут триггеры SQLite, и
 * ошибиться можно только в SQL. Поэтому база поднимается **версии 46** — из
 * выгруженной схемы, той самой, что стоит сейчас на телефоне, — на неё
 * накатывается миграция, и дальше всё спрашивается у базы.
 *
 * Отсюда же видно и то, ради чего миграция писалась: Room открывает
 * перенесённую базу и не жалуется на схему. Если бы в миграции недоставало
 * колонки или индекса, приложение упало бы при первом запуске после
 * обновления — у человека, а не здесь.
 */
class SyncJournalTest {

    private val folder: File = Files.createTempDirectory("askya-sync").toFile()
    private val file = File(folder, "askya.db")
    private var db: AppDatabase? = null

    @AfterTest
    fun close() {
        db?.close()
        folder.deleteRecursively()
    }

    /** База версии 46 — точь-в-точь такая, какой её оставила прошлая версия. */
    private fun oldDatabase() {
        val schema = JSONObject(File("schemas/app.askya.data.db.AppDatabase/46.json").readText())
            .getJSONObject("database")
        val connection: SQLiteConnection = BundledSQLiteDriver().open(file.absolutePath)
        connection.execSQL("PRAGMA user_version = 46")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val name = entity.getString("tableName")
            // Кавычки вокруг места уже стоят в самой схеме — имя подставляется голым.
            fun sql(text: String) = text.replace(TABLE_NAME, name)
            connection.execSQL(sql(entity.getString("createSql")))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                connection.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
        }
        // Отпечаток схемы: по нему Room понимает, что база и правда 46-й
        // версии, а не собрана неизвестно кем.
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
        )
        connection.execSQL(
            "INSERT OR REPLACE INTO room_master_table VALUES(42, '" +
                schema.getString("identityHash") + "')",
        )
        // Записанное до синхронизации: ему имена раздаст миграция.
        connection.execSQL(
            "INSERT INTO notes (title, body, tags, isImage, durationMs, mime, createdAt, updatedAt) " +
                "VALUES ('Старая запись', 'до облака', '', 0, 0, 'text/plain', " +
                "'2026-09-01T10:00:00', '2026-09-01T10:00:00')",
        )
        connection.execSQL("INSERT INTO generated_days (date) VALUES ('2026-09-01')")
        connection.close()
    }

    private fun open(): AppDatabase {
        oldDatabase()
        return Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_46_47_DESKTOP, MIGRATION_47_48_DESKTOP)
            .addCallback(SYNC_CALLBACK_DESKTOP)
            .build()
            .also { db = it }
    }

    @Test
    fun `база с телефона переносится и открывается`() = runBlocking {
        val database = open()
        val old = assertNotNull(database.noteDao().getById(1))

        assertEquals("Старая запись", old.title)
        // Имя выдано и не пустое — иначе строка не уехала бы никуда.
        assertEquals(32, old.uid.length, old.uid)

        // Записанное до синхронизации ждёт отправки: первый раз облако должно
        // увезти всё, что накопилось.
        val state = assertNotNull(database.syncDao().state("notes", old.uid))
        assertEquals(1, state.dirty)
        assertEquals(0, state.dead)
        assertEquals(0L, state.hlc, "у старого нет времени правки — оно было до часов")
    }

    @Test
    fun `отметка заполненного дня получает имя из своей даты`() = runBlocking {
        // Room открывает базу лениво, на первом вопросе: без него миграция не
        // тронулась бы с места, и спрашивать было бы нечего.
        open().noteDao().getById(1)

        assertEquals(
            listOf(Uid.ofGeneratedDay(LocalDate.of(2026, 9, 1))),
            ask("SELECT uid FROM generated_days"),
        )
    }

    @Test
    fun `новая запись попадает в журнал сама`() = runBlocking {
        val database = open()
        val id = database.noteDao().insert(Note(title = "Свежая"))
        val saved = assertNotNull(database.noteDao().getById(id))

        val state = assertNotNull(database.syncDao().state("notes", saved.uid))
        assertEquals(1, state.dirty)
        assertTrue(state.hlc > 0, "часы пошли")
    }

    @Test
    fun `правка двигает часы вперёд`() = runBlocking {
        val database = open()
        val id = database.noteDao().insert(Note(title = "Правится"))
        val saved = assertNotNull(database.noteDao().getById(id))
        val first = assertNotNull(database.syncDao().state("notes", saved.uid)).hlc

        database.noteDao().update(saved.copy(title = "Поправлена"))
        val second = assertNotNull(database.syncDao().state("notes", saved.uid)).hlc

        assertTrue(second > first, second.toString() + " должно быть больше " + first)
        // Имя строки правка не меняет: это то же дело, а не новое.
        assertEquals(saved.uid, assertNotNull(database.noteDao().getById(id)).uid)
    }

    @Test
    fun `удалённая строка остаётся в журнале мёртвой`() = runBlocking {
        val database = open()
        val id = database.noteDao().insert(Note(title = "Уйдёт"))
        val saved = assertNotNull(database.noteDao().getById(id))

        database.noteDao().delete(saved)

        val state = assertNotNull(database.syncDao().state("notes", saved.uid))
        assertEquals(1, state.dead, "иначе другое устройство вернёт строку обратно")
        assertEquals(1, state.dirty)
        assertNull(database.noteDao().getById(id))
    }

    @Test
    fun `на время приёма чужих правок журнал молчит`() = runBlocking {
        val database = open()
        database.syncDao().applying(1)
        val id = database.noteDao().insert(Note(title = "Пришла из облака"))
        val saved = assertNotNull(database.noteDao().getById(id))

        assertNull(
            database.syncDao().state("notes", saved.uid),
            "принятое не должно уезжать обратно — это было бы эхо на двоих",
        )

        database.syncDao().applying(0)
        database.noteDao().update(saved.copy(title = "Поправлена уже здесь"))
        assertNotNull(database.syncDao().state("notes", saved.uid))
        Unit
    }

    @Test
    fun `часы не идут назад и подтягиваются к чужой отметке`() = runBlocking {
        val database = open()
        val far = System.currentTimeMillis() * 4096 + 1_000_000
        database.syncDao().advance(far)
        assertEquals(far + 1, database.syncDao().clock())

        database.syncDao().advance(0)
        assertEquals(far + 1, database.syncDao().clock(), "чужая старая отметка часы не отводит")

        database.noteDao().insert(Note(title = "После чужой правки"))
        assertTrue(assertNotNull(database.syncDao().clock()) > far + 1)
    }

    @Test
    fun `день, заполненный дважды, даёт одно и то же имя дела`() {
        val date = LocalDate.of(2026, 9, 16)
        val routineUid = Uid.new()

        assertEquals(Uid.ofRoutineDay(date, routineUid), Uid.ofRoutineDay(date, routineUid))
        assertTrue(Uid.ofRoutineDay(date, routineUid) != Uid.ofRoutineDay(date.plusDays(1), routineUid))
        assertTrue(Uid.ofRoutineDay(date, routineUid) != Uid.ofRoutineDay(date, Uid.new()))
    }

    @Test
    fun `имена строк не повторяются`() = runBlocking {
        val database = open()
        val uids = (1..50).map { number ->
            val id = database.scheduleDao().insert(
                ScheduleItem(
                    date = LocalDate.of(2026, 9, 16),
                    startTime = LocalTime.of(9, 0),
                    title = "Дело " + number,
                ),
            )
            assertNotNull(database.scheduleDao().getById(id)).uid
        }

        assertEquals(50, uids.toSet().size)
    }

    /** Спросить базу напрямую — там, где у DAO такого вопроса нет. */
    private fun ask(sql: String): List<String> {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        val answer = mutableListOf<String>()
        connection.prepare(sql).use { statement ->
            while (statement.step()) answer += statement.getText(0)
        }
        connection.close()
        return answer
    }

    private companion object {
        /** Место таблицы в выгруженной схеме — Room подставляет туда имя. */
        const val TABLE_NAME = "$" + "{TABLE_NAME}"
    }
}
