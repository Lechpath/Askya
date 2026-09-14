package app.askya.ui.scroll

import app.askya.platform.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import app.askya.platform.LocalPlatformContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.domain.docs.DocFormat
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.FadingScroll
import app.askya.ui.components.MarkdownDocument
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.noteedit.NoteEditViewModel

/**
 * Просмотр приложенного файла внутри Askya.
 *
 * Раньше файл открывало чужое приложение: человек уходил из Askya и возвращался
 * кнопкой «назад» в чужой навигации. Картинки, текст и pdf Android умеет
 * показывать сам — `BitmapFactory` и `PdfRenderer` входят в систему, — поэтому
 * никакой библиотеки для этого не нужно.
 *
 * Чужое приложение остаётся запасным выходом: форматов больше трёх, и для
 * остальных честнее отдать файл тому, кто умеет, чем показать пустой экран.
 *
 * [paged] означает, что картинку открыли из «Изображений», и соседние
 * листаются пальцем — как в любой галерее. Что считать соседними, говорит
 * [album]: `null` — все картинки раздела, номер — один альбом. Тот же срез, что
 * был в сетке под пальцем: листать из альбома в чужие снимки человек не ждёт.
 *
 * Файл, открытый не из раздела (из «Недавнего», из книги), листать не с чем —
 * там [paged] выключен, и экран остаётся тем же одиночным просмотром.
 */
