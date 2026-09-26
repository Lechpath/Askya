package app.askya.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import app.askya.data.db.AppDatabase
import app.askya.data.images.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Когда слепок сделан — словами, а не машинной записью.
 *
 * Стоит здесь, а не в двух местах: те же слова показывает окно подтверждения
 * при восстановлении и строка «последний слепок» в настройках, и разойтись им
 * нельзя — человек сверяет одно с другим глазами.
 */
fun snapshotWhen(moment: LocalDateTime): String = SNAPSHOT_WHEN.format(moment)

private val SNAPSHOT_WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM, HH:mm")

/**
 * «Слепок» — всё, что помнит Askya, одним файлом.
 *
 * ## Зачем он есть
 *
 * Аккаунтов нет, облака нет, второго места, где живут данные, — тоже нет.
 * Разбитый или сброшенный телефон стирает записи за годы, дневник тела, книги
 * с закладками и списки. Системная копия Google этого не закрывает: без
 * аккаунта она молча не делается, режется на двадцати пяти мегабайтах и
 * никогда не говорит, что не получилась.
 *
 * Слепок устроен наоборот: его делает человек, руками, в тот момент, когда
 * решил, и кладёт туда, куда сам выбрал. Никакого облака и никакой
 * синхронизации: «Слепок» не должен превратиться в аккаунт.
 *
 * ## Что внутри
 *
 * Один zip:
 *
 * - `snapshot.json` — что это за файл: формат слепка, версия схемы базы,
 *   версия приложения, когда сделан и какие картинки внутри;
 * - `db/askya.db` — база целиком;
 * - `prefs` — хранилища DataStore, файлами как есть;
 * - `images` — файлы картинок из папки Askya.
 *
 * Вес будущего файла показывается до записи ([estimate]): «сделать копию» не
 * должно оборачиваться получасовым ожиданием без предупреждения.
 *
 * Версия схемы лежит внутри и сверяется при чтении. Слепок из **будущей**
 * версии принимать нельзя: там таблицы, о которых эта сборка не знает, и
 * миграции «назад» не бывает. Слепок из прошлой — можно: Room поднимет его
 * своими миграциями, ровно как поднимает базу после обновления приложения.
 *
 * ## Почему чтение идёт в два захода
 *
 * Базу нельзя подменить под открытым соединением, а картинки нельзя разложить,
 * не открыв уже подменённую базу: ссылки на них лежат в записях, и после
 * переезда на другой телефон они другие.
 *
 * Поэтому чтение разбито: [stageRestore] раскладывает базу и настройки и
 * оставляет картинки во временной папке, после чего приложение
 * перезапускается; при следующем запуске [finishRestore] переносит картинки в
 * папку Askya и переписывает ссылки в записях. Второй заход переживает то, что
 * его прервали: временная папка остаётся на месте, и он повторится при
 * следующем запуске.
 */
