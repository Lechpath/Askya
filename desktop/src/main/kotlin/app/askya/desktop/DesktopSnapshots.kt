package app.askya.desktop

import androidx.room.useWriterConnection
import app.askya.data.db.AppDatabase
import app.askya.ui.components.MONTHS
import app.askya.ui.components.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * «Слепок» у Windows-версии — тот же файл, что делает телефон, и в обе
 * стороны: слепок телефона читается компьютером, слепок компьютера —
 * телефоном. Так данные переезжают между ними руками, как и договаривались:
 * синхронизации у Askya нет и не будет, а файл кладут туда, куда решили сами
 * (см. «Слепок» в README).
 *
 * Внутри один zip:
 * - `snapshot.json` — опись: формат, версия схемы базы, версия приложения,
 *   когда сделан и какие картинки внутри;
 * - `db/askya.db` — база целиком;
 * - `prefs` — хранилища настроек файлами как есть; у компьютера их три
 *   (`settings`, `reader`, `weather`), настройки Echo и AskyaV телефон при
 *   чтении оставляет свои;
 * - `images` — файлы картинок из папки Askya.
 *
 * **Слепок старшей схемы компьютер не поднимает.** Миграции базы с первой
 * версии живут у телефона и написаны на его SQLite; у компьютера базы старых
 * версий не бывает. Слепок, сделанный телефоном со старой Askya, компьютер
 * просит пересоздать, обновив Askya на телефоне, — чинить чужую базу
 * наугад хуже, чем сказать об этом.
 *
 * Чтение — в два захода, как у телефона: подменить базу под открытым
 * соединением нельзя. Первый заход раскладывает файлы во временную папку и
 * закрывает Askya; второй — при следующем запуске, до того как база
 * откроется ([applyPending]), и после — картинки со ссылками ([finishImages]).
 */
class DesktopSnapshots(private val container: DesktopContainer) {

    private val home get() = container.home
    private val staging get() = File(home, STAGING)

    data class Info(
        val createdAt: String,
        val schema: Int,
        val app: String,
        val images: Int,
        /** Годен ли к чтению этой сборкой: схема ровно та же. */
        val readable: Boolean,
    )

    fun suggestedName(): String =
        "askya-" + LocalDateTime.now().toString().take(16).replace(":", "-") + ".zip"

