package app.askya.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.askya.data.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Новая сборка, лежащая в облаке.
 *
 * [size] = 0 значит «сервер не сказал, сколько весит»: полоска в этом случае
 * не рисуется — врущая полоска хуже её отсутствия, как и у закачек AskyaV.
 */
data class Release(
    val version: String,
    val notes: String,
    val link: String,
    val size: Long,
)

/** Где сейчас находится дело с обновлением. */
sealed interface UpdateState {

    /** Ничего не спрашивали и ничего не делаем. */
    data object Idle : UpdateState

    /** Спрашиваем у GitHub. */
    data object Asking : UpdateState

    /** Спросили: новее нет. [version] — та, что стоит. */
    data class Latest(val version: String) : UpdateState

    /** Есть новее. */
    data class Found(val build: Release) : UpdateState

    /** Качаем. [total] = 0 — длину не сказали. */
    data class Getting(val build: Release, val done: Long, val total: Long) : UpdateState

    /** Скачали; дальше окно установки показывает система. */
    data class Ready(val build: Release, val file: File) : UpdateState

    /** Не вышло. [reason] — человеку, а не в лог. */
    data class Failed(val reason: String) : UpdateState
}

/**
 * Обновление приложения из облака.
 *
 * ## Зачем это здесь
 *
 * Askya не лежит в магазине приложений, и до сих пор новая сборка приезжала
 * файлом на рабочий стол: собрать, перекинуть, найти в проводнике, нажать
 * «Установить». Это работало ровно до тех пор, пока рядом был компьютер.
 *
 * Теперь приложение умеет спросить само. Порядок ровно тот, о котором просили:
 * **спросили — есть ли новее; нет — не тронули ничего; есть — скачали и отдали
 * системе ставить.**
 *
 * ## Что уходит наружу
 *
 * Один запрос к `api.github.com` и, если сборка нашлась, скачивание файла с
 * `github.com`. В запросе нет ничего о человеке: ни имени, ни ключа, ни
 * телефона — GitHub отдаёт список выпусков публичного репозитория кому угодно
 * без всякого входа. Это второе место после погоды, где Askya выходит в сеть,
 * и оба выключаются насовсем: пустой адрес в настройках — и запроса нет.
 *
 * ## Почему приложение не ставит себя само
 *
 * Потому что не может и не должно. Установку показывает система своим окном, и
 * «да» в нём говорит человек. Askya только скачивает файл и передаёт на него
 * ссылку — `REQUEST_INSTALL_PACKAGES` даёт право попросить, а не право
 * поставить. Сборка при этом должна быть подписана тем же ключом, что стоящая:
 * иначе система откажет, и откажет правильно.
 *
 * ## Как сравниваются версии
 *
 * По имени выпуска: `v1.5` новее, чем `1.4`. Числами по частям, а не строками:
 * «1.10» строкой меньше «1.4», и по строкам обновление после десятого выпуска
 * перестало бы находиться навсегда.
 */
