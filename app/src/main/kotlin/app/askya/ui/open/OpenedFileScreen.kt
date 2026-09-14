package app.askya.ui.open

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.IncomingFile
import app.askya.app.androidContainer
import app.askya.domain.docs.DocFormat
import app.askya.echo.OutsideAudio
import app.askya.echo.playFromOutside
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
 * кончается вместе с задачей приложения (см. [app.askya.app.FileOpenRouter]).
 * Запись, заведённая на такую ссылку, назавтра открывалась бы ошибкой, и
 * человек винил бы в этом Askya, а не проводник.
 *
 * Исключение — картинка: её Askya умеет забрать себе целиком, копией в свою
 * папку, как забирает всякую другую. Копия не зависит ни от чьего доступа, и
 * «оставить» здесь означает ровно то, что обещает. Для книг и документов
 * такого хранилища нет — они в Scroll живут ссылками, — и обещать «оставлю»
 * там было бы враньём.
 *
 * ## Два выхода из этого листа
 *
 * Кино и музыка сквозь него проходят насквозь, и по-разному. Кино открывается
 * прямо здесь, во весь экран: фильм смотрят и закрывают, и лист — ровно та
 * мера жизни, которая ему нужна. А музыку включают и **уходят**, и лист поверх
 * приложения живёт до первого шага назад. Поэтому песня отдаётся плееру и
 * уводит человека в AskyaEcho, а лист закрывается за ней
 * (`echo/EchoOutside.kt`).
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
fun OpenedFileScreen(file: IncomingFile, onClose: () -> Unit, onEcho: () -> Unit) {
    val container = androidContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var notice by remember(file.uri) { mutableStateOf<String?>(null) }
    var kept by remember(file.uri) { mutableStateOf(false) }

    /**
     * Почему не заиграло. Пока `null` и файл — музыка, лист пуст: разбор
     * длится доли секунды, и мелькнувшая на них надпись читалась бы как ошибка.
     */
    var unplayable by remember(file.uri) { mutableStateOf<String?>(null) }

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

    // Музыка уходит в AskyaEcho и уводит человека за собой.
    LaunchedEffect(file.uri) {
        if (!file.audio) return@LaunchedEffect
        when (val result = playFromOutside(context, container.echoPlayer, file.uri, file.name)) {
            is OutsideAudio.Playing -> onEcho()
            is OutsideAudio.Failed -> unplayable = result.reason
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

            // Музыка ещё разбирается: пусто. Не заигравшая падает ниже, в
            // общий разговор о том, чего Askya не открыла.
            file.audio && unplayable == null -> Unit

            // У книги своя шапка — с оглавлением, поиском и кеглем. Накрывать
            // её ещё одной значило бы отнять у читалки полосу экрана ради
            // имени файла, которое и так стоит на её собственной обложке.
            file.format == DocFormat.BOOK ->
                BookReaderScreen(name = file.name, uri = file.uri, onBack = onClose)

            else -> OpenedDocument(
                file = file,
                kept = kept,
                unplayable = unplayable,
                onClose = onClose,
                onElsewhere = { openElsewhere(context, file) },
                onKeep = {
                    scope.launch {
                        val copy = container.imageStore
                            .importFrom(Uri.parse(file.uri), file.name, file.mime)
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

/**
 * Отдать файл тому, кто его откроет.
 *
 * Тупик — худшее, чем может кончиться «Открыть с помощью»: человек выбрал
 * Askya, Askya не смогла, и он остался с ней наедине. Поэтому вместе с отказом
 * стоит выход: то же намерение уходит обратно в систему, и она предлагает
 * остальных.
 *
 * Право на чтение передаётся вместе с намерением — без него выбранное
 * приложение получит ссылку, которую ему нечем открыть.
 *
 * Открывать некому — не беда и не ошибка: приложений для этого формата на
 * телефоне просто нет, и сказано об этом уже на самом листе.
 */
private fun openElsewhere(context: android.content.Context, file: IncomingFile) {
    val uri = Uri.parse(file.uri)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, file.mime.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(view, "Открыть файл"))
    }
}

/** Сам файл под шапкой с его именем — всё, кроме книги и кино. */
@Composable
private fun OpenedDocument(
    file: IncomingFile,
    kept: Boolean,
    unplayable: String?,
    onClose: () -> Unit,
    onElsewhere: () -> Unit,
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
        when {
            unplayable != null -> EmptyState(
                title = "Не заиграло",
                hint = unplayable,
                actionLabel = "Открыть другим приложением",
                onAction = onElsewhere,
            )

            file.format == DocFormat.IMAGE -> ImageView(file.uri)
            file.format == DocFormat.PDF -> PdfView(file.uri)
            file.format == DocFormat.TEXT -> TextView(file.uri)
            file.format == DocFormat.WORD -> OfficeView(file.uri, DocFormat.WORD)
            file.format == DocFormat.EXCEL -> OfficeView(file.uri, DocFormat.EXCEL)

            else -> EmptyState(
                title = "Askya это не читает",
                hint = "Формат «${file.name.substringAfterLast('.', "без расширения")}» " +
                    "она открыть не берётся. Телефон предложит другое приложение.",
                actionLabel = "Открыть другим приложением",
                onAction = onElsewhere,
            )
        }
    }
}
