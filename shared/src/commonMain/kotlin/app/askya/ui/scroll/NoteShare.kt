package app.askya.ui.scroll

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import app.askya.platform.LocalPlatformContext
import app.askya.platform.PlatformContext
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogChoice
import kotlinx.coroutines.CoroutineScope

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
    val context = LocalPlatformContext.current

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Share) }) {
        DialogCaption("Как отправить?")

        DialogChoice(
            icon = Icons.AutoMirrored.Outlined.Send,
            title = "Текстом",
            about = SHARE_TEXT_ABOUT,
            onClick = {
                onDismiss()
                if (!shareNoteText(context, title, body)) onFailed()
            },
        )
        DialogChoice(
            icon = Icons.Outlined.Description,
            title = "Файлом .md",
            about = SHARE_FILE_ABOUT,
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

expect fun shareNoteText(context: PlatformContext, title: String, body: String): Boolean

/**
 * Заметка файлом `.md`.
 *
 * Пишется в кэш, а не в папку Askya: это копия на один заход, и место в
 * хранилище она занимать не должна — система вычистит её сама.
 *
 * Имя файла — название заметки, очищенное от того, чего в именах файлов не
 * бывает: с косыми чертами и двоеточиями файл просто не создастся.
 */

expect fun shareNoteFile(context: PlatformContext, title: String, body: String): Boolean

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

expect fun shareAttachment(
    context: PlatformContext,
    scope: CoroutineScope,
    uri: String,
    name: String,
    mime: String,
    onFailed: () -> Unit,
)

/** Слова окна «Как отправить?» — у телефона и компьютера отправляют по-разному. */
internal expect val SHARE_TEXT_ABOUT: String
internal expect val SHARE_FILE_ABOUT: String

/** Заметка одной строкой: название сверху, текст под ним. */
internal fun noteAsText(title: String, body: String): String = when {
    title.isBlank() -> body.trim()
    body.isBlank() -> title.trim()
    else -> title.trim() + "\n\n" + body.trim()
}

/**
 * Имя файла из названия заметки.
 *
 * Убирается то, чего в именах не бывает, и длина: файловые системы не берут
 * имена длиннее двух с половиной сотен знаков, а название заметки бывает
 * абзацем.
 */
internal fun safeName(title: String): String = title
    .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .take(60)
    .ifBlank { "Заметка" }
