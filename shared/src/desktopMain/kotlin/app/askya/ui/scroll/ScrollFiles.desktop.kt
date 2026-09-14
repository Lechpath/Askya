package app.askya.ui.scroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.askya.app.appContainer
import app.askya.platform.PlatformContext
import app.askya.platform.asUri
import app.askya.platform.fileOf
import app.askya.platform.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.net.URLConnection
import kotlin.math.max

/*
 * Файлы Scroll на компьютере: выбор — окном Windows, открытие — программой,
 * которую Windows назначила типу, «поделиться» — показать файл в проводнике.
 *
 * Ссылка в записи — `file:/…`. Сам документ, как и на телефоне, не
 * копируется: запись ссылается на файл там, где он лежит. Картинка — копией
 * в папку Askya, по той же причине, что у телефона (см. [rememberImageImport]).
 */

@Composable
actual fun rememberFilePicker(
    mimeTypes: Array<String>,
    keep: Boolean,
    onPicked: (List<PickedFile>) -> Unit,
): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    return remember(mimeTypes.joinToString()) {
        {
            val files = chooseFiles("Выбрать файлы", mimeTypes)
            if (files.isNotEmpty()) picked(files.map { it.describe() })
        }
    }
}

@Composable
actual fun rememberImageImport(
    onFailed: (Int) -> Unit,
    onImported: (List<PickedFile>) -> Unit,
): () -> Unit {
    val store = appContainer().imageStore
    val scope = rememberCoroutineScope()
    val failed by rememberUpdatedState(onFailed)
    val imported by rememberUpdatedState(onImported)
    return remember(store) {
        {
            val files = chooseFiles("Выбрать картинки", arrayOf("image/*"))
            if (files.isNotEmpty()) {
                scope.launch {
                    val copied = files.mapNotNull { file ->
                        val about = file.describe()
                        store.importFrom(about.uri, about.name, about.mime)
                            ?.let { copy -> about.copy(uri = copy, isImage = true) }
                    }
                    if (copied.isNotEmpty()) imported(copied)
                    if (copied.size < files.size) failed(files.size - copied.size)
                }
            }
        }
    }
}

actual fun openFile(context: PlatformContext, uri: String, mime: String): Boolean {
    val file = fileOf(uri)?.takeIf { it.isFile } ?: return false
    return runCatching { Desktop.getDesktop().open(file) }.isSuccess
}

/**
 * «Поделиться» у Windows — проводник с выделенным файлом: оттуда его
 * перетаскивают в письмо, мессенджер или на флешку. Отправлять самой Askya
 * некуда — у компьютера нет общего окна «поделиться», как у телефона.
 */
actual fun shareImages(context: PlatformContext, uris: List<String>): Boolean {
    val files = uris.mapNotNull { fileOf(it)?.takeIf(File::isFile) }
    if (files.isEmpty()) return false
    val shown = showInExplorer(files.first())
    if (shown && files.size > 1) context.toast("Картинки лежат в этой папке — выделена первая")
    return shown
}

actual suspend fun decodeImageBitmap(context: PlatformContext, uri: String, maxSide: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val file = fileOf(uri)?.takeIf { it.isFile } ?: return@runCatching null
            decodeUpright(file.readBytes(), maxSide)?.toComposeImageBitmap()
        }.getOrNull()
    }

/**
 * Картинка, уменьшенная до [maxSide] и повёрнутая по EXIF. Снимки с камеры
 * лежат боком, а поворот записан меткой рядом с пикселями — ровно как на
 * телефоне, и сетка без него показывала бы их не той стороной.
 */
internal fun decodeUpright(bytes: ByteArray, maxSide: Int): Image? {
    val data = Data.makeFromBytes(bytes)
    val origin = runCatching { Codec.makeFromData(data).encodedOrigin }.getOrDefault(EncodedOrigin.TOP_LEFT)
    val image = Image.makeFromEncoded(bytes)
    // Поворот и матрицу к нему Skia знает сама — те же восемь случаев, что у EXIF.
    val sideways = origin.swapsWidthHeight()
    val width = if (sideways) image.height else image.width
    val height = if (sideways) image.width else image.height
    val scale = minOf(1f, maxSide.toFloat() / max(width, height))
    val outW = max(1, (width * scale).toInt())
    val outH = max(1, (height * scale).toInt())
    if (scale == 1f && origin == EncodedOrigin.TOP_LEFT) return image

    val surface = Surface.makeRasterN32Premul(outW, outH)
    val canvas = surface.canvas
    canvas.scale(scale, scale)
    canvas.concat(origin.toMatrix(image.width, image.height))
    canvas.drawImageRect(
        image,
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
        SamplingMode.LINEAR,
        Paint(),
        true,
    )
    return surface.makeImageSnapshot()
}