@Composable
fun ScrollViewerScreen(
    noteId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit = {},
    /** Правка картинки; `null` — у системы её нет. */
    onEditImage: ((Long) -> Unit)? = null,
    onRename: (Long) -> Unit = {},
    album: Long? = null,
    paged: Boolean = false,
) {
    val viewModel: NoteEditViewModel = viewModel(factory = NoteEditViewModel.factory(appContainer()))
    val note by remember(noteId) { viewModel.note(noteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val context = LocalPlatformContext.current
    val store = appContainer().imageStore

    var noShare by remember { mutableStateOf(false) }
    // Отправка не картинки: у заметки спрашивается, текстом или файлом, у
    // документа спрашивать нечего.
    var sharing by remember { mutableStateOf(false) }
    var noSend by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val scroll: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(appContainer()))
    val siblings by when {
        // Одиночный просмотр ни на что не подписывается: соседей у файла нет.
        !paged -> remember { mutableStateOf(emptyList<Note>()) }
        album == null -> scroll.images.collectAsStateWithLifecycle()
        else -> remember(album) { scroll.inAlbum(album) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
    }

    // Пока срез не пришёл, страница одна — та, которую открыли: иначе на месте
    // картинки был бы пустой экран, пока база отвечает.
    val pages = if (siblings.isEmpty()) listOfNotNull(note) else siblings
    val pager = rememberPagerState(pageCount = { pages.size })

    // Какая картинка перед глазами. Помнится номером записи, а не номером
    // страницы: срез приходит из базы заново и может перестроиться (подписали
    // картинку — она уехала в начало), а человек должен остаться на той же
    // картинке, а не на том же месте списка.
    var shown by remember(noteId) { mutableStateOf(noteId) }
    var positioned by remember(noteId) { mutableStateOf(false) }

    LaunchedEffect(pages) {
        val index = pages.indexOfFirst { it.id == shown }
        if (index < 0) return@LaunchedEffect
        if (index != pager.currentPage) pager.scrollToPage(index)
        positioned = true
    }

    // Листнули — запомнили, на чём остановились. До того как встали на
    // открытую картинку, страницы не в счёт: срез приходит позже экрана, и
    // первая страница успела бы записаться как выбранная.
    LaunchedEffect(pager, pages) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (positioned) pages.getOrNull(page)?.let { shown = it.id }
        }
    }

    val current = pages.getOrNull(pager.currentPage) ?: note

    /*
     * Книга открывается не просмотром, а читалкой: у fb2 есть главы,
     * оглавление и место, на котором человек остановился, — показывать их
     * прокруткой текста значило бы отдать всё это чужому приложению.
     *
     * Читалка занимает экран целиком, вместе с шапкой: в книге и без того есть
     * своя шапка с именем книги и главой, а вторая над ней съедала бы строку
     * текста ради того же самого.
     */
    val bookUri = current?.uri?.takeIf { formatOfFile(current) == DocFormat.BOOK }
    if (current != null && bookUri != null) {
        BookReaderScreen(
            name = current.title,
            uri = bookUri,
            onBack = onBack,
            onOpenElsewhere = { openFile(context, bookUri, current.mime) },
        )
        return
    }

    BackHandler(onBack = onBack)

    ScreenScaffold(
        title = current?.title.orEmpty().ifEmpty { "Файл" },
        onNavigationClick = onBack,
        navigationIsBack = true,
        actions = {
            // Править можно только своё. У заметки это текст, у картинки —
            // сама картинка: она лежит в папке Askya, и портить чужой файл
            // правка не может. У pdf и прочего исходник у системы, и
            // переписывать его отсюда нечем.
            if (current?.uri == null && current != null) {
                TextButton(onClick = { onEdit(current.id) }) { Text("Переписать") }
            }
            // Действия относятся к той картинке, что сейчас на экране, а не к
            // той, с которой листание начали.
            // У картинки их три, и словами они в шапку не помещаются — от трёх
            // подписей не осталось бы места самому имени файла.
            // Отдать наружу можно всё: документ уходит файлом, своя
            // заметка — текстом или файлом на выбор.
            if (current != null && current.isImage != true) {
                HeaderIcon(
                    icon = Icons.Outlined.Share,
                    contentDescription = "Поделиться",
                    onClick = {
                        val file = current.uri
                        if (file == null) {
                            sharing = true
                        } else {
                            shareAttachment(
                                context = context,
                                scope = scope,
                                uri = file,
                                name = current.title,
                                mime = current.mime,
                                onFailed = { noSend = true },
                            )
                        }
                    },
                )
            }

            if (current?.isImage == true) {
                HeaderIcon(
                    icon = Icons.Outlined.Share,
                    contentDescription = "Поделиться",
                    onClick = {
                        val ready = store.shareLink(current.uri)
                        if (ready == null || !shareImages(context, listOf(ready))) noShare = true
                    },
                )
                HeaderIcon(
                    icon = Icons.Outlined.DriveFileRenameOutline,
                    contentDescription = "Переименовать",
                    onClick = { onRename(current.id) },
                )
                if (onEditImage != null) {
                    HeaderIcon(
                        icon = Icons.Outlined.Brush,
                        contentDescription = "Изменить",
                        onClick = { onEditImage(current.id) },
                    )
                }
            }
        },
    ) {
        if (pages.isEmpty()) return@ScreenScaffold

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            // Одна страница — тот же одиночный просмотр, что и был: у файла
            // соседей нет, и листать его некуда.
            userScrollEnabled = pages.size > 1,
        ) { page ->
            Viewed(note = pages[page], onOpenElsewhere = { note, uri ->
                openFile(context, uri, note.mime)
            })
        }
    }

    if (noShare) NoShareDialog(onDismiss = { noShare = false })

    if (sharing && current != null) {
        ShareNoteDialog(
            title = current.title,
            body = current.body,
            onDismiss = { sharing = false },
            onFailed = { noSend = true },
        )
    }

    if (noSend) {
        AskyaNotice(
            title = "Некуда отправить",
            text = "На телефоне нет приложения, которое принимает такое.",
            onDismiss = { noSend = false },
            icon = Icons.Outlined.Share,
        )
    }
}

/** Что показывать: своя заметка, картинка, pdf, документ — или чужой файл. */
@Composable
private fun Viewed(note: Note, onOpenElsewhere: (Note, String) -> Unit) {
    val uri = note.uri

    when {
        // Запись без файла — своя заметка: показывается набранной.
        uri == null -> MarkdownDocument(note.body)
        note.isImage -> ImageView(uri)
        note.mime == "application/pdf" || formatOfFile(note) == DocFormat.PDF -> PdfView(uri)
        isTextFile(note) -> TextView(uri)
        formatOfFile(note) == DocFormat.WORD -> OfficeView(uri, DocFormat.WORD)
        formatOfFile(note) == DocFormat.EXCEL -> OfficeView(uri, DocFormat.EXCEL)
        else -> Unsupported(note) { onOpenElsewhere(note, uri) }
    }
}

