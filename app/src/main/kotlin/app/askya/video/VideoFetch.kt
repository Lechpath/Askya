package app.askya.video

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

/**
 * Скачать видео по ссылке — и смотреть его потом как свой файл.
 *
 * ## Почему качать, а не смотреть по ссылке
 *
 * Просмотр по ссылке в разделе был и убран. Он работал ровно наполовину: своё
 * дело плеер делал, но половина адресов не открывалась — не из-за плеера, а
 * из-за той стороны. Чужой сервер отвечает `403` на запрос без нужного
 * заголовка, отдаёт поток кусками, рвёт соединение на перемотке, кладёт
 * видео за страницей. Человеку всё это видно одинаково: чёрный экран.
 *
 * Скачивание тем и лучше, что оно происходит **один раз**. Неудачу видно
 * сразу и словами, а удача кладёт файл в «Movies/Askya», где он уже ничем не
 * отличается от снятого телефоном: играет, режется, ложится в плейлист,
 * помнит место остановки и работает без сети.
 *
 * ## Что здесь понимается
 *
 * Три вещи, и в этом порядке:
 *
 * 1. **Прямая ссылка на файл** — `mp4`, `mkv`, `webm`, что угодно, что отдают
 *    целиком. Качается потоком, с показом того, сколько уже лежит.
 * 2. **Ссылка на HLS** (`m3u8`) — то, чем раздаётся почти всё сетевое видео:
 *    список кусков по несколько секунд. Куски скачиваются подряд и
 *    складываются в один файл. Список списков (разные качества) разбирается
 *    тоже: берётся лучшее. Зашифрованные `AES-128` куски расшифровываются —
 *    ключ лежит там же, рядом со списком, и это не взлом защиты, а часть
 *    самого формата.
 * 3. **Страница с видео** — если по ссылке пришла не запись, а `html`,
 *    страница просматривается на предмет того, где на ней лежит само видео:
 *    `<video src>`, `<source src>`, `og:video`, адреса `mp4` и `m3u8` в
 *    разметке и в скриптах. Найденное качается по первым двум правилам.
 *
 * ## Чего здесь нет и не будет
 *
 * **Сайтов, которые собирают адрес видео скриптом.** YouTube и его родня
 * выдают не адрес, а задачу: подпись, которую считает их же javascript,
 * меняющийся каждую неделю. Программы, которые это умеют, — это отдельное
 * ремесло на десятки тысяч строк, живущее обновлениями по нескольку раз в
 * месяц; внутри блокнота такому взяться неоткуда, и вид, что оно работает,
 * был бы обманом. С таких страниц Askya честно говорит, что видео не нашла.
 *
 * **Защищённого видео.** `DRM` — это не «спрятано похитрее», а «расшифровать
 * может только плеер с ключом от правообладателя». Такое не качается никак и
 * ничем, и обходить эту защиту раздел не станет.
 *
 * **Прямых эфиров.** У живого потока нет конца, и «скачать» его значит
 * качать, пока не кончится место. Список без `#EXT-X-ENDLIST` отвергается с
 * этими самыми словами.
 */
object VideoFetch {

    /**
     * Скачать то, что лежит по ссылке.
     *
     * [onNote] — что сейчас происходит, словами: поиск на странице, разбор
     * списка, само скачивание. Шагов у скачивания три, и они разной длины;
     * молчащая полоска на первых двух читалась бы как зависание.
     *
     * [onStep] — сколько сделано и сколько всего: байтами у файла, кусками у
     * потока. Ноль вторым числом означает «неизвестно» — так отвечают
     * серверы, не сказавшие длину.
     */
    suspend fun grab(
        context: Context,
        store: VideoStore,
        link: String,
        onNote: (String) -> Unit = {},
        onStep: (Long, Long) -> Unit = { _, _ -> },
    ): EditResult = withContext(Dispatchers.IO) {
        val start = runCatching { URL(link.trim()) }.getOrNull()
            ?: return@withContext EditResult.Failed("Это не ссылка")

        if (start.protocol !in listOf("http", "https")) {
            return@withContext EditResult.Failed(
                "Скачивается то, что лежит в сети по http или https. " +
                    "Файл с телефона открывается папкой в шапке.",
            )
        }

        val found = runCatching { look(start, onNote) }.getOrElse { failure ->
            // Брошенная закачка — не неудача: о ней уже сказано словом
            // «Брошено», и второе объяснение затёрло бы его.
            coroutineContext.ensureActive()
            return@withContext EditResult.Failed(reasonOf(failure))
        } ?: return@withContext EditResult.Failed(
            "На этой странице видео не нашлось. Так отвечают сайты, которые " +
                "собирают адрес ролика своим скриптом, — их адрес не лежит в " +
                "разметке, и взять его неоткуда.",
        )

        runCatching {
            when (found.kind) {
                Kind.FILE -> whole(context, store, found, onNote, onStep)
                Kind.HLS -> pieces(context, store, found, onNote, onStep)
            }
        }.getOrElse { failure ->
            coroutineContext.ensureActive()
            EditResult.Failed(reasonOf(failure))
        }
    }