/** Проводник Windows с выделенным файлом. */
fun showInExplorer(file: File): Boolean = runCatching {
    ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
    true
}.getOrElse { runCatching { Desktop.getDesktop().open(file.parentFile); true }.getOrDefault(false) }

/**
 * Окно выбора файлов Windows. [types] — типы, как их просит телефон
 * (картинки, pdf, текст); окну Windows нужны расширения, и они выводятся
 * из типов. Несколько файлов разом, как и на телефоне.
 */
fun chooseFiles(title: String, types: Array<String>): List<File> {
    val dialog = fileDialog(title, FileDialog.LOAD)
    dialog.isMultipleMode = true
    val extensions = extensionsOf(types)
    if (extensions.isNotEmpty()) {
        // Фильтр по расширению — для окна Windows он становится строкой
        // «*.png;*.jpg» внизу; без неё показываются все файлы.
        dialog.file = extensions.joinToString(";") { "*.$it" }
    }
    dialog.isVisible = true
    return dialog.files.orEmpty().toList().also { chosen ->
        chosen.firstOrNull()?.parentFile?.let { lastFolder = it }
    }
}

/**
 * Окно выбора файла — поверх Askya и в той папке, где выбирали в прошлый раз.
 *
 * Хозяин — окно Askya: без него выбор мог уйти под неё, и Askya, ждущая
 * ответа, казалась бы зависшей. Встаёт окно Windows всё равно в левый верхний
 * угол — место ему AWT задать не даёт. Папка без подсказки была бы той, из
 * которой запущена программа, — человеку она ни о чём не говорит. В первый
 * раз — «Загрузки»: туда приходит слепок с телефона и всё, что скачано, а
 * значит, и то, что хотят положить в Scroll.
 */
internal fun fileDialog(title: String, mode: Int): FileDialog {
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
        ?: Frame.getFrames().firstOrNull { it.isShowing }
    val dialog = FileDialog(owner, title, mode)
    dialog.directory = (lastFolder ?: downloads()).absolutePath
    return dialog
}

/** Папка последнего выбора — до закрытия Askya. */
internal var lastFolder: File? = null

private fun downloads(): File {
    val home = File(System.getProperty("user.home"))
    return File(home, "Downloads").takeIf { it.isDirectory } ?: home
}

private fun extensionsOf(types: Array<String>): List<String> {
    if (types.any { it == "*/*" }) return emptyList()
    return types.flatMap { type ->
        when {
            type == "image/*" -> listOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic")
            type == "application/pdf" -> listOf("pdf")
            type.startsWith("text/") -> listOf("txt", "md", "markdown", "csv", "log")
            type.contains("fictionbook") || type.contains("fb2") -> listOf("fb2", "zip")
            type.contains("wordprocessingml") -> listOf("docx")
            type.contains("spreadsheetml") -> listOf("xlsx", "xlsm")
            type == "application/zip" -> listOf("zip")
            type == "application/json" -> listOf("json")
            type == "text/html" -> listOf("html", "htm")
            else -> emptyList()
        }
    }.distinct()
}

private fun File.describe(): PickedFile {
    val mime = mimeOfName(name)
    return PickedFile(uri = asUri(), name = name, mime = mime, isImage = mime.startsWith("image/"))
}

/** Тип файла по имени: у Windows его спрашивать не у кого, кроме расширения. */
internal fun mimeOfName(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".md") || lower.endsWith(".markdown") -> "text/markdown"
        lower.endsWith(".fb2") -> "application/x-fictionbook+xml"
        lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".heic") -> "image/heic"
        else -> URLConnection.guessContentTypeFromName(name) ?: ""
    }
}
