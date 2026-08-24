package app.askya.ui.scroll

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Отдать заметку наружу — в мессенджер, в почту, в чужое приложение.
 *
 * Записанное в Askya часто пишется не для себя: список покупок уходит домой,
 * выписка из книги — в переписку, черновик письма — в почту. До сих пор
 * единственным способом вынести заметку было выделить её текст руками и
 * скопировать по кускам.
 *
 * Спрашивается, чем именно отдать, потому что это два разных желания:
 *
 * — текстом: заметка ложится прямо в сообщение, и на той стороне её читают, не
 *   открывая ничего;
 * — файлом: заметка уходит как `.md` — её можно положить в облако, в чужой
 *   редактор, приложить к письму. Разметка при этом остаётся разметкой:
 *   заголовки, списки и таблицы переживут дорогу.
 *
 * Куда именно отправлять, спрашивает система своим окном выбора: список
 * приложений у каждого свой, и подсовывать в него угаданный мессенджер — не
 * дело приложения.
 */
@Composable
internal fun ShareNoteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Share) }) {
        DialogCaption("Как отправить?")

        DialogChoice(
            icon = Icons.AutoMirrored.Outlined.Send,
            title = "Текстом",
            about = "Ляжет прямо в сообщение — в мессенджер, в почту",
            onClick = {
                onDismiss()
                if (!shareNoteText(context, title, body)) onFailed()
            },
        )
        DialogChoice(
            icon = Icons.Outlined.Description,
            title = "Файлом .md",
            about = "Заметка уйдёт документом — в облако, в редактор, письмом",
            onClick = {
                onDismiss()
                if (!shareNoteFile(context, title, body)) onFailed()
            },
        )
    }
}

/**
 * Заметка текстом.
 *
 * Название идёт и темой письма, и первой строкой сообщения: почта берёт тему
 * отдельным полем, а мессенджер о теме не знает вовсе — и без первой строки
 * заметка приходила бы туда безымянной.
 *
 * `false` означает, что отправить нечем: на телефоне нет ни одного
 * приложения, принимающего текст.
 */
fun shareNoteText(context: Context, title: String, body: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, noteAsText(title, body))
    }
    return context.launchChooser(send, "Поделиться заметкой")
}

/**
 * Заметка файлом `.md`.
 *
 * Пишется в кэш, а не в папку Askya: это копия на один заход, и место в
 * хранилище она занимать не должна — система вычистит её сама.
 *
 * Имя файла — название заметки, очищенное от того, чего в именах файлов не
 * бывает: с косыми чертами и двоеточиями файл просто не создастся.
 */
fun shareNoteFile(context: Context, title: String, body: String): Boolean {
    val uri = noteFile(context, title, body) ?: return false
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return context.launchChooser(send, "Отправить файлом")
}

/**
 * Приложенный документ — наружу как есть.
 *
 * Копией в кэш, а не ссылкой на исходник: ссылка на чужой документ выдана
 * системой нам и передать её дальше мы не вправе — на той стороне она
 * открылась бы отказом в доступе.
 *
 * Копия делается в стороне от главного потока: книга и pdf весят мегабайты, и
 * переписывать их на кадре нельзя.
 */
suspend fun shareableDocument(context: Context, uri: String, name: String): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val target = File(shareDir(context), safeName(name.ifBlank { "документ" }))
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("нет доступа к файлу")
            provided(context, target)
        }.getOrNull()
    }

/** Отправить готовый файл. Тип — какой знаем; неизвестный сойдёт за любой. */
fun shareDocument(context: Context, uri: Uri, mime: String): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime.ifEmpty { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return context.launchChooser(send, "Поделиться файлом")
}

/**
 * Отдать приложенный документ наружу — одним движением из любого экрана.
 *
 * Копия делается в стороне от главного потока, поэтому нужна корутина: экраны
 * зовут это из нажатия, и заводить у каждого свою обвязку ради двух строк
 * незачем.
 *
 * [onFailed] значит «отправить нечем»: файл не прочитался или на телефоне нет
 * приложения, которое принимает такое.
 */
fun shareAttachment(
    context: Context,
    scope: CoroutineScope,
    uri: String,
    name: String,
    mime: String,
    onFailed: () -> Unit,
) {
    scope.launch {
        val ready = shareableDocument(context, uri, name)
        if (ready == null || !shareDocument(context, ready, mime)) onFailed()
    }
}

/** Заметка одной строкой: название сверху, текст под ним. */
internal fun noteAsText(title: String, body: String): String = when {
    title.isBlank() -> body.trim()
    body.isBlank() -> title.trim()
    else -> title.trim() + "\n\n" + body.trim()
}

private fun noteFile(context: Context, title: String, body: String): Uri? = runCatching {
    val name = safeName(title.ifBlank { "Заметка" })
    val target = File(shareDir(context), if (name.endsWith(".md")) name else "$name.md")
    target.writeText(noteAsText(title, body))
    provided(context, target)
}.getOrNull()

/**
 * Окно выбора, кому отдать.
 *
 * Право на чтение выдаётся и самому окну: получает его то приложение, которое
 * из окна запустят, а запускает его система, а не мы.
 */
private fun Context.launchChooser(send: Intent, label: String): Boolean {
    val chooser = Intent.createChooser(send, label)
        .apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    return try {
        startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun shareDir(context: Context): File =
    File(context.cacheDir, "share").apply { if (!exists()) mkdirs() }

private fun provided(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/**
 * Имя файла из названия заметки.
 *
 * Убирается то, чего в именах не бывает, и длина: файловые системы не берут
 * имена длиннее двух с половиной сотен знаков, а название заметки бывает
 * абзацем.
 */
private fun safeName(title: String): String = title
    .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(60)
    .ifBlank { "Заметка" }
