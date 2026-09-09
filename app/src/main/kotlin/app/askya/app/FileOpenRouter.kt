package app.askya.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import app.askya.domain.docs.DocFormat
import app.askya.domain.docs.documentFormat
import app.askya.video.VideoFormats
import java.io.InputStream

/** Куда Askya отдаёт файл, принесённый снаружи. */
enum class OpenTarget {
    /** AskyaEcho: песня, запись, плейлист. */
    ECHO,

    /** AskyaV: кино. */
    VIDEO,

    /** Разовый просмотр: книга, документ, картинка. */
    VIEW,

    /** Askya этого не показывает. Не ошибка, а честный ответ. */
    UNKNOWN,
}

/**
 * Файл, с которым Askya открыли снаружи.
 *
 * [target] — единственное, что решает, куда он пойдёт; [format] нужен уже
 * внутри просмотра, чтобы выбрать, чем показывать.
 */
data class IncomingFile(
    val uri: String,
    val name: String,
    val mime: String,
    val format: DocFormat,
    val target: OpenTarget,
) {
    val video: Boolean get() = target == OpenTarget.VIDEO
    val audio: Boolean get() = target == OpenTarget.ECHO

    /** Есть ли чем это показать своими силами. */
    val readable: Boolean get() = target != OpenTarget.UNKNOWN
}

/**
 * Единственная дверь, через которую в Askya входит чужой файл.
 *
 * ## Зачем она одна
 *
 * Прежде разбор жил в двух местах сразу: половину работы делал манифест —
 * ловил файл по `pathPattern`, то есть **по имени в ссылке**, — а вторую
 * половину код, уже после запуска. Работало это, пока файл приходил из
 * проводника: там в ссылке есть путь, а в пути имя с расширением.
 *
 * А из «Загрузок», из Drive, из мессенджера приходит `content://`-ссылка
 * вида `content://…/document/1247`, и имени в ней нет вовсе. Фильтр по
 * `pathPattern` на такую ссылку просто не срабатывает — и Askya не появляется
 * в списке «Открыть с помощью». Молча: человек видит, что его приложения там
 * нет, и не может знать почему.
 *
 * Теперь имя спрашивается у того, кто ссылку выдал (`OpenableColumns`), а не
 * вычитывается из неё, а манифест ловит в первую очередь по типу.
 *
 * ## Три вопроса, и всегда в этом порядке
 *
 * 1. **Тип.** Сперва тот, что назвал отправитель (`intent.type`): он знает,
 *    что отправляет. Потом тот, что скажет провайдер.
 * 2. **Расширение из имени** — но имя берётся у провайдера, а не из ссылки.
 *    Это главный ответ, а не запасной: у провайдеров всё, кроме картинок и
 *    mp4, сплошь и рядом `application/octet-stream`, а имя врёт реже.
 * 3. **Первые байты.** Последний довод, когда и типа нет, и имя без
 *    расширения — так приходят файлы из мессенджеров и из «Недавних».
 *
 * Третий вопрос стоит денег: он открывает поток, а поток по чужой ссылке
 * бывает медленным. Поэтому спрашивается он только тогда, когда первые два
 * промолчали, и читает двенадцать байт.
 */
object FileOpenRouter {

    /**
     * Разбор намерения, с которым запустили приложение.
     *
     * Два случая, и оба означают одно и то же — «открой это»:
     *
     * — `VIEW` приходит из проводника и из «Открыть с помощью»;
     * — `SEND` — из кнопки «Поделиться» в любом чужом приложении.
     *
     * Больше ничего Askya снаружи не принимает: она не редактор по вызову и не
     * обработчик ссылок.
     *
     * ## Про доступ к файлу
     *
     * Доступ приходит вместе с намерением и живёт ровно столько, сколько живёт
     * задача приложения. Постоянное право можно взять только у того, кто сам
     * его предложил (`FLAG_GRANT_PERSISTABLE_URI_PERMISSION`), а проводники
     * его почти никогда не ставят.
     *
     * Отсюда правило: **файл, открытый снаружи, показывается, но не заводится
     * записью.** Запись со ссылкой, которая перестанет открываться завтра, —
     * это не «добавили в Библиотеку», а поломка с отсрочкой. Оставить у себя
     * можно отдельным действием, и вот оно уже копирует файл, а не ссылку.
     */
    fun route(context: Context, incoming: Intent?): IncomingFile? {
        val intent = incoming ?: return null
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
            else -> null
        } ?: return null