    /** Пишет слепок в [target] и отдаёт его вес в байтах. */
    suspend fun write(target: File): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val staged = File(home, "snapshot-db")
            staged.delete()
            dumpDatabase(staged)
            check(staged.length() > 0) { "база не скопировалась" }

            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                val shots = collectImages()
                zip.put("snapshot.json") { it.write(manifest(shots).toString(2).toByteArray()) }
                zip.put("db/askya.db") { out -> staged.inputStream().use { it.copyTo(out) } }
                DesktopContainer.STORES.forEach { name ->
                    val file = container.storeFile(name)
                    if (!file.exists()) return@forEach
                    zip.put("prefs/$name.preferences_pb") { out -> file.inputStream().use { it.copyTo(out) } }
                }
                shots.forEach { shot ->
                    zip.put(shot.entry) { out -> shot.file.inputStream().use { it.copyTo(out) } }
                }
            }

            staged.delete()
            target.length()
        }
    }

    /** Что за слепок лежит в файле; `null` — это не слепок Askya. */
    suspend fun describe(source: File): Info? = withContext(Dispatchers.IO) {
        val json = readManifest(source) ?: return@withContext null
        val schema = json.optInt("schema", 0)
        Info(
            createdAt = whenMade(json.optString("createdAt")),
            schema = schema,
            app = json.optString("app"),
            images = json.optJSONArray("images")?.length() ?: 0,
            readable = schema == AppDatabase.VERSION,
        )
    }

    /**
     * Первый заход чтения: слепок раскладывается во временную папку. После
     * этого Askya должна закрыться — база и настройки встанут на место при
     * следующем запуске, раньше, чем их кто-нибудь откроет.
     */
    suspend fun stageRestore(source: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            staging.deleteRecursively()
            staging.mkdirs()

            var manifest: JSONObject? = null
            ZipInputStream(source.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name == "snapshot.json") {
                        manifest = JSONObject(zip.readBytes().decodeToString())
                    } else if (name.startsWith("db/") || name.startsWith("prefs/") || name.startsWith("images/")) {
                        zip.copyToFile(File(staging, safeName(name)))
                    }
                    zip.closeEntry()
                }
            }

            val json = manifest ?: error("это не слепок Askya")
            val schema = json.optInt("schema", 0)
            check(schema <= AppDatabase.VERSION) { "слепок сделан более новой версией Askya" }
            check(schema == AppDatabase.VERSION) {
                "слепок сделан старой версией Askya на телефоне — обновите её и сделайте слепок заново"
            }
            check(File(staging, safeName("db/askya.db")).exists()) { "в слепке нет базы" }
            File(staging, "snapshot.json").writeText(json.toString())
            File(staging, READY).writeText("1")
        }.onFailure { staging.deleteRecursively() }
    }

    /**
     * Второй заход, до открытия базы: база и настройки встают на место.
     * Зовётся на запуске раньше всего остального; не было слепка — ничего.
     */
    fun applyPending() {
        if (!File(staging, READY).exists()) return
        DesktopContainer.STORES.forEach { name ->
            val from = File(staging, safeName("prefs/$name.preferences_pb"))
            if (!from.exists()) return@forEach
            val to = container.storeFile(name)
            to.parentFile?.mkdirs()
            from.copyTo(to, overwrite = true)
        }
        val db = File(staging, safeName("db/askya.db"))
        val target = File(home, AppDatabase.NAME)
        db.copyTo(target, overwrite = true)
        // Журнал старой базы к новой не подходит: он описывает страницы,
        // которых в ней нет.
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        File(staging, READY).delete()
    }

    /**
     * Третий шаг, уже по открытой базе: картинки переезжают в папку Askya, и
     * ссылки в записях переписываются на новое место. Прервали — временная
     * папка остаётся, и шаг повторится на следующем запуске.
     */
    suspend fun finishImages() = withContext(Dispatchers.IO) {
        if (!staging.exists() || File(staging, READY).exists()) return@withContext
        val json = runCatching { JSONObject(File(staging, "snapshot.json").readText()) }.getOrNull()
        val dao = container.database.noteDao()
        val images = container.imageStore
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

    private class Shot(val noteId: Long, val file: File, val name: String, val mime: String, val entry: String)

    private suspend fun collectImages(): List<Shot> {
        val images = container.imageStore
        return container.database.noteDao().allImages()
            .filter { images.isOurs(it.uri) }
            .mapNotNull { note -> app.askya.platform.fileOf(note.uri)?.takeIf(File::isFile)?.let { note to it } }
            .mapIndexed { index, (note, file) ->
                val mime = images.mimeOf(note.uri.orEmpty())
                Shot(
                    noteId = note.id,
                    file = file,
                    name = note.title.ifBlank { "askya" },
                    mime = mime,
                    entry = "images/%04d.%s".format(index + 1, extensionOf(mime)),
                )
            }
    }

    private fun manifest(shots: List<Shot>): JSONObject {
        val rows = JSONArray()
        shots.forEach { shot ->
            rows.put(
                JSONObject()
                    .put("note", shot.noteId)
                    .put("name", shot.name)
                    .put("mime", shot.mime)
                    .put("entry", shot.entry),
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("schema", AppDatabase.VERSION)
            .put("app", "Windows " + appVersion())
            .put("createdAt", LocalDateTime.now().toString())
            .put("images", rows)
    }

    /**
     * Копия базы под открытым соединением — `VACUUM INTO`, как у телефона на
     * Android 11 и новее: целая копия, ни у кого не спрашивая.
     */
    private suspend fun dumpDatabase(into: File) {
        val path = into.absolutePath.replace("'", "''")
        container.database.useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO '$path'") { it.step() }
        }
    }

    private fun readManifest(source: File): JSONObject? = runCatching {
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "snapshot.json") return@runCatching JSONObject(zip.readBytes().decodeToString())
                zip.closeEntry()
            }
        }
        null
    }.getOrNull()

    /**
     * «14 сентября, 08:12». Месяц — из русского списка [MONTHS], а не через
     * `Locale("ru")`: в Java, которую везёт с собой Askya.exe, русских названий
     * нет, и собранная Askya писала «14 Sep» — хотя из-под Gradle всё было
     * по-русски.
     */
    private fun whenMade(iso: String): String = runCatching {
        val made = LocalDateTime.parse(iso.take(19))
        "${made.dayOfMonth} ${MONTHS[made.monthValue - 1]}, ${formatTime(made.toLocalTime())}"
    }.getOrDefault(iso.take(16).replace('T', ' '))

    /** Имя записи архива — одним файлом во временной папке, без `../`. */
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

    private companion object {
        /** Формат самого слепка — тот же, что у телефона. */
        const val FORMAT = 1
        const val STAGING = "snapshot-restore"

        /** Метка «база и настройки ещё не встали на место». */
        const val READY = "apply-on-start"
    }
}

/**
 * Версия Windows-сборки — из установщика; из-под Gradle её нет.
 *
 * Установщику Windows нужны три числа («2.9.0»), а Askya знают по двум, как на
 * телефоне: нуль в конце отрезается, и в настройках обеих стоит «2.9».
 */
fun appVersion(): String {
    val full = System.getProperty("jpackage.app-version")
        ?: DesktopSnapshots::class.java.`package`?.implementationVersion
        ?: "2.9"
    return if (full.count { it == '.' } == 2) full.removeSuffix(".0") else full
}