class Snapshots(
    private val context: Context,
    private val database: AppDatabase,
    private val images: ImageStore,
) {

    /** Что известно о слепке, не разбирая его целиком. */
    data class Info(
        val createdAt: String,
        val schema: Int,
        val app: String,
        val images: Int,
        /** Годен ли к чтению этой сборкой. */
        val readable: Boolean,
    )

    /** Имя, которое предлагается системному окну выбора места. */
    fun suggestedName(): String =
        "askya-" + LocalDateTime.now().toString().take(16).replace(":", "-") + ".zip"

    /**
     * Пишет слепок в выбранное человеком место и отдаёт его вес в байтах.
     *
     * Вес возвращается не для красоты: человек должен видеть, что файл вышел
     * не пустым, — «сделано» без числа ничем не отличается от «сделано вид».
     */
    suspend fun write(target: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
        runCatching {
            val staged = File(context.cacheDir, "snapshot-db")
            staged.delete()
            check(dumpDatabase(staged)) { "база не скопировалась" }

            val stream = context.contentResolver.openOutputStream(target)
                ?: error("файл не открылся на запись")

            stream.use { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    val shots = collectImages()

                    zip.put("snapshot.json") {
                        it.write(manifest(shots).toString(2).toByteArray())
                    }
                    zip.put("db/askya.db") { target2 -> staged.inputStream().use { it.copyTo(target2) } }

                    STORES.forEach { name ->
                        val file = storeFile(name)
                        if (!file.exists()) return@forEach
                        zip.put("prefs/" + name + ".preferences_pb") { target2 ->
                            file.inputStream().use { it.copyTo(target2) }
                        }
                    }

                    shots.forEach { shot ->
                        zip.put(shot.entry) { target2 ->
                            context.contentResolver.openInputStream(Uri.parse(shot.uri))
                                ?.use { it.copyTo(target2) }
                        }
                    }
                }
            }

            staged.delete()
            sizeOf(target)
        }
    }

    /**
     * Сколько примерно будет весить слепок.
     *
     * Примерно — потому что zip сожмёт базу и текст, а картинки сожмёт едва;
     * считать точно пришлось бы, записав файл. Человеку нужен порядок
     * величины: тридцать мегабайт или триста.
     */
    suspend fun estimate(): Long = withContext(Dispatchers.IO) {
        val db = runCatching { context.getDatabasePath(DB_NAME).length() }.getOrDefault(0L)
        val prefs = STORES.sumOf { name -> storeFile(name).length() }
        val shots = collectImages().sumOf { sizeOf(Uri.parse(it.uri)) }
        db + prefs + shots
    }

    /** Что за слепок лежит по ссылке. `null` — это вообще не слепок Askya. */
    suspend fun describe(source: Uri): Info? = withContext(Dispatchers.IO) {
        val json = readManifest(source) ?: return@withContext null
        val schema = json.optInt("schema", 0)
        Info(
            createdAt = whenMade(json.optString("createdAt")),
            schema = schema,
            app = json.optString("app"),
            images = json.optJSONArray("images")?.length() ?: 0,
            readable = schema in 1..AppDatabase.VERSION,
        )
    }

    /**
     * Раскладывает слепок: база и настройки встают на место, картинки ждут
     * второго захода. После этого приложение обязано перезапуститься — под
     * старым соединением подменённая база не живёт.
     */
    suspend fun stageRestore(source: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val staging = File(context.filesDir, STAGING)
            staging.deleteRecursively()
            staging.mkdirs()

            var manifest: JSONObject? = null
            val stream = context.contentResolver.openInputStream(source)
                ?: error("файл не открылся на чтение")

            stream.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        if (name == "snapshot.json") {
                            manifest = JSONObject(zip.readBytes().decodeToString())
                        } else if (
                            name.startsWith("db/") ||
                            name.startsWith("prefs/") ||
                            name.startsWith("images/")
                        ) {
                            zip.copyToFile(File(staging, safeName(name)))
                        }
                        zip.closeEntry()
                    }
                }
            }

            val json = manifest ?: error("это не слепок Askya")
            val schema = json.optInt("schema", 0)
            check(schema in 1..AppDatabase.VERSION) { "слепок сделан более новой версией Askya" }

            File(staging, "snapshot.json").writeText(json.toString())

            // Настройки — первыми: они мельче, и если что-нибудь оборвётся,
            // база останется прежней и целой.
            STORES.forEach { name ->
                val from = File(staging, safeName("prefs/" + name + ".preferences_pb"))
                if (!from.exists()) return@forEach
                val to = storeFile(name)
                to.parentFile?.mkdirs()
                from.copyTo(to, overwrite = true)
            }

            val db = File(staging, safeName("db/askya.db"))
            check(db.exists()) { "в слепке нет базы" }
            val target = context.getDatabasePath(DB_NAME)
            target.parentFile?.mkdirs()
            db.copyTo(target, overwrite = true)
            // Журнал старой базы к новой не подходит: он описывает страницы,
            // которых в ней нет. Оставленный, он испортил бы её при первом же
            // открытии.
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()
            Unit
        }
    }

    /** Ждёт ли прочитанный слепок второго захода — картинок. */
    fun restorePending(): Boolean = File(context.filesDir, STAGING).exists()

    /**
     * Второй заход: картинки переезжают в папку Askya, ссылки в записях
     * переписываются на новое место.
     *
     * Картинка, ссылка которой после подмены базы всё ещё открывается и ведёт
     * в нашу папку, не переписывается: слепок часто читают на том же телефоне,
     * и без этой проверки галерея набралась бы вторыми копиями.
     */
    suspend fun finishRestore() = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, STAGING)
        if (!staging.exists()) return@withContext

        val json = runCatching {
            JSONObject(File(staging, "snapshot.json").readText())
        }.getOrNull()

        val dao = database.noteDao()
        val list = json?.optJSONArray("images") ?: JSONArray()
        for (i in 0 until list.length()) {
            val shot = list.optJSONObject(i) ?: continue
            val note = dao.getById(shot.optLong("note")) ?: continue
            if (images.isOurs(note.uri)) continue

            val file = File(staging, safeName(shot.optString("entry")))
            if (!file.exists()) continue

            val moved = images.restore(
                name = shot.optString("name"),
                mime = shot.optString("mime", "image/jpeg"),
            ) { out -> file.inputStream().use { it.copyTo(out) } } ?: continue

            dao.update(note.copy(uri = moved))
        }

        staging.deleteRecursively()
    }

    // ——— внутренности ———

    private data class Shot(
        val noteId: Long,
        val uri: String,
        val name: String,
        val mime: String,
        val entry: String,
    )

    private suspend fun collectImages(): List<Shot> =
        database.noteDao().allImages()
            .filter { images.isOurs(it.uri) }
            .mapIndexed { index, note ->
                val uri = note.uri.orEmpty()
                val mime = images.mimeOf(uri)
                Shot(
                    noteId = note.id,
                    uri = uri,
                    name = note.title.ifBlank { "askya" },
                    mime = mime,
                    entry = "images/%04d.%s".format(index + 1, extensionOf(mime)),
                )
            }

    private fun manifest(shots: List<Shot>): JSONObject {
        fun rows(items: List<Shot>) = JSONArray().apply {
            items.forEach { shot ->
                put(
                    JSONObject().apply {
                        put("note", shot.noteId)
                        put("name", shot.name)
                        put("mime", shot.mime)
                        put("entry", shot.entry)
                    },
                )
            }
        }
        val json = JSONObject()
        json.put("format", FORMAT)
        json.put("schema", AppDatabase.VERSION)
        json.put("app", versionName())
        json.put("createdAt", LocalDateTime.now().toString())
        json.put("images", rows(shots))
        return json
    }

    /**
     * Копия базы для архива.
     *
     * `VACUUM INTO` там, где он есть (SQLite научился ему в Android 11): он
     * делает целую копию под открытым соединением, ни у кого не спрашивая. На
     * версиях постарше — сброс журнала в саму базу и обычное копирование
     * файла: после `TRUNCATE` журнал пуст, а файл базы полон.
     */
    private fun dumpDatabase(into: File): Boolean = runCatching {
        val db = database.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            db.query("VACUUM INTO ?", arrayOf<Any>(into.path)).use { it.moveToFirst() }
        } else {
            db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            File(db.path ?: error("у базы нет файла")).copyTo(into, overwrite = true)
        }
        into.length() > 0
    }.getOrDefault(false)

    private fun readManifest(source: Uri): JSONObject? = runCatching {
        var found: JSONObject? = null
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == "snapshot.json") {
                        found = JSONObject(zip.readBytes().decodeToString())
                        break
                    }
                    zip.closeEntry()
                }
            }
        }
        found
    }.getOrNull()

    /**
     * Когда слепок сделан — теми же словами, что стоят строчкой ниже в
     * настройках. Машинная запись из описи («2026-08-26T13:07») человеку в
     * окне подтверждения ничего не говорит, а решает он по ней.
     */
    private fun whenMade(iso: String): String = runCatching {
        snapshotWhen(LocalDateTime.parse(iso.take(19)))
    }.getOrDefault(iso.take(16).replace('T', ' '))

    private fun storeFile(name: String) =
        File(context.filesDir, "datastore/" + name + ".preferences_pb")

    private fun sizeOf(uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull() ?: 0L

    private fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /**
     * Имя записи архива, приведённое к безымянному файлу во временной папке.
     *
     * Имя внутри чужого zip бывает каким угодно, включая `../../`, — и
     * распаковка по такому имени пишет туда, куда указали не мы. Разделители
     * схлопываются, две точки убираются: остаётся ровно один файл в ровно
     * одной папке.
     */
    private fun safeName(entry: String): String =
        entry.replace('\\', '/').replace("..", "").replace("/", "__")

    private fun extensionOf(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

    private inline fun ZipOutputStream.put(name: String, body: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        body(this)
        closeEntry()
    }

    private fun InputStream.copyToFile(target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().use { copyTo(it) }
    }

    internal companion object {
        /** Формат самого слепка — не версия схемы и не версия приложения. */
        private const val FORMAT = 1

        private const val DB_NAME = "askya.db"
        private const val STAGING = "snapshot-restore"

        /**
         * Хранилища DataStore — по имени, которым они заведены. Аккаунта и
         * агента здесь нет и быть не должно: замок, ключ Claude и согласие на
         * облако со Слепком не переезжают (проверяет `SnapshotStoresTest`).
         */
        val STORES = listOf("settings", "echo", "reader", "video", "weather")
    }
}
