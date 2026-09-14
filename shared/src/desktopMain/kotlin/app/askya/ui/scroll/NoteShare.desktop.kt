package app.askya.ui.scroll

import app.askya.platform.PlatformContext
import app.askya.platform.fileOf
import app.askya.platform.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/*
 * «Отправить» у Windows-версии. Общего окна «поделиться», как у телефона, у
 * компьютера нет — поэтому текст ложится в буфер обмена, а файл сохраняется
 * туда, куда покажут: в письмо и мессенджер его приносят уже оттуда.
 */

actual fun shareNoteText(context: PlatformContext, title: String, body: String): Boolean {
    val copied = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(noteAsText(title, body)), null)
    }.isSuccess
    if (copied) context.toast("Заметка скопирована — вставьте её куда нужно")
    return copied
}

actual fun shareNoteFile(context: PlatformContext, title: String, body: String): Boolean {
    val name = safeName(title.ifBlank { "Заметка" }).let { if (it.endsWith(".md")) it else "$it.md" }
    val target = chooseSaveFile("Сохранить заметку", name) ?: return true
    return runCatching { target.writeText(noteAsText(title, body)) }.isSuccess
}

actual fun shareAttachment(
    context: PlatformContext,
    scope: CoroutineScope,
    uri: String,
    name: String,
    mime: String,
    onFailed: () -> Unit,
) {
    val source = fileOf(uri)?.takeIf { it.isFile }
    if (source == null) {
        onFailed()
        return
    }
    val target = chooseSaveFile("Сохранить копию", safeName(name.ifBlank { source.name })) ?: return
    scope.launch {
        val done = withContext(Dispatchers.IO) { runCatching { source.copyTo(target, overwrite = true) }.isSuccess }
        if (done) context.toast("Копия лежит в «${target.parentFile?.name ?: target.parent}»") else onFailed()
    }
}

/** Окно «Сохранить как» Windows; `null` — передумали. */
fun chooseSaveFile(title: String, suggested: String): File? {
    val dialog = fileDialog(title, FileDialog.SAVE)
    dialog.file = suggested
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name).also { lastFolder = it.parentFile }
}

internal actual val SHARE_TEXT_ABOUT: String = "Скопируется — останется вставить в письмо или мессенджер"
internal actual val SHARE_FILE_ABOUT: String = "Сохранится документом .md туда, куда покажете"
