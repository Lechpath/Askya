package app.askya.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import app.askya.domain.docs.DocFormat
import app.askya.domain.docs.documentFormat
import app.askya.video.VideoFormats

/** Ключ намерения: с каким разделом Askya просят открыться. */
const val OPEN_ROUTE = "askya.open"

/**
 * Раздел погоды. Единственный, куда сейчас ведут снаружи, — из виджета погоды
 * на рабочем столе: смотревший на градусы хочет подробностей о них, а не
 * приложение вообще.
 */
const val OPEN_WEATHER = "weather"

/**
 * AskyaDay. Сюда ведёт уведомление со списком дела: нажавший на него хочет
 * увидеть само дело, а не тот раздел, на котором Askya закрыли в прошлый раз.
 */
const val OPEN_TODAY = "today"

/**
 * Ключ намерения: какое дело открыть раскрытым.
 *
 * Отдельно от маршрута, как и просьба «начни писать»: это не другое место, а
 * другое дело в том же месте. Ноль значит «никакое» — дня довольно и без него.
 */
const val OPEN_DEED = "askya.deed"

/**
 * Подраздел «Голос». Сюда ведёт виджет голосовой заметки на рабочем столе —
 * обеими своими половинами: кружок просит ещё и начать запись ([SAY_NOW]),
 * строка рядом просто открывает раздел.
 */
const val OPEN_VOICE = "voice"

/**
 * Ключ намерения: открыть «Голос» и сразу начать писать.
 *
 * Отдельно от маршрута, а не третьим маршрутом «voice-recording», потому что
 * это не другое место, а другое дело в том же месте. Разрешение на микрофон
 * при этом всё равно спрашивает экран: у виджета окна нет, а у просьбы должно
 * быть лицо.
 */
const val SAY_NOW = "askya.say"

/**
 * Просят ли начать запись — и сразу вычеркнутое из намерения, по той же
 * причине, что и маршрут: намерение живёт дольше своего повода, и вернувшееся
 * из памяти приложение включило бы микрофон само.
 */
fun sayNowOf(incoming: Intent?): Boolean {
    val intent = incoming ?: return false
    if (!intent.getBooleanExtra(SAY_NOW, false)) return false
    intent.removeExtra(SAY_NOW)
    return true
}

/**
 * С какого раздела просят открыться — и сразу вычеркнутое из намерения.
 *
 * Вычеркнуть нужно потому, что намерение живёт дольше своего повода: система
 * держит его у задачи, и приложение, вернувшееся к жизни после того, как его
 * выгрузили из памяти, получило бы ту же просьбу второй раз — и увело бы
 * человека в погоду с того места, где он был.
 */
/**
 * Какое дело просят раскрыть — и сразу вычеркнутое из намерения, по той же
 * причине, что и маршрут: намерение живёт дольше своего повода, и приложение,
 * вернувшееся из памяти, раскрыло бы вчерашнее дело поверх сегодняшнего дня.
 */
fun openDeedOf(incoming: Intent?): Long? {
    val intent = incoming ?: return null
    val id = intent.getLongExtra(OPEN_DEED, 0L)
    if (id <= 0L) return null
    intent.removeExtra(OPEN_DEED)
    return id
}

fun openRouteOf(incoming: Intent?): String? {
    val intent = incoming ?: return null
    val route = intent.getStringExtra(OPEN_ROUTE) ?: return null
    intent.removeExtra(OPEN_ROUTE)
    return route.takeIf { it.isNotBlank() }
}

/**
 * Файл, с которым Askya открыли снаружи.
 *
 * [format] — то же, чем Askya меряет всякий документ; [video] стоит отдельно,
 * потому что для видео формата в [DocFormat] нет вовсе: в Scroll видео не
 * заводят, его смотрят в AskyaV.
 */
data class IncomingFile(
    val uri: String,
    val name: String,
    val mime: String,
    val format: DocFormat,
    val video: Boolean,
) {
    /** Есть ли чем это показать своими силами. */
    val readable: Boolean
        get() = video || format != DocFormat.OTHER
}

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
 * задача приложения. Постоянное право можно взять только у того, кто сам его
 * предложил (`FLAG_GRANT_PERSISTABLE_URI_PERMISSION`), а проводники его почти
 * никогда не ставят.
 *
 * Отсюда правило: **файл, открытый снаружи, показывается, но не заводится
 * записью.** Запись со ссылкой, которая перестанет открываться завтра, — это
 * не «добавили в Библиотеку», а поломка с отсрочкой. Оставить у себя можно
 * отдельным действием, и вот оно уже копирует файл, а не ссылку.
 */
fun incomingFileOf(context: Context, incoming: Intent?): IncomingFile? {
    val intent = incoming ?: return null
    val uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtraCompat(Intent.EXTRA_STREAM)
        else -> null
    } ?: return null

    // Право, если его всё-таки дали. Не дали — не беда: сейчас файл открыт, а
    // навсегда его никто и не обещал.
    runCatching {
        if (intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    val name = context.displayName(uri)
    // Тип от того, кто прислал, вернее, чем от провайдера: он знает, что
    // отправляет. Пустой — спрашиваем провайдера, как и везде.
    val mime = intent.type
        ?: runCatching { context.contentResolver.getType(uri) }.getOrNull()
        ?: ""

    return IncomingFile(
        uri = uri.toString(),
        name = name,
        mime = mime,
        format = documentFormat(name, mime),
        // Видео узнаётся по имени и по типу: у экзотических контейнеров
        // провайдер отдаёт `application/octet-stream`, а VLC их открывает.
        video = VideoFormats.isVideo(name) || mime.startsWith("video/"),
    )
}

/** Имя файла: в ссылке его нет — спрашивается у провайдера. */
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
 * С Android 13 у него появился вариант с типом, а прежний объявлен устаревшим;
 * до неё есть только прежний. Оба здесь, и выбор делается по версии.
 */
@Suppress("DEPRECATION")
private fun Intent.getParcelableExtraCompat(name: String): Uri? =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, Uri::class.java)
    } else {
        getParcelableExtra(name)
    }
