package app.askya.ui.scroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import app.askya.platform.LocalPlatformContext
import app.askya.platform.PlatformContext
import app.askya.platform.openStream
import app.askya.data.entity.Note
import app.askya.domain.docs.DocFormat
import app.askya.domain.docs.documentFormat
import app.askya.domain.docs.readOfficeDocument
import app.askya.ui.components.AskyaNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Что удалось узнать о выбранном документе. */
data class PickedFile(
    val uri: String,
    val name: String,
    val mime: String,
    val isImage: Boolean,
)

/**
 * Выбор документов системным диалогом.
 *
 * Берётся `OpenMultipleDocuments`, а не фотопикер и не `GetContent`: только
 * `OpenDocument`-семейство даёт разрешение, которое переживает перезапуск.
 * Фотопикер отдаёт доступ на один заход, и завтра картинка в Scroll
 * открывалась бы ошибкой.
 *
 * Несколько разом, а не по одному: файлы приносят пачкой — выгрузили переписку,
 * скачали подборку, — и десять заходов в системный выбор ради десяти файлов
 * означали бы девять лишних.
 *
 * Сам файл не копируется: хранится ссылка на документ. Копия занимала бы место
 * второй раз и устаревала бы молча, когда человек правит файл снаружи. Для
 * картинок это правило обратное — см. [rememberImageImport].
 */
@Composable
expect fun rememberFilePicker(
    mimeTypes: Array<String>,
    /**
     * Держать ли доступ к файлу и после перезапуска: записи Scroll ссылаются
     * на документ годами. Для одноразового чтения (перенос заметок) не нужно —
     * разрешений на процесс отпущено ограниченное число.
     */
    keep: Boolean = true,
    onPicked: (List<PickedFile>) -> Unit,
): () -> Unit

/**
 * Выбор картинок с копированием в свою папку.
 *
 * Отдельно от [rememberFilePicker] ровно из-за копии. Картинку в Scroll
 * приносят как содержимое, а не как ссылку на чужой файл: исходник удаляют из
 * галереи, чистят загрузки, отзывают доступ — и раздел оставался с пустыми
 * квадратами. Скопированную картинку можно ещё и править (см. редактор), не
 * трогая чужой файл.
 *
 * Постоянное разрешение здесь не берётся намеренно: документ читается один раз,
 * прямо сейчас, а разрешений на процесс отпущено ограниченное число — копить их
 * ради одноразового чтения незачем.
 *
 * Копии пишутся по очереди, и [onImported] зовётся один раз на всю пачку, уже
 * с готовыми файлами: записи должны заводиться на лежащее, а раздел — получать
 * пачку разом, а не мигать по картинке в секунду.
 *
 * Не скопировавшиеся не заводятся вовсе, и [onFailed] говорит, сколько их:
 * пустая карточка в сетке выглядела бы как испорченная картинка. Остальные из
 * пачки при этом добавляются — терять девять из-за одной незачем.
 */
@Composable
expect fun rememberImageImport(
    onFailed: (Int) -> Unit = {},
    onImported: (List<PickedFile>) -> Unit,
): () -> Unit

/**
 * Открыть файл тем, чем система умеет: на телефоне — чужим приложением, на
 * компьютере — программой, которую Windows назначила этому типу. `false` —
 * открыть нечем.
 */
expect fun openFile(context: PlatformContext, uri: String, mime: String): Boolean

/**
 * Отдать картинки наружу. На телефоне — окном «Поделиться»: куда отправляют,
 * решает человек, и список у каждого свой. Одна картинка уходит обычной
 * отправкой, пачка — одним `SEND_MULTIPLE`. На компьютере «поделиться» — это
 * показать файл в проводнике выделенным: оттуда его перетаскивают в письмо
 * или мессенджер.
 *
 * [uris] — ссылки, которые чужое приложение сможет прочитать (см.
 * `ImageFiles.shareLink`), а не то, что записано в Scroll.
 *
 * `false` означает, что отправить нечем.
 */
expect fun shareImages(context: PlatformContext, uris: List<String>): Boolean

/**
 * Картинка по ссылке, уменьшенная до [maxSide] по длинной стороне и
 * повёрнутая по EXIF. `null` — не прочиталась.
 */
expect suspend fun decodeImageBitmap(context: PlatformContext, uri: String, maxSide: Int): ImageBitmap?

/**
 * Отправить не вышло. Одно окно на просмотр и на правку: причина у них одна и
 * та же — отдать картинку некому.
 */