/**
 * Word и Excel — разметкой Askya, а не чужим приложением.
 *
 * Внутри и того, и другого — zip с xml, и текст из них достаётся своими
 * руками (`domain/docs`): заголовки заголовками, списки списками, таблицы
 * таблицами. Показываются они тем же, чем показана заметка, — и это главное:
 * документ, открытый в Scroll, выглядит страницей Askya, а не окном Word.
 *
 * Оформление при этом теряется: шрифты, поля, цвета, картинки внутри
 * документа. Документ, который нужен ровно таким, каким его свёрстали, всегда
 * можно отдать наружу — из карточки файла.
 */
@Composable
fun OfficeView(uri: String, format: DocFormat) {
    when (val text = rememberOfficeText(uri, format)) {
        null -> Waiting()
        "" -> Failed(
            "Прочитать не вышло. Файл могли удалить или отозвать доступ — " +
                "а старые .doc и .xls Askya не читает: это другой формат, не тот, что docx.",
        )
        else -> MarkdownDocument(text)
    }
}

/**
 * Картинка на весь экран с масштабированием щипком.
 *
 * Не впритык к краям, а со скруглёнными углами и отступом: экран — не рамка
 * снимка, и картинка, упирающаяся в края, читается как обои, а не как
 * показанная вещь. Отступ небольшой — ровно чтобы отделить её от края, а не
 * чтобы уменьшить.
 *
 * Скругление ложится на саму картинку, а не на место под неё: рамка считается
 * по её пропорциям, поэтому у вертикального снимка углы на его углах, а не по
 * бокам от него. Клип стоит снаружи увеличения, поэтому у приближённой
 * картинки углы остаются теми же — она ездит внутри рамки, а не наползает на
 * весь экран.
 *
 * Масштаб и сдвиг задаются трансформацией слоя: раскладка от них не меняется,
 * и на кадр приходится только отрисовка.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageView(uri: String) {
    // Крупнее превью в сетке: здесь картинку и разглядывают.
    val bitmap = rememberThumbnail(uri, targetPx = 2048)

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Пока картинка не увеличена, одним пальцем её не таскают: этот жест
    // отдан листанию соседних. Увеличенную таскают — тогда лишнего места на
    // экране нет и листать нечем.
    val state = rememberTransformableState { zoom, pan, _ ->
        // Меньше единицы не пускаем: уменьшенная картинка болталась бы в
        // пустоте, а выйти из этого состояния было бы нечем.
        scale = (scale * zoom).coerceIn(1f, 6f)
        offsetX += pan.x
        offsetY += pan.y
        if (scale == 1f) {
            offsetX = 0f
            offsetY = 0f
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Waiting()
        } else {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            // Чем картинка упирается в место под неё: широкая — боками, высокая
            // — верхом и низом. Иначе рамка вышла бы больше самой картинки.
            val byWidth = ratio >= maxWidth / maxHeight

            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .then(if (byWidth) Modifier.fillMaxWidth() else Modifier.fillMaxHeight())
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(20.dp))
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .transformable(state, canPan = { scale > 1f }),
            )
        }
    }
}

/** Текстовый файл: читается целиком и показывается разметкой. */
@Composable
fun TextView(uri: String) {
    when (val loaded = rememberTextFile(uri)) {
        null -> Waiting()
        "" -> Failed("Прочитать не вышло — файл удалили или отозвали доступ.")
        // Разметкой, а не исходником: .md для того и пишут, чтобы читать
        // набранным. Обычный .txt от этого не страдает — в нём просто нет
        // разметки, и он выходит теми же абзацами.
        else -> MarkdownDocument(loaded)
    }
}

/**
 * PDF страницами. На телефоне их рисует `PdfRenderer` системы; у Windows
 * своей рисовалки PDF нет, и файл отдаётся программе, которую Windows
 * назначила для PDF (см. «Windows-версия» в README).
 */
@Composable
expect fun PdfView(uri: String)

@Composable
private fun Unsupported(note: Note, onOpenExternally: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Askya показывает картинки, текст, pdf, fb2, docx и xlsx. " +
                "Этот формат (${note.mime.ifEmpty { "неизвестный" }}) — не из них.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onOpenExternally, modifier = Modifier.padding(top = 12.dp)) {
            Text("Открыть другим приложением")
        }
    }
}

@Composable
internal fun Waiting() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Читаю…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun Failed(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
    }
}