        takeAccessIfOffered(context, intent, uri)

        val name = context.displayName(uri)
        val mime = intent.type
            ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?: ""

        return IncomingFile(
            uri = uri.toString(),
            name = name,
            mime = mime,
            format = documentFormat(name, mime),
            target = targetOf(context, uri, name, mime),
        )
    }

    /**
     * Постоянное право на файл — если его предложили.
     *
     * Не предложили — не беда: сейчас файл открыт, а навсегда его никто и не
     * обещал. Берётся здесь, на входе, а не там, где файл кладут в
     * Библиотеку: к тому времени намерение уже разобрано, и флага в нём не
     * найти.
     */
    private fun takeAccessIfOffered(context: Context, intent: Intent, uri: Uri) {
        if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    /** Куда отдать файл. Три вопроса подряд, до первого внятного ответа. */
    private fun targetOf(context: Context, uri: Uri, name: String, mime: String): OpenTarget {
        val type = mime.lowercase()
        val ext = extensionOf(name)

        // Список песен узнаётся по имени прежде всякого типа, и это
        // единственное исключение из порядка. Провайдеры отдают `.m3u` и
        // `.cue` то как «текст», то как поток байтов, и по типу «текст» файл
        // ушёл бы на страницу с текстом — где человек увидел бы пути к своим
        // песням вместо самих песен.
        if (ext in PLAYLISTS) return OpenTarget.ECHO

        // 1. Тип, если он не отговорка. `application/octet-stream` и пустая
        //    строка — это «не знаю», а не ответ, и по ним решать нельзя.
        byType(type)?.let { return it }

        // 2. Расширение имени, спрошенного у провайдера.
        byExtension(ext)?.let { return it }

        // 3. Первые байты. Дороже двух прежних, и потому последний.
        bySignature(context, uri)?.let { return it }

        return OpenTarget.UNKNOWN
    }

    private fun byType(type: String): OpenTarget? = when {
        type.startsWith("audio/") -> OpenTarget.ECHO
        type.startsWith("video/") -> OpenTarget.VIDEO
        type.startsWith("image/") -> OpenTarget.VIEW
        type == "application/pdf" -> OpenTarget.VIEW
        type.contains("fictionbook") -> OpenTarget.VIEW
        type.contains("wordprocessingml") -> OpenTarget.VIEW
        type.contains("spreadsheetml") -> OpenTarget.VIEW
        // Список песен у Apple зовётся не «audio»; всё прочее его написание
        // поймала первая строка.
        type == "application/vnd.apple.mpegurl" -> OpenTarget.ECHO
        type.startsWith("text/") -> OpenTarget.VIEW
        else -> null
    }

    private fun byExtension(ext: String): OpenTarget? = when {
        ext.isEmpty() -> null
        ext in AUDIO -> OpenTarget.ECHO
        ext in PLAYLISTS -> OpenTarget.ECHO
        ext in VideoFormats.video -> OpenTarget.VIDEO
        ext in VIEWABLE -> OpenTarget.VIEW
        else -> null
    }

    /**
     * Что за файл — по его первым байтам.
     *
     * Подписи короткие и известные; читается двенадцать байт, потому что
     * дальше всех отстоит `ftyp` контейнера mp4 — он лежит с четвёртого.
     *
     * Zip здесь не различается на docx, xlsx и fb2.zip: для того, куда файл
     * отдать, довольно знать, что это документ, — а чем именно его показывать,
     * разберётся уже просмотр, у которого файл в руках.
     */
    private fun bySignature(context: Context, uri: Uri): OpenTarget? {
        val head = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readHead(12) }
        }.getOrNull() ?: return null
        if (head.size < 4) return null

        fun at(offset: Int, text: String): Boolean {
            if (head.size < offset + text.length) return false
            return text.indices.all { i -> head[offset + i] == text[i].code.toByte() }
        }

        return when {
            at(0, "ID3") -> OpenTarget.ECHO
            at(0, "fLaC") -> OpenTarget.ECHO
            at(0, "OggS") -> OpenTarget.ECHO
            at(0, "#EXTM3U") -> OpenTarget.ECHO
            at(0, "RIFF") && at(8, "WAVE") -> OpenTarget.ECHO
            // Кадр mp3 без тега ID3: маркер синхронизации из одиннадцати
            // единиц подряд.
            head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 -> OpenTarget.ECHO

            at(0, "RIFF") && at(8, "AVI ") -> OpenTarget.VIDEO
            at(0, "RIFF") && at(8, "WEBP") -> OpenTarget.VIEW
            // Matroska и WebM — один контейнер на два вида, и по подписи они
            // не различаются. Кино в нём встречается неизмеримо чаще музыки.
            head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
                head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte() -> OpenTarget.VIDEO
            // mp4 и всё его семейство: `ftyp` с четвёртого байта, а вид — в
            // марке следом. `M4A`/`M4B` — музыка, остальное кино.
            at(4, "ftyp") -> if (at(8, "M4A") || at(8, "M4B")) OpenTarget.ECHO else OpenTarget.VIDEO

            at(0, "%PDF") -> OpenTarget.VIEW
            at(0, "PK") -> OpenTarget.VIEW
            at(0, "GIF8") -> OpenTarget.VIEW
            head[0] == 0x89.toByte() && at(1, "PNG") -> OpenTarget.VIEW
            head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> OpenTarget.VIEW
            at(0, "<?xml") -> OpenTarget.VIEW

            else -> null
        }
    }

    /** Имя файла: в ссылке его может не быть — спрашивается у провайдера. */
    private fun Context.displayName(uri: Uri): String {
        val asked = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return asked?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "Файл"
    }

    /**
     * `getParcelableExtra` без предупреждения о старости.
     *
     * С Android 13 у него появился вариант с типом, а прежний объявлен
     * устаревшим; до неё есть только прежний. Оба здесь, и выбор по версии.
     */
    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            getParcelableExtra(name)
        }

    private fun InputStream.readHead(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val step = read(buffer, read, count - read)
            if (step < 0) break
            read += step
        }
        return if (read == count) buffer else buffer.copyOf(read)
    }

    /**
     * Что Echo берётся играть — с точкой, как и у видео.
     *
     * Список свой, а не взятый у библиотеки: играет Echo системным
     * `MediaPlayer`, и что именно он открывает, зависит от телефона. Здесь
     * перечислено то, что просят открыть, — а не то, что наверняка откроется:
     * не открывшийся файл не пропадает молча, о нём говорят словами
     * (`ui/open/OpenedFileScreen.kt`).
     */
    val AUDIO = setOf(
        ".mp3", ".m4a", ".m4b", ".flac", ".wav", ".ogg", ".oga", ".opus",
        ".aac", ".wma", ".ape", ".mka", ".aif", ".aiff", ".amr", ".wv", ".mid",
    )

    /** Списки песен: не звук сам по себе, а перечень того, что играть. */
    val PLAYLISTS = setOf(".m3u", ".m3u8", ".cue")

    /** То, что Askya показывает разовым просмотром. */
    private val VIEWABLE = setOf(
        ".fb2", ".zip", ".pdf", ".docx", ".xlsx", ".xlsm",
        ".txt", ".md", ".markdown", ".csv", ".log",
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif",
    )

    /** Расширение с точкой и в нижнем регистре; пустая строка — его нет. */
    fun extensionOf(name: String): String {
        val clean = name.substringBefore('?').substringBefore('#').trimEnd('/')
        if (clean.endsWith(".fb2.zip", ignoreCase = true)) return ".fb2"
        val dot = clean.lastIndexOf('.')
        if (dot < 0 || dot == clean.length - 1) return ""
        return clean.substring(dot).lowercase()
    }
}