@Composable
fun NoShareDialog(onDismiss: () -> Unit) {
    AskyaNotice(
        title = "Некуда отправить",
        text = "На телефоне нет приложения, которое принимает картинки.",
        onDismiss = onDismiss,
    )
}

/**
 * Уменьшенная картинка по ссылке на документ.
 *
 * Читается в фоне и сразу уменьшается: полноразмерное фото с телефона — это
 * десятки мегабайт в памяти, а в сетке оно занимает пару сантиметров.
 *
 * Тем же кодом, что и правка (`decodeImage`), — из-за разворота по EXIF: снимки
 * с камеры лежат боком, и если сетка показывает их так, а редактор эдак, то
 * правка выглядит как порча картинки.
 *
 * null означает и «ещё грузится», и «не прочиталось»: файл могли удалить или
 * отозвать доступ, и для сетки это одно и то же — показать нечего.
 */
@Composable
fun rememberThumbnail(uri: String, targetPx: Int = 512): ImageBitmap? {
    val context = LocalPlatformContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = decodeImageBitmap(context, uri, maxSide = targetPx)
    }

    return bitmap
}

/**
 * Текстовый ли это файл.
 *
 * Текстом считается не только тип, начинающийся с `text/`: для `.md`
 * провайдеры отдают то `text/markdown`, то `application/octet-stream`, то
 * пустую строку. По расширению получается вернее, чем по одному типу.
 */
fun isTextFile(note: Note): Boolean {
    if (note.mime.startsWith("text/")) return true
    val name = note.title.lowercase()
    return name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".markdown")
}

/**
 * Содержимое текстового файла по ссылке на документ.
 *
 * `null` означает «ещё читаю», пустая строка — «прочитать не вышло»: файл
 * могли удалить или отозвать доступ. Одно из другого экран различает сам —
 * ждать и не смочь это разные слова.
 *
 * Общее для просмотра во весь экран и для карточки в «Библиотеке»: читают они
 * одно и то же, и два чтения разошлись бы потолком или кодировкой.
 */
@Composable
fun rememberTextFile(uri: String): String? {
    val context = LocalPlatformContext.current
    var text by remember(uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                context.openStream(uri)?.use { stream ->
                    // Потолок на случай, если подсунут гигабайтный лог: без него
                    // чтение «текстового» файла кладёт приложение по памяти.
                    // Читается вручную, а не readNBytes: тот появился в API 33,
                    // а minSdk у нас 26.
                    val buffer = ByteArray(MAX_TEXT_BYTES)
                    var read = 0
                    while (read < buffer.size) {
                        val step = stream.read(buffer, read, buffer.size - read)
                        if (step < 0) break
                        read += step
                    }
                    String(buffer, 0, read)
                }
            }.getOrNull() ?: ""
        }
    }

    return text
}

/** Потолок на текстовый файл — иначе гигабайтный лог кладёт приложение. */
private const val MAX_TEXT_BYTES = 2 * 1024 * 1024

/** Что это за документ — по имени файла и типу от провайдера. */
fun formatOfFile(note: Note): DocFormat =
    if (note.uri == null) DocFormat.NOTE else documentFormat(note.title, note.mime)

/**
 * Word или Excel, разобранные в разметку Askya.
 *
 * `null` означает «ещё читаю», пустая строка — «прочитать не вышло»: файл
 * могли удалить, отозвать доступ, или это оказался старый `.doc`, который не
 * zip и не xml. Экран различает это сам — ждать и не смочь разные слова.
 *
 * Общее для карточки в «Библиотеке» и для просмотра во весь экран: разбирают
 * они одно и то же, и два разбора разошлись бы таблицами или заголовками.
 */
@Composable
fun rememberOfficeText(uri: String, format: DocFormat): String? {
    val context = LocalPlatformContext.current
    var text by remember(uri, format) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri, format) {
        text = readOfficeDocument(context, uri, format).orEmpty()
    }

    return text
}

/**
 * Метка формата: расширение из имени, а не тип из системы.
 *
 * `MD` и `PDF` человек различает мгновенно, а `application/octet-stream`, чем
 * тот же `.md` нередко приходит от провайдера, не говорит ничего.
 */
fun formatOf(note: Note): String? {
    if (note.uri == null) return null
    val ext = note.title.substringAfterLast('.', "").lowercase()
    return when {
        ext.isNotEmpty() && ext.length <= 4 -> ext.uppercase()
        note.mime == "application/pdf" -> "PDF"
        note.isImage -> "IMG"
        else -> "ФАЙЛ"
    }
}
