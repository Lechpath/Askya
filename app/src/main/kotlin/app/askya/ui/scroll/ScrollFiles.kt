package app.askya.ui.scroll

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.domain.docs.DocFormat
import app.askya.domain.docs.documentFormat
import app.askya.domain.docs.readOfficeDocument
import app.askya.ui.components.AskyaNotice
import app.askya.ui.scroll.imageedit.decodeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
fun rememberFilePicker(
    mimeTypes: Array<String>,
    onPicked: (List<PickedFile>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            // Без этого доступ живёт до перезапуска процесса, и записи в Scroll
            // переставали открываться на следующий день.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        onPicked(uris.map { context.describe(it) })
    }
    return { launcher.launch(mimeTypes) }
}

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
fun rememberImageImport(
    onFailed: (Int) -> Unit = {},
    onImported: (List<PickedFile>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val store = appContainer().imageStore
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = uris.mapNotNull { uri ->
                val picked = context.describe(uri)
                store.importFrom(uri, picked.name, picked.mime)
                    ?.let { copy -> picked.copy(uri = copy, isImage = true) }
            }
            if (copied.isNotEmpty()) onImported(copied)
            if (copied.size < uris.size) onFailed(uris.size - copied.size)
        }
    }
    return { launcher.launch(arrayOf("image/*")) }
}

/** Имя и тип документа. Имя спрашивается у провайдера, а не берётся из пути. */
private fun Context.describe(uri: Uri): PickedFile {
    val mime = contentResolver.getType(uri).orEmpty()
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
            }
    }.getOrNull()

    return PickedFile(
        uri = uri.toString(),
        // Последний кусок пути — не имя файла, но лучше пустой строки:
        // провайдер имя отдавать не обязан.
        name = name ?: uri.lastPathSegment.orEmpty().substringAfterLast('/'),
        mime = mime,
        isImage = mime.startsWith("image/"),
    )
}

/** Открыть файл тем, чем система умеет. Для pdf и md это чужое приложение. */
fun openFile(context: Context, uri: String, mime: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), mime.ifEmpty { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Отдать картинку наружу — в мессенджер, почту, куда угодно.
 *
 * Через окно выбора, а не сразу в одно приложение: куда отправляют, решает
 * человек, и список у каждого свой. Право на чтение выдаётся вместе с
 * намерением и живёт до конца отправки — постоянного доступа к папке Askya
 * чужое приложение не получает.
 *
 * [uri] берётся у `ImageStore` (`shareable`, `shareableCopy`), а не из записи:
 * старую копию по её `file://` система отдать наружу не даст.
 *
 * `false` означает, что отправить нечем — на телефоне нет ни одного
 * приложения, умеющего принимать картинки.
 */
fun shareImage(context: Context, uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Флаг нужен и на самом окне выбора: право получает то приложение,
    // которое из него запустят, а запускает его система, а не мы.
    val chooser = Intent.createChooser(send, "Поделиться картинкой")
        .apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }

    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Отдать наружу пачку картинок разом.
 *
 * Отдельным намерением, а не отправкой по одной: приложение на той стороне
 * ждёт `SEND_MULTIPLE` и складывает пачку в одно сообщение, а десять отправок
 * подряд означали бы десять окон выбора.
 *
 * Тип общий на всю пачку — «любая картинка»: в ней могут оказаться и png, и
 * jpeg, и точный тип у разнородного набора всё равно назвать нечем.
 */
fun shareImages(context: Context, uris: List<Uri>): Boolean {
    if (uris.isEmpty()) return false
    // Одна картинка уходит обычной отправкой: пачку из одной понимают не все,
    // а «отправить картинку» — все.
    if (uris.size == 1) return shareImage(context, uris.first())

    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Поделиться картинками")
        .apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }

    return try {
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Отправить не вышло. Одно окно на просмотр и на правку: причина у них одна и
 * та же — отдать картинку некому.
 */
@Composable
internal fun NoShareDialog(onDismiss: () -> Unit) {
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
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = decodeImage(context, uri, maxSide = targetPx)?.asImageBitmap()
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
    val context = LocalContext.current
    var text by remember(uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
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
    val context = LocalContext.current
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
