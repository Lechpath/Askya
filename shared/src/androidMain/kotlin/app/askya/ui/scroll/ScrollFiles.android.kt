package app.askya.ui.scroll

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.askya.app.appContainer
import app.askya.platform.PlatformContext
import app.askya.ui.scroll.imageedit.decodeImage
import kotlinx.coroutines.launch

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
actual fun rememberFilePicker(
    mimeTypes: Array<String>,
    keep: Boolean,
    onPicked: (List<PickedFile>) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        if (keep) uris.forEach { uri ->
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
actual fun rememberImageImport(
    onFailed: (Int) -> Unit,
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
                store.importFrom(uri.toString(), picked.name, picked.mime)
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
actual fun openFile(context: PlatformContext, uri: String, mime: String): Boolean = try {
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
private fun shareImageUris(context: Context, uris: List<Uri>): Boolean {
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

actual fun shareImages(context: PlatformContext, uris: List<String>): Boolean =
    shareImageUris(context, uris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() })

actual suspend fun decodeImageBitmap(context: PlatformContext, uri: String, maxSide: Int): ImageBitmap? =
    decodeImage(context, uri, maxSide)?.asImageBitmap()
