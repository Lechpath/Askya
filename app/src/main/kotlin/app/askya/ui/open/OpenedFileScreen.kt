package app.askya.ui.open

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.IncomingFile
import app.askya.app.appContainer
import app.askya.domain.docs.DocFormat
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.EmptyState
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.scroll.BookReaderScreen
import app.askya.ui.scroll.ImageView
import app.askya.ui.scroll.OfficeView
import app.askya.ui.scroll.PdfView
import app.askya.ui.scroll.TextView
import app.askya.ui.video.VideoPlayerScreen
import app.askya.video.VideoSource
import kotlinx.coroutines.launch

/**
 * Файл, открытый снаружи: из проводника, из «Поделиться», из чужого
 * приложения.
 *
 * Не экран раздела и не запись в Scroll, а **разовый просмотр**. Askya
 * показывает файл теми же средствами, какими показывает свои, и уходит,
 * оставив всё как было: ни новой записи, ни следа в «Недавнем».
 *
 * Так — потому что доступ к чужому файлу приходит вместе с намерением и
 * кончается вместе с задачей приложения (см. [app.askya.app.incomingFileOf]).
 * Запись, заведённая на такую ссылку, назавтра открывалась бы ошибкой, и
 * человек винил бы в этом Askya, а не проводник.
 *
 * Исключение — картинка: её Askya умеет забрать себе целиком, копией в свою
 * папку, как забирает всякую другую. Копия не зависит ни от чьего доступа, и
 * «оставить» здесь означает ровно то, что обещает. Для книг и документов
 * такого хранилища нет — они в Scroll живут ссылками, — и обещать «оставлю»
 * там было бы враньём.
 *
 * Экран поверх всего приложения, а не маршрутом: он приходит не от нажатия
 * внутри Askya, а снаружи, и возвращаться из него надо туда, где человек был,
 * — то есть в проводник.
 *
 * Поверх — значит со своим листом. Без него сквозь открытый файл просвечивал
 * тот раздел, на котором приложение застали: две шапки в одной строке, чужие
 * карточки под картинкой, календарь соседа рядом с именем файла. Лист забирает
 * и касания: то, чего не поймал открытый файл, не должно доставаться
 * спрятанному под ним разделу.
 */
@Composable
fun OpenedFileScreen(file: IncomingFile, onClose: () -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()

    var notice by remember(file.uri) { mutableStateOf<String?>(null) }
    var kept by remember(file.uri) { mutableStateOf(false) }

    BackHandler(onBack = onClose)

    // Видео уходит прямо в плеер AskyaV: своей страницы у него здесь нет и
    // быть не должно — кино смотрят во весь экран.
    val engine = container.videoEngine
    val playing by engine.state.collectAsStateWithLifecycle()
    LaunchedEffect(file.uri) {
        if (file.video) {
            engine.open(VideoSource(uri = file.uri, title = file.name.substringBeforeLast('.', file.name)))
        }
    }

    // Кино смотрят на чёрном, всё остальное — на кремовом листе Askya.
    val sheet = if (file.video) Color.Black else MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(sheet)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        when {
            file.video -> if (playing.source != null) {
                VideoPlayerScreen(
                    onClose = {
                        engine.stop()
                        onClose()
                    },
                )
            }

            // У книги своя шапка — с оглавлением, поиском и кеглем. Накрывать
            // её ещё одной значило бы отнять у читалки полосу экрана ради
            // имени файла, которое и так стоит на её собственной обложке.
            file.format == DocFormat.BOOK ->
                BookReaderScreen(name = file.name, uri = file.uri, onBack = onClose)

            else -> OpenedDocument(
                file = file,
                kept = kept,
                onClose = onClose,
                onKeep = {
                    scope.launch {
                        val copy = container.imageStore
                            .importFrom(android.net.Uri.parse(file.uri), file.name, file.mime)
                        if (copy == null) {
                            notice = "Скопировать картинку не вышло"
                        } else {
                            container.noteRepository.addFile(
                                uri = copy,
                                name = file.name.substringBeforeLast('.', file.name),
                                mime = file.mime,
                                isImage = true,
                                topicId = null,
                            )
                            kept = true
                            notice = "Картинка скопирована в «Изображения»"
                        }
                    }
                },
            )
        }
    }

    notice?.let { text ->
        AskyaNotice(
            title = if (kept) "Оставлено" else "Не вышло",
            text = text,
            onDismiss = { notice = null },
        )
    }
}

/** Сам файл под шапкой с его именем — всё, кроме книги и кино. */
@Composable
private fun OpenedDocument(
    file: IncomingFile,
    kept: Boolean,
    onClose: () -> Unit,
    onKeep: () -> Unit,
) {
    ScreenScaffold(
        title = file.name.substringBeforeLast('.', file.name),
        onNavigationClick = onClose,
        navigationIsBack = true,
        actions = {
            if (file.format == DocFormat.IMAGE && !kept) {
                HeaderIcon(
                    icon = Icons.Outlined.BookmarkAdd,
                    contentDescription = "Оставить в Askya",
                    onClick = onKeep,
                )
            }
        },
    ) {
        when (file.format) {
            DocFormat.IMAGE -> ImageView(file.uri)
            DocFormat.PDF -> PdfView(file.uri)
            DocFormat.TEXT -> TextView(file.uri)
            DocFormat.WORD -> OfficeView(file.uri, DocFormat.WORD)
            DocFormat.EXCEL -> OfficeView(file.uri, DocFormat.EXCEL)

            else -> EmptyState(
                title = "Askya это не читает",
                hint = "Формат «${file.name.substringAfterLast('.', "без расширения")}» " +
                    "она открыть не берётся. Верните файл проводнику — он предложит другое " +
                    "приложение.",
            )
        }
    }
}