    /** Что нашлось по ссылке: сам адрес, откуда на него сослались, и чем он является. */
    private data class Found(
        val url: URL,
        val referer: String?,
        val kind: Kind,
        val name: String,
    )

    private enum class Kind { FILE, HLS }

    /**
     * Что лежит по адресу.
     *
     * Спрашивается обычным запросом, а не `HEAD`: половина серверов отвечает
     * на `HEAD` отказом или враньём, а начатую и брошенную загрузку они
     * переживают спокойно — так работает любой браузер.
     *
     * Со страницы разрешён один переход: страница → видео. Двух не бывает, а
     * ходить по ссылкам вглубь — это уже обход сайта, а не разбор адреса.
     */
    private suspend fun look(from: URL, onNote: (String) -> Unit, depth: Int = 0): Found? {
        onNote(if (depth == 0) "Смотрю, что по ссылке" else "Ищу видео на странице")

        val referer = if (depth == 0) null else from.toString()
        val connection = open(from, referer)
        try {
            val kind = connection.contentType.orEmpty().substringBefore(';').trim().lowercase()
            val here = connection.url ?: from
            val named = nameOf(connection, here)

            // Список кусков узнаётся и по подписи, и по хвосту адреса:
            // раздают его под доброй дюжиной разных подписей.
            val playlist = kind.contains("mpegurl") || kind.contains("m3u") ||
                here.path.orEmpty().endsWith(".m3u8", ignoreCase = true)

            return when {
                playlist -> Found(here, referer, Kind.HLS, named)

                kind.startsWith("video/") || kind.startsWith("audio/") ||
                    kind == "application/octet-stream" ->
                    Found(here, referer, Kind.FILE, named)

                kind.startsWith("text/html") || kind.contains("xhtml") -> {
                    if (depth > 0) return null
                    val page = connection.inputStream.use { it.text(PAGE_LIMIT) }
                    val inside = insideOf(page, here) ?: return null
                    look(inside, onNote, depth + 1)
                }

                // Подпись бывает и пустая, и «application/force-download»:
                // тогда верим хвосту адреса, а не подписи.
                fileLike(here) -> Found(here, referer, Kind.FILE, named)

                else -> null
            }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /** Целый файл — потоком, с показом того, сколько уже лежит. */
    private suspend fun whole(
        context: Context,
        store: VideoStore,
        found: Found,
        onNote: (String) -> Unit,
        onStep: (Long, Long) -> Unit,
    ): EditResult {
        onNote("Качаю")

        val connection = open(found.url, found.referer)
        val total = connection.contentLengthLong.coerceAtLeast(0)
        val pending = store.create(found.name, mimeOf(found.url, connection.contentType))
            ?: return EditResult.Failed("Не удалось завести файл в папке ${store.folderName}")

        try {
            var done = 0L
            connection.inputStream.use { input ->
                FileOutputStream(pending.fileDescriptor).use { out ->
                    val chunk = ByteArray(CHUNK)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(chunk)
                        if (read < 0) break
                        out.write(chunk, 0, read)
                        done += read
                        onStep(done, total)
                    }
                    out.fd.sync()
                }
            }
            if (done == 0L) {
                pending.cancel()
                return EditResult.Failed("Сервер отдал пустой ответ")
            }
            return EditResult.Done(pending.done(), pending.displayName)
        } catch (failure: Throwable) {
            pending.cancel()
            throw failure
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    /**
     * Поток кусками: разобрать список и сложить куски в один файл.
     *
     * Куски пишутся подряд, как есть, — контейнер `ts` для того и придуман,
     * чтобы его можно было резать и склеивать в любом месте. У кусков вида
     * `fmp4` в начале стоит свой заголовок (`#EXT-X-MAP`), и он пишется
     * первым: без него склеенное — это набор кусков без начала.
     */
    private suspend fun pieces(
        context: Context,
        store: VideoStore,
        found: Found,
        onNote: (String) -> Unit,
        onStep: (Long, Long) -> Unit,
    ): EditResult {
        onNote("Разбираю список")

        var url = found.url
        var text = read(url, found.referer)

        // Список списков: разные качества одного и того же. Берётся лучшее —
        // человек качает, чтобы смотреть, а не чтобы сэкономить трафик,
        // который уже потрачен на выбор.
        if (text.contains("#EXT-X-STREAM-INF")) {
            onNote("Выбираю качество")
            url = bestOf(text, url) ?: return EditResult.Failed(
                "В списке качеств не нашлось ни одной дорожки",
            )
            text = read(url, found.referer)
        }

        if (!text.contains("#EXT-X-ENDLIST")) {
            return EditResult.Failed(
                "Это прямой эфир: у него нет конца, и скачать его целиком нельзя. " +
                    "Смотрят такое плеером, а не папкой.",
            )
        }

        val list = playlistOf(text, url)
        if (list.parts.isEmpty()) return EditResult.Failed("Список кусков оказался пуст")

        val lock = list.key
        if (lock != null && lock.method != "AES-128") {
            return EditResult.Failed(
                "Это видео защищено (${lock.method}): расшифровать его может только " +
                    "плеер с ключом от правообладателя.",
            )
        }
        val key = lock?.let {
            onNote("Беру ключ")
            bytesOf(it.url, found.referer)
        }

        val mime = if (list.header != null || list.parts.first().url.path.orEmpty()
                .endsWith(".mp4", ignoreCase = true)
        ) {
            "video/mp4"
        } else {
            "video/mp2t"
        }
        val pending = store.create(found.name, mime)
            ?: return EditResult.Failed("Не удалось завести файл в папке ${store.folderName}")

        try {
            onNote("Собираю куски")
            FileOutputStream(pending.fileDescriptor).use { out ->
                list.header?.let { header ->
                    out.write(bytesOf(header, found.referer))
                }
                list.parts.forEachIndexed { at, part ->
                    coroutineContext.ensureActive()
                    val raw = bytesOf(part.url, found.referer)
                    out.write(if (key == null) raw else unlock(raw, key, lock, part.sequence))
                    onStep((at + 1).toLong(), list.parts.size.toLong())
                }
                out.fd.sync()
            }
            return EditResult.Done(pending.done(), pending.displayName)
        } catch (failure: Throwable) {
            pending.cancel()
            throw failure
        }
    }

    /** Один кусок списка: адрес и его номер — по номеру считается вектор шифра. */
    private data class Part(val url: URL, val sequence: Long)

    /** Замок списка: чем и откуда. */
    private data class Lock(val method: String, val url: URL, val iv: ByteArray?)

    private data class Playlist(
        val parts: List<Part>,
        val header: URL?,
        val key: Lock?,
    )

    /** Разбор списка кусков — построчно, как он и написан. */
    private fun playlistOf(text: String, from: URL): Playlist {
        val parts = mutableListOf<Part>()
        var header: URL? = null
        var key: Lock? = null
        var sequence = 0L

        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> Unit

                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") ->
                    sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L

                line.startsWith("#EXT-X-MAP:") ->
                    header = attribute(line, "URI")?.let { resolve(it, from) }

                line.startsWith("#EXT-X-KEY:") -> {
                    val method = attribute(line, "METHOD").orEmpty()
                    val at = attribute(line, "URI")?.let { resolve(it, from) }
                    key = if (method == "NONE" || at == null) {
                        null
                    } else {
                        Lock(method, at, attribute(line, "IV")?.let { hex(it) })
                    }
                }

                line.startsWith("#") -> Unit

                else -> resolve(line, from)?.let { parts += Part(it, sequence + parts.size) }
            }
        }
        return Playlist(parts, header, key)
    }

    /** Лучшая дорожка списка качеств — по заявленной скорости. */
    private fun bestOf(text: String, from: URL): URL? {
        var best: URL? = null
        var bestRate = -1L

        val lines = text.lines()
        for (at in lines.indices) {
            val line = lines[at].trim()
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val rate = attribute(line, "BANDWIDTH")?.toLongOrNull() ?: 0L
            val next = lines.drop(at + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?: continue
            if (rate >= bestRate) {
                bestRate = rate
                best = resolve(next.trim(), from)
            }
        }
        return best
    }

    /**
     * Расшифровать кусок.
     *
     * Вектор берётся из списка, а если его там нет — из номера куска: так
     * сказано в самом описании формата, и это не догадка.
     */
    private fun unlock(raw: ByteArray, key: ByteArray, lock: Lock?, sequence: Long): ByteArray {
        val iv = lock?.iv ?: ByteArray(16).also { vector ->
            for (at in 0 until 8) {
                vector[15 - at] = ((sequence shr (8 * at)) and 0xFF).toByte()
            }
        }
        val secret = SecretKeySpec(key, "AES")
        // Дополнение по стандарту есть, но кладут его не все: кусок без него
        // расшифровывается тем же ключом и без дополнения.
        return runCatching {
            Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))
            }.doFinal(raw)
        }.getOrElse {
            Cipher.getInstance("AES/CBC/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, secret, IvParameterSpec(iv))
            }.doFinal(raw)
        }
    }

    /**
     * Где на странице лежит само видео.
     *
     * Разбирается не деревом, а поиском по разметке: страница с видео —
     * это почти всегда либо тег `video`, либо подпись `og:video` для
     * пересылок, либо адрес, написанный прямо в скрипте плеера. Полноценный
     * разбор `html` не дал бы ничего сверх этого: в дереве адреса плеера всё
     * равно нет — он в тексте скрипта.
     *
     * Прямой файл предпочитается списку кусков: `mp4` качается одним махом и
     * ложится готовым файлом, а список — это сотни запросов.
     */
    private fun insideOf(page: String, from: URL): URL? {
        val seen = LinkedHashSet<String>()

        for (pattern in TAGS) {
            pattern.findAll(page).forEach { hit ->
                hit.groupValues.getOrNull(1)?.let { seen += it }
            }
        }
        LOOSE.findAll(page).forEach { hit -> seen += hit.value }

        val links = seen
            .map { it.replace("\\/", "/").replace("&amp;", "&").trim() }
            .filter { it.isNotBlank() && !it.startsWith("data:") && !it.startsWith("blob:") }

        val direct = links.firstOrNull { fileLike(it) }
        val stream = links.firstOrNull { it.substringBefore('?').endsWith(".m3u8", true) }
        return (direct ?: stream)?.let { resolve(it, from) }
    }

    private fun fileLike(url: URL): Boolean = fileLike(url.toString())

    private fun fileLike(link: String): Boolean {
        val path = link.substringBefore('?').substringBefore('#').lowercase()
        return FILE_ENDS.any { path.endsWith(it) }
    }

    /** Имя будущего файла: из заголовка ответа, из адреса или из узла. */
    private fun nameOf(connection: HttpURLConnection, url: URL): String {
        val told = connection.getHeaderField("Content-Disposition").orEmpty()
        val quoted = Regex("filename\\*?=(?:UTF-8''|\")?([^\";]+)")
            .find(told)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted.substringBeforeLast('.', quoted)

        val tail = url.path.orEmpty().substringAfterLast('/')
        val base = tail.substringBeforeLast('.', tail)
        return when {
            base.isNotBlank() && base != "index" && base != "master" -> base
            !url.host.isNullOrBlank() -> url.host.removePrefix("www.")
            else -> "video"
        }
    }

    private fun mimeOf(url: URL, told: String?): String {
        val said = told.orEmpty().substringBefore(';').trim().lowercase()
        if (said.startsWith("video/") || said.startsWith("audio/")) return said
        val path = url.path.orEmpty().lowercase()
        return when {
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".ts") -> "video/mp2t"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".m4a") || path.endsWith(".mp3") -> "audio/mp4"
            else -> "video/mp4"
        }
    }

    /**
     * Открыть соединение так, как его открыл бы браузер.
     *
     * Подпись обозревателя и адрес страницы, с которой пришли, — не хитрость,
     * а то, чего от запроса ждёт сервер: без них половина хостингов отвечает
     * отказом всем подряд. Ничего о человеке в этих заголовках нет: ни имени,
     * ни печенья, ни адреса — они те же самые у всякого, кто качает.
     */
    private fun open(url: URL, referer: String?): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = PATIENCE_MS
        connection.readTimeout = PATIENCE_MS
        connection.setRequestProperty("User-Agent", BROWSER)
        connection.setRequestProperty("Accept", "*/*")
        referer?.let { connection.setRequestProperty("Referer", it) }

        val code = connection.responseCode
        if (code !in 200..299) {
            val reason = connection.responseMessage.orEmpty()
            runCatching { connection.disconnect() }
            error("сервер ответил $code${if (reason.isBlank()) "" else " $reason"}")
        }
        return connection
    }

    private fun read(url: URL, referer: String?): String {
        val connection = open(url, referer)
        return try {
            connection.inputStream.use { it.text(PAGE_LIMIT) }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun bytesOf(url: URL, referer: String?): ByteArray {
        val connection = open(url, referer)
        return try {
            connection.inputStream.use { it.readBytes() }
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun InputStream.text(limit: Int): String {
        val out = StringBuilder()
        val chunk = ByteArray(CHUNK)
        var taken = 0
        while (taken < limit) {
            val read = read(chunk)
            if (read < 0) break
            out.append(String(chunk, 0, read, Charsets.UTF_8))
            taken += read
        }
        return out.toString()
    }

    /** Значение поля в строке списка: `BANDWIDTH=123`, `URI="…"`. */
    private fun attribute(line: String, name: String): String? =
        Regex("$name=(\"[^\"]*\"|[^,]*)").find(line)
            ?.groupValues?.getOrNull(1)
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }

    private fun hex(value: String): ByteArray {
        val clean = value.removePrefix("0x").removePrefix("0X")
        return ByteArray(clean.length / 2) { at ->
            clean.substring(at * 2, at * 2 + 2).toInt(16).toByte()
        }
    }

    private fun resolve(link: String, from: URL): URL? =
        runCatching { URL(from, link) }.getOrNull()

    /**
     * Почему не вышло — словами, а не именем исключения.
     *
     * Отказ сервера, оборванная сеть и место на телефоне — три разные беды, и
     * человеку в каждой из них нужно разное: подождать, поменять сеть,
     * почистить память.
     */
    private fun reasonOf(failure: Throwable): String = when {
        failure is java.io.FileNotFoundException -> "Сервер сказал, что такого файла у него нет"
        failure is java.net.UnknownHostException -> "Такого адреса нет — или сети нет у телефона"
        failure is javax.net.ssl.SSLException -> "Сервер не смог договориться о защищённой связи"
        failure is java.net.SocketTimeoutException -> "Сервер молчит: ответа нет"
        failure is java.io.IOException && failure.message?.contains("ENOSPC") == true ->
            "На телефоне кончилось место"

        !failure.message.isNullOrBlank() -> failure.message!!.replaceFirstChar { it.uppercase() }
        else -> "Не вышло: ${failure.javaClass.simpleName}"
    }

    /** Хвосты, по которым адрес считается ссылкой на сам файл. */
    private val FILE_ENDS = listOf(
        ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".avi", ".flv", ".ts", ".ogv", ".wmv", ".3gp",
    )

    /** Места разметки, где адрес видео лежит прямо. */
    private val TAGS = listOf(
        Regex("""<video[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex("""<source[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
        Regex(
            """<meta[^>]+(?:property|name)=["']og:video[^"']*["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']og:video[^"']*["']""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""["'](?:file|src|url|source|hls|playlist)["']\s*:\s*["']([^"']+)["']"""),
    )

    /** Последняя попытка: адрес записи, написанный где угодно в тексте страницы. */
    private val LOOSE = Regex(
        """https?://[^"'\s\\<>]+?\.(?:mp4|m4v|webm|mkv|mov|m3u8)(?:\?[^"'\s\\<>]*)?""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Подпись обозревателя. Обычная и ничья: она одинакова у всех, кто качает
     * через Askya, и ничего не говорит ни о телефоне, ни о человеке.
     */
    private const val BROWSER =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Сколько ждать ответа, прежде чем считать сервер молчащим. */
    private const val PATIENCE_MS = 20_000

    /** Кусок, которым переливается поток. */
    private const val CHUNK = 64 * 1024

    /** Докуда читается страница или список: дальше в них нет ничего нужного. */
    private const val PAGE_LIMIT = 4 * 1024 * 1024
}