class Updates(
    private val context: Context,
    private val settings: SettingsPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Версия, которая стоит сейчас, — у системы, а не из BuildConfig. */
    val installed: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

    /** Есть ли куда смотреть: без адреса проверка не начинается вовсе. */
    fun configured(): Boolean = settings.state.value.updateSource.isNotBlank()

    /**
     * Спросить, нет ли сборки новее.
     *
     * Тихая проверка ([quiet]) отличается одним: она не показывает ни «ищу»,
     * ни «не вышло». Так проверяют при запуске — человек не просил, и
     * сообщение о том, что GitHub не ответил, было бы ему новостью ни о чём.
     */
    fun check(quiet: Boolean = false) {
        val source = settings.state.value.updateSource.trim()
        if (source.isBlank()) {
            if (!quiet) _state.value = UpdateState.Failed("Не сказано, откуда качать обновление")
            return
        }
        if (work?.isActive == true) return

        if (!quiet) _state.value = UpdateState.Asking
        work = scope.launch {
            val found = runCatching { ask(source) }
            val build = found.getOrNull()

            when {
                found.isFailure -> if (!quiet) {
                    _state.value = UpdateState.Failed(reasonOf(found.exceptionOrNull()))
                }

                build == null -> if (!quiet) {
                    _state.value = UpdateState.Latest(installed)
                }

                else -> _state.value = UpdateState.Found(build)
            }
        }
    }

    /**
     * Скачать найденную сборку.
     *
     * В кэш приложения, а не в библиотеку Askya: установочный файл — не то, что
     * человек складывает к себе, и оставлять сотню мегабайт в видимой папке
     * после установки было бы мусором. Кэш система чистит сама.
     */
    fun get(build: Release) {
        if (work?.isActive == true) return
        _state.value = UpdateState.Getting(build, 0, build.size)
        work = scope.launch {
            val file = runCatching { download(build) }
            val ready = file.getOrNull()
            _state.value = if (ready == null) {
                UpdateState.Failed(reasonOf(file.exceptionOrNull()))
            } else {
                UpdateState.Ready(build, ready)
            }
        }
    }

    /** Забыть, чем кончилось: окно закрыли. */
    fun forget() {
        work?.cancel()
        _state.value = UpdateState.Idle
    }

    /**
     * Отдать скачанное системному установщику.
     *
     * Ссылкой через `FileProvider`: `file://` на чужой процесс с Android 7
     * вылетает исключением, а установщик — чужой процесс.
     */
    fun install(file: File) {
        val uri: Uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull() ?: return

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Разрешено ли приложению просить об установке.
     *
     * С Android 8 это отдельное согласие на каждое приложение, и без него окно
     * установки не откроется вовсе — вместо него будет пустой отказ. Спросить о
     * нём лучше до скачивания сотни мегабайт, а не после.
     */
    fun mayInstall(): Boolean =
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) true
        else runCatching { context.packageManager.canRequestPackageInstalls() }
            .getOrDefault(false)

    /** Системный экран, где это согласие дают. */
    fun installConsent(): Intent = Intent(
        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Спросить у GitHub последний выпуск.
     *
     * `null` означает «новее нет» — и это не ошибка, а самый частый ответ.
     */
    private suspend fun ask(source: String): Release? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/${source.trim('/')}/releases/latest")
        val body = read(url)
        val release = JSONObject(body)

        val version = release.optString("tag_name").ifBlank { release.optString("name") }
        if (version.isBlank()) error("выпуск без версии")
        if (!newer(version, installed)) return@withContext null

        val asset = pickApk(release.optJSONArray("assets"))
            ?: error("в выпуске $version нет файла .apk")

        Release(
            version = version.trim().removePrefix("v").removePrefix("V"),
            notes = release.optString("body").trim().take(NOTES_LIMIT),
            link = asset.optString("browser_download_url"),
            size = asset.optLong("size"),
        )
    }

    /** Файл сборки среди вложений выпуска — первый, чьё имя кончается на .apk. */
    private fun pickApk(assets: JSONArray?): JSONObject? {
        val list = assets ?: return null
        for (at in 0 until list.length()) {
            val item = list.optJSONObject(at) ?: continue
            if (item.optString("name").endsWith(".apk", ignoreCase = true)) return item
        }
        return null
    }

    /**
     * Скачать файл, отсчитывая пройденное.
     *
     * Недокачанное убирается: половина установочного файла — это не «почти
     * обновление», а мусор, который система всё равно откажется ставить.
     */
    private suspend fun download(build: Release): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR)
        if (!dir.exists()) dir.mkdirs()
        // Одна и та же сборка не копится в кэше: имя по версии, и повторная
        // закачка ложится на место прежней.
        val target = File(dir, "askya-${build.version}.apk")

        val connection = open(URL(build.link))
        try {
            val total = if (build.size > 0) build.size else connection.contentLengthLong
            var done = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val chunk = ByteArray(CHUNK)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        output.write(chunk, 0, read)
                        done += read
                        _state.value = UpdateState.Getting(build, done, total)
                    }
                }
            }
            if (target.length() <= 0) error("файл пришёл пустым")
            target
        } catch (failure: Throwable) {
            runCatching { target.delete() }
            throw failure
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun read(url: URL): String {
        val connection = open(url)
        return try {
            connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Открыть соединение.
     *
     * Подпись обязательна: GitHub отвечает отказом запросам без `User-Agent`.
     * В ней имя приложения и версия — и больше ничего: ни ключа, ни телефона,
     * ни того, кто спрашивает.
     */
    private fun open(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = PATIENCE_MS
        connection.readTimeout = PATIENCE_MS
        connection.setRequestProperty("User-Agent", "Askya/$installed")
        connection.setRequestProperty("Accept", "application/vnd.github+json")

        val code = connection.responseCode
        if (code !in 200..299) {
            runCatching { connection.disconnect() }
            error(
                when (code) {
                    // 404 приходит и на несуществующий репозиторий, и на тот,
                    // где ещё не выложено ни одного выпуска. Различить их можно
                    // было бы вторым запросом, но человеку от этого не легче:
                    // делать в обоих случаях предстоит одно и то же — сходить и
                    // посмотреть, что там на самом деле.
                    404 -> "выпусков по этому адресу нет: либо репозиторий " +
                        "назван иначе, либо в нём ещё не выложено ни одной сборки"
                    403 -> "GitHub просит подождать: слишком много запросов подряд"
                    else -> "сервер ответил $code"
                },
            )
        }
        return connection
    }

    private fun reasonOf(failure: Throwable?): String = when (failure) {
        null -> "Не получилось"
        is java.net.UnknownHostException -> "Нет сети — проверить неоткуда"
        is java.net.SocketTimeoutException -> "Сервер не ответил вовремя"
        else -> failure.message?.replaceFirstChar { it.uppercase() } ?: "Не получилось"
    }

    private companion object {
        const val DIR = "updates"
        const val CHUNK = 64 * 1024
        const val PATIENCE_MS = 20_000
        const val NOTES_LIMIT = 1200
    }
}

/**
 * Новее ли [candidate], чем [current].
 *
 * Числами по частям: «1.10» строкой меньше «1.4», и по строкам обновление
 * после десятого выпуска перестало бы находиться навсегда. Всё, что не число,
 * из части выбрасывается — так «v1.5» и «1.5-beta» сравниваются с «1.5» как
 * одна и та же пятёрка.
 *
 * Не приватная и лежит рядом с [Updates]: у неё есть свои проверки в тестах —
 * ошибка здесь означала бы либо вечное «обновлений нет», либо предложение
 * поставить то, что уже стоит.
 */
fun newer(candidate: String, current: String): Boolean {
    val left = parts(candidate)
    val right = parts(current)
    if (left.isEmpty()) return false
    if (right.isEmpty()) return true
    for (at in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(at) { 0 }
        val b = right.getOrElse(at) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun parts(version: String): List<Int> = version
    .trim()
    .removePrefix("v")
    .removePrefix("V")
    .split('.', '-', '_', '+')
    .map { piece -> piece.takeWhile(Char::isDigit) }
    .filter { it.isNotEmpty() }
    .map { it.toIntOrNull() ?: 0 }
