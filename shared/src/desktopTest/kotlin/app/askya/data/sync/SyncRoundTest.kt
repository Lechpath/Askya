package app.askya.data.sync

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.askya.data.db.AppDatabase
import app.askya.data.db.MIGRATION_46_47_DESKTOP
import app.askya.data.db.MIGRATION_47_48_DESKTOP
import app.askya.data.db.SYNC_CALLBACK_DESKTOP
import app.askya.data.entity.DeedTask
import app.askya.data.entity.GeneratedDay
import app.askya.data.entity.Note
import app.askya.data.entity.ScheduleItem
import app.askya.data.entity.ScrollTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Синхронизация целиком — без сети и без Google.
 *
 * Две Askya с разными папками данных сходятся через третью папку на том же
 * компьютере. Это не выдумка ради теста: облако здесь — немой ящик
 * ([SyncStore]), и Диск в четвёртом этапе встанет на то же место, что папка
 * сейчас. Значит, всё, что проверяется тут, проверяется по-настоящему: порция,
 * шифр, перевод номеров строк в имена и обратно, правила слияния.
 *
 * Номера строк у двух устройств нарочно разведены: на втором заранее лежит
 * лишнее, поэтому седьмое дело у него седьмое, а у первого — двенадцатое. Тест,
 * в котором номера совпадают, не проверил бы главного.
 */
class SyncRoundTest {

    private val folder: File = Files.createTempDirectory("askya-sync-round").toFile()
    private val cloud = FolderStore(File(folder, "cloud"))
    private val key = Crypt.key()
    private val devices = mutableListOf<Device>()

    @AfterTest
    fun close() {
        devices.forEach { it.database.close() }
        folder.deleteRecursively()
    }

    /** Одна Askya: своя база, своя папка в облаке, своя подпись. */
    private inner class Device(name: String, val from: String, store: SyncStore = cloud) {

        val home = File(folder, name).also { it.mkdirs() }

        val database: AppDatabase = Room.databaseBuilder<AppDatabase>(
            name = File(home, AppDatabase.NAME).absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addMigrations(MIGRATION_46_47_DESKTOP, MIGRATION_47_48_DESKTOP)
            .addCallback(SYNC_CALLBACK_DESKTOP)
            .build()
            .also { devices += this }

        val sync = SyncEngine(database, store, key, device = name, from = from)

        suspend fun once() = sync.once()

        suspend fun pull() = sync.pull()

        /** Спросить базу напрямую — там, где у DAO такого вопроса нет. */
        fun ask(sql: String): List<String> {
            val connection = BundledSQLiteDriver().open(File(home, AppDatabase.NAME).absolutePath)
            val answer = mutableListOf<String>()
            connection.prepare(sql).use { statement ->
                while (statement.step()) answer += if (statement.isNull(0)) "" else statement.getText(0)
            }
            connection.close()
            return answer
        }

        fun count(sql: String): Int = ask(sql).size
    }

    private fun phone() = Device("phone", from = "с телефона")

    private fun computer() = Device("computer", from = "с компьютера")

    /** Разводит номера строк: у второго устройства свой счётчик. */
    private suspend fun Device.shiftNumbers() {
        repeat(5) { number ->
            val id = database.noteDao().insert(Note(title = "Своё $number"))
            database.noteDao().getById(id)?.let { database.noteDao().delete(it) }
        }
    }

    @Test
    fun `запись доезжает до другого устройства`() = runBlocking {
        val phone = phone()
        val computer = computer()
        computer.shiftNumbers()

        val id = phone.database.noteDao().insert(Note(title = "Первая", body = "с телефона"))
        val mine = assertNotNull(phone.database.noteDao().getById(id))

        assertEquals(1, phone.once().sent)
        val report = computer.once()

        assertEquals(1, report.taken)
        val theirs = computer.ask("SELECT title FROM notes WHERE uid = '" + mine.uid + "'")
        assertEquals(listOf("Первая"), theirs)
        // Номер у неё свой, имя — общее: в этом весь смысл uid.
        assertTrue(
            computer.ask("SELECT id FROM notes WHERE uid = '" + mine.uid + "'").single() != id.toString(),
            "номера строк должны разойтись, иначе тест ничего не проверяет",
        )
    }

    @Test
    fun `принятое не уезжает обратно`() = runBlocking {
        val phone = phone()
        val computer = computer()

        phone.database.noteDao().insert(Note(title = "Туда"))
        phone.once()
        computer.once()

        // Ничего своего компьютер не писал — значит, и отправлять ему нечего.
        assertEquals(0, computer.database.syncDao().dirtyCount())
        assertTrue(computer.once().quiet, "второй круг должен быть пустым")
        assertEquals(1, computer.count("SELECT id FROM notes"))
    }

    @Test
    fun `список внутри дела встаёт под своё дело, а не под чужой номер`() = runBlocking {
        val phone = phone()
        val computer = computer()
        computer.shiftNumbers()
        // У компьютера уже есть свои дела: номера обязаны разойтись.
        repeat(3) { number ->
            computer.database.scheduleDao().insert(
                ScheduleItem(
                    date = LocalDate.of(2026, 9, 16),
                    startTime = LocalTime.of(7, 0),
                    title = "Чужое $number",
                ),
            )
        }

        val deedId = phone.database.scheduleDao().insert(
            ScheduleItem(
                date = LocalDate.of(2026, 9, 16),
                startTime = LocalTime.of(9, 0),
                title = "Поездка",
            ),
        )
        phone.database.deedTaskDao().insert(DeedTask(deedId = deedId, text = "Взять пропуск"))

        phone.once()
        computer.once()

        val under = computer.ask(
            "SELECT t.text FROM deed_tasks t JOIN schedule_items s ON s.id = t.deedId " +
                "WHERE s.title = 'Поездка'",
        )
        assertEquals(listOf("Взять пропуск"), under)
    }

    @Test
    fun `привязка дела доезжает записью, а не номером`() = runBlocking {
        val phone = phone()
        val computer = computer()
        computer.shiftNumbers()

        val noteId = phone.database.noteDao().insert(Note(title = "Смета"))
        phone.database.scheduleDao().insert(
            ScheduleItem(
                date = LocalDate.of(2026, 9, 16),
                startTime = LocalTime.of(10, 0),
                title = "Считать смету",
                link = "note:$noteId",
            ),
        )

        phone.once()
        computer.once()

        val linked = computer.ask(
            "SELECT n.title FROM schedule_items s JOIN notes n ON ('note:' || n.id) = s.link " +
                "WHERE s.title = 'Считать смету'",
        )
        assertEquals(listOf("Смета"), linked)
    }

    @Test
    fun `удалённое уезжает удалённым и не возвращается`() = runBlocking {
        val phone = phone()
        val computer = computer()

        val id = phone.database.noteDao().insert(Note(title = "Ошибка"))
        phone.once()
        computer.once()
        assertEquals(1, computer.count("SELECT id FROM notes"))

        val mine = assertNotNull(phone.database.noteDao().getById(id))
        phone.database.noteDao().delete(mine)
        phone.once()
        computer.once()

        assertEquals(0, computer.count("SELECT id FROM notes"))
        // И назад её компьютер не отправит: у него метка, а не пустота.
        computer.once()
        phone.once()
        assertEquals(0, phone.count("SELECT id FROM notes"))
    }

    @Test
    fun `спорная запись ложится обеими версиями`() = runBlocking {
        val phone = phone()
        val computer = computer()

        val id = phone.database.noteDao().insert(Note(title = "Покупки", body = "хлеб"))
        val start = assertNotNull(phone.database.noteDao().getById(id))
        phone.once()
        computer.once()

        // Дальше оба правят одну запись, не зная друг о друге.
        val theirs = assertNotNull(
            computer.database.noteDao().getById(
                computer.ask("SELECT id FROM notes WHERE uid = '" + start.uid + "'").single().toLong(),
            ),
        )
        computer.database.noteDao().update(theirs.copy(body = "хлеб, молоко"))
        phone.database.noteDao().update(start.copy(body = "хлеб, сыр"))

        phone.once()
        val report = computer.once()

        assertEquals(1, report.kept, "своя версия должна лечь рядом, а не пропасть")
        val titles = computer.ask("SELECT title FROM notes ORDER BY id").sorted()
        assertEquals(2, titles.size, titles.toString())
        assertTrue(titles.any { it == "Покупки" }, titles.toString())
        assertTrue(
            titles.any { it.startsWith("Покупки (с компьютера, ") },
            "копия подписывается тем, чья она: " + titles,
        )
        // В самой записи — поздняя правка, то есть пришедшая с телефона.
        assertEquals(
            listOf("хлеб, сыр"),
            computer.ask("SELECT body FROM notes WHERE uid = '" + start.uid + "'"),
        )
        // И обе версии уезжают дальше: на телефоне тоже встанут рядом.
        computer.once()
        phone.once()
        assertEquals(2, phone.count("SELECT id FROM notes"))
    }

    @Test
    fun `у дела спорную правку решает время, а не копия`() = runBlocking {
        val phone = phone()
        val computer = computer()

        val id = phone.database.scheduleDao().insert(
            ScheduleItem(
                date = LocalDate.of(2026, 9, 16),
                startTime = LocalTime.of(9, 0),
                title = "Зарядка",
            ),
        )
        val mine = assertNotNull(phone.database.scheduleDao().getById(id))
        phone.once()
        computer.once()

        val theirs = assertNotNull(
            computer.database.scheduleDao().getById(
                computer.ask("SELECT id FROM schedule_items WHERE uid = '" + mine.uid + "'")
                    .single().toLong(),
            ),
        )
        computer.database.scheduleDao().update(theirs.copy(title = "Зарядка дома"))
        phone.database.scheduleDao().update(mine.copy(title = "Зарядка в зале"))

        phone.once()
        val report = computer.once()

        assertEquals(0, report.kept, "у дела копии быть не должно")
        assertEquals(1, computer.count("SELECT id FROM schedule_items"))
        assertEquals(
            listOf("Зарядка в зале"),
            computer.ask("SELECT title FROM schedule_items"),
        )
    }

    @Test
    fun `день, заполненный порознь, не задваивается`() = runBlocking {
        val phone = phone()
        val computer = computer()
        val date = LocalDate.of(2026, 9, 16)
        val routine = Uid.new()

        // Оба устройства развернули один и тот же распорядок в один и тот же
        // день — порознь, не зная друг о друге.
        listOf(phone, computer).forEach { device ->
            device.database.routineDao().markGenerated(GeneratedDay(date))
            device.database.scheduleDao().insert(
                ScheduleItem(
                    uid = Uid.ofRoutineDay(date, routine),
                    date = date,
                    startTime = LocalTime.of(7, 0),
                    title = "Завтрак",
                ),
            )
        }

        phone.once()
        computer.once()
        computer.once()
        phone.once()

        assertEquals(1, phone.count("SELECT id FROM schedule_items"), "дубль дела")
        assertEquals(1, computer.count("SELECT id FROM schedule_items"), "дубль дела")
        assertEquals(1, phone.count("SELECT date FROM generated_days"))
        assertEquals(1, computer.count("SELECT date FROM generated_days"))
    }

    @Test
    fun `книга доезжает раньше своей записи`() = runBlocking {
        val phone = phone()
        val computer = computer()
        computer.shiftNumbers()

        val topicId = phone.database.topicDao().insert(ScrollTopic(title = "Работа"))
        phone.database.noteDao().insert(Note(title = "Смета", topicId = topicId))

        phone.once()
        computer.once()

        val together = computer.ask(
            "SELECT t.title FROM notes n JOIN scroll_topics t ON t.id = n.topicId " +
                "WHERE n.title = 'Смета'",
        )
        assertEquals(listOf("Работа"), together)
    }

    @Test
    fun `чужим ключом порция не открывается`() = runBlocking {
        val phone = phone()
        phone.database.noteDao().insert(Note(title = "Не для чужих"))
        phone.once()

        val stranger = SyncEngine(
            database = Device("stranger", from = "с чужого").database,
            store = cloud,
            key = Crypt.key(),
            device = "stranger",
            from = "с чужого",
        )
        val report = stranger.pull()

        assertEquals(0, report.taken)
        assertTrue(report.broken > 0, "порция должна остаться закрытой")
    }

    @Test
    fun `тронутая порция не применяется`() = runBlocking {
        val phone = phone()
        val computer = computer()
        phone.database.noteDao().insert(Note(title = "Целая"))
        phone.once()

        val file = File(folder, "cloud/" + Pack.DEVICES + "/phone/" + Pack.nameOf(1))
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        file.writeBytes(bytes)

        val report = computer.once()

        assertEquals(0, report.taken)
        assertEquals(1, report.broken)
        assertEquals(0, computer.count("SELECT id FROM notes"))
    }

    @Test
    fun `порцию нельзя переложить в чужую папку`() = runBlocking {
        val phone = phone()
        val computer = computer()
        phone.database.noteDao().insert(Note(title = "Подменённая"))
        phone.once()

        // Тот же ключ, тот же файл — но лежит он теперь как чужой: путь входит
        // в подпись, и подмена не проходит.
        val moved = File(folder, "cloud/" + Pack.DEVICES + "/stranger/" + Pack.nameOf(1))
        moved.parentFile.mkdirs()
        File(folder, "cloud/" + Pack.DEVICES + "/phone/" + Pack.nameOf(1)).copyTo(moved)

        val report = computer.once()

        assertEquals(1, report.taken, "своя папка телефона читается")
        assertEquals(1, report.broken, "переложенная — нет")
    }

    @Test
    fun `схему из будущего это устройство не применяет`() = runBlocking {
        val computer = computer()
        // Room открывает базу лениво, на первом вопросе, а эта порция до базы
        // и не доходит — спрашиваем заранее, иначе спрашивать будет нечего.
        computer.database.noteDao().getById(1)
        val ahead = Pack(
            schema = AppDatabase.VERSION + 1,
            device = "phone",
            from = "с телефона",
            made = 0,
            rows = listOf(Pack.Row("notes", Uid.new(), 4096, values = mapOf("title" to "Из будущего"))),
        )
        val path = Pack.DEVICES + "/phone/" + Pack.nameOf(1)
        cloud.write(path, Crypt.seal(key, ahead.bytes(), path))

        val report = computer.pull()

        assertEquals(0, report.taken)
        assertEquals(1, report.broken)
        assertEquals(0, computer.count("SELECT id FROM notes"))
        // И не отмечена прочитанной: обновятся — прочтут.
        assertEquals(0, computer.pull().taken)
    }

    @Test
    fun `второй круг ничего не везёт`() = runBlocking {
        val phone = phone()
        val computer = computer()
        phone.database.noteDao().insert(Note(title = "Одна"))

        phone.once()
        computer.once()

        assertTrue(phone.once().quiet)
        assertTrue(computer.once().quiet)
        assertEquals(1, phone.count("SELECT id FROM notes"))
        assertEquals(1, computer.count("SELECT id FROM notes"))
    }

    @Test
    fun `в облако едут все таблицы журнала и ничего сверх`() {
        assertContentEquals(
            SyncSchema.TABLES.sorted(),
            SyncTables.ALL.map { it.name }.sorted(),
            "список таблиц журнала и список для слияния разошлись",
        )
    }

    @Test
    fun `у синхронизируемых таблиц нет двоичных колонок`() = runBlocking {
        val phone = phone()
        // Двоичную колонку порция увезти не умеет — значения ходят числом,
        // дробью и строкой. Появится такая — этот тест покраснеет раньше, чем
        // человек потеряет её содержимое.
        val binary = SyncSchema.TABLES.flatMap { table ->
            phone.ask("SELECT name || ':' || type FROM pragma_table_info('$table')")
                .filter { it.endsWith(":BLOB") }
                .map { table + "." + it }
        }
        assertEquals(emptyList(), binary)
    }

    @Test
    fun `пустой круг не оставляет файлов`() = runBlocking {
        val phone = phone()
        assertTrue(phone.once().quiet)
        assertNull(cloud.read(Pack.DEVICES + "/phone/" + Pack.nameOf(1)))
    }
}
