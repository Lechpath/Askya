package app.askya.ui.scroll

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.preferences.ReaderPage
import app.askya.data.preferences.ReaderStyle
import app.askya.domain.docs.BookBlock
import app.askya.domain.docs.BookText
import app.askya.domain.docs.readBook
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.highlighted
import app.askya.ui.components.snippet
import app.askya.ui.theme.CoralAccent
import app.askya.ui.theme.CoralSoft
import app.askya.ui.theme.PaperCream
import app.askya.ui.theme.PaperInk
import app.askya.ui.theme.PaperMuted
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Читалка книг: fb2 внутри Askya.
 *
 * Книгу отдавали чужому приложению — и человек уходил из Scroll в чужую
 * навигацию, к чужим шрифтам и чужой рекламе, а место, на котором он
 * остановился, оставалось там же. Между тем книга — это ровно то, ради чего
 * заводят полку: раздел называется «Библиотека», и книге место в ней.
 *
 * От читалки ждут не «показать текст», а нескольких вещей, без которых чтение
 * не чтение, — они здесь и есть:
 *
 * — оглавление: книгу открывают не с начала, а с той главы, где встали;
 * — кегль: у каждого своё зрение, и книга должна подстраиваться под глаз, а
 *   не глаз под книгу;
 * — свет страницы: кремовая днём, сепия при лампе, чёрная ночью в постели;
 * — место: закрыл на середине главы — открылось там же, само;
 * — поиск по всей книге: «где это было?» — вопрос, который задают книге чаще
 *   всего;
 * — чистый экран: касание посреди страницы убирает всё, кроме букв.
 *
 * Страница — глава целиком, а не разворот: разбивать текст на страницы значит
 * считать вёрстку под каждый кегль и каждый поворот экрана, а прокрутка не
 * теряет строку при повороте и не заставляет ловить край страницы пальцем.
 * Прогресс при этом остаётся: глава такая-то из стольких, столько-то процентов
 * книги позади.
 *
 * Настройки чтения общие для всех книг, а место — своё у каждой; и то, и
 * другое живёт в [app.askya.data.preferences.ReaderPreferences].
 */
@Composable
fun BookReaderScreen(
    name: String,
    uri: String,
    onBack: () -> Unit,
    onOpenElsewhere: () -> Unit = {},
) {
    val context = LocalContext.current
    val preferences = appContainer().readerPreferences

    val style by preferences.style.collectAsStateWithLifecycle(initialValue = preferences.state.value)
    val spot by remember(uri) { preferences.spot(uri) }
        .collectAsStateWithLifecycle(initialValue = null)

    var book by remember(uri) { mutableStateOf<BookText?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val read = readBook(context, uri)
        book = read
        failed = read == null
    }

    val palette = paletteOf(style.page)

    // Ночная страница — тёмная во весь экран, и системные значки над ней
    // должны стать светлыми: чёрные часы на чёрной странице просто исчезают.
    ReaderSystemBars(dark = style.page == ReaderPage.NIGHT)

    Box(modifier = Modifier.fillMaxSize().background(palette.page)) {
        when {
            failed -> Trouble(
                text = "Открыть книгу не вышло. Файл могли удалить, отозвать доступ — " +
                    "или это не fb2.",
                action = "Открыть другим приложением",
                palette = palette,
                onAction = onOpenElsewhere,
            )

            book == null -> Trouble(
                text = "Читаю книгу…",
                action = null,
                palette = palette,
                onAction = {},
            )

            else -> Reading(
                book = book!!,
                name = name,
                uri = uri,
                style = style,
                palette = palette,
                startAt = spot,
                onBack = onBack,
            )
        }
    }

    // «Назад» из читалки — назад в Scroll, а не на страницу выше в книге:
    // главы листают самой книгой, а этой кнопкой из неё выходят.
    BackHandler(onBack = onBack)
}

/** Какая створка открыта поверх страницы. */
private enum class ReaderPanel { CONTENTS, SEARCH, STYLE }

/** Найденное в книге: где это и как выглядит вокруг. */
private class Found(val chapter: Int, val block: Int, val text: String)

@Composable
private fun Reading(
    book: BookText,
    name: String,
    uri: String,
    style: ReaderStyle,
    palette: ReaderPalette,
    startAt: app.askya.data.preferences.ReaderSpot?,
    onBack: () -> Unit,
) {
    val preferences = appContainer().readerPreferences

    var chapter by remember(book) { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<ReaderPanel?>(null) }
    // Голая страница: касание посреди неё убирает всё, кроме букв, — и
    // возвращает обратно.
    var chrome by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var found by remember(book) { mutableStateOf(emptyList<Found>()) }
    // Куда прокрутить сразу после смены главы: место из прошлого чтения или
    // найденный абзац.
    var jumpTo by remember(book) { mutableStateOf<Int?>(null) }
    var restored by remember(book) { mutableStateOf(false) }

    val list = rememberLazyListState()
    val current = book.chapters.getOrElse(chapter) { book.chapters.first() }

    // Место из прошлого чтения — один раз: перечитывать его значило бы
    // возвращать человека назад на каждой записи прогресса.
    LaunchedEffect(book, startAt) {
        if (restored || startAt == null) return@LaunchedEffect
        restored = true
        if (startAt.chapter in book.chapters.indices) {
            chapter = startAt.chapter
            if (startAt.block > 0) jumpTo = startAt.block
        }
    }

    LaunchedEffect(chapter, jumpTo, current) {
        val target = jumpTo
        if (target != null) {
            list.scrollToItem(target.coerceIn(0, (current.blocks.size - 1).coerceAtLeast(0)))
            jumpTo = null
        }
    }

    // Место записывается на остановке прокрутки, а не на каждой строке: пишет
    // это на диск, и делать так по десять раз в секунду незачем.
    LaunchedEffect(book, uri) {
        snapshotFlow { list.isScrollInProgress }.collect { moving ->
            if (!moving && restored) {
                preferences.remember(uri, chapter, list.firstVisibleItemIndex)
            }
        }
    }

    LaunchedEffect(chapter) {
        if (restored) preferences.remember(uri, chapter, 0)
    }

    fun openChapter(index: Int, block: Int = 0) {
        chapter = index.coerceIn(0, book.chapters.lastIndex)
        jumpTo = block
        panel = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            FadingColumn(
                state = list,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { chrome = !chrome }
                    },
                contentPadding = PaddingValues(
                    start = 22.dp,
                    end = 22.dp,
                    // Место под створки: текст не должен начинаться и кончаться
                    // под ними. Створки полупрозрачны, и без запаса последняя
                    // строка читалась бы сквозь подошву.
                    top = 84.dp,
                    bottom = 112.dp,
                ),
            ) {
                items(current.blocks.size) { index ->
                    BlockText(
                        block = current.blocks[index],
                        style = style,
                        palette = palette,
                        highlight = query,
                    )
                }

                item {
                    ChapterEnd(
                        book = book,
                        chapter = chapter,
                        palette = palette,
                        onNext = { openChapter(chapter + 1) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(
                title = book.title.ifBlank { name },
                chapter = current.title,
                palette = palette,
                onBack = onBack,
                onContents = { panel = ReaderPanel.CONTENTS },
                onSearch = { panel = ReaderPanel.SEARCH },
                onStyle = { panel = ReaderPanel.STYLE },
            )
        }

        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(
                book = book,
                chapter = chapter,
                inChapter = progressIn(list.firstVisibleItemIndex, current.blocks.size),
                palette = palette,
                onPrevious = { openChapter(chapter - 1) },
                onNext = { openChapter(chapter + 1) },
            )
        }

        when (panel) {
            ReaderPanel.CONTENTS -> ContentsPanel(
                book = book,
                chapter = chapter,
                palette = palette,
                onPick = { openChapter(it) },
                onDismiss = { panel = null },
            )

            ReaderPanel.SEARCH -> SearchPanel(
                book = book,
                query = query,
                found = found,
                palette = palette,
                onQuery = { query = it },
                onFound = { found = it },
                onPick = { place -> openChapter(place.chapter, place.block) },
                onDismiss = { panel = null },
            )

            ReaderPanel.STYLE -> StylePanel(
                style = style,
                palette = palette,
                onFontSize = { preferences.setFontSize(it) },
                onPage = { preferences.setPage(it) },
                onSerif = { preferences.setSerif(it) },
                onDismiss = { panel = null },
            )

            null -> Unit
        }
    }
}

/**
 * Абзац или заголовок книги.
 *
 * Абзацы выключены по формату и с красной строкой — так набирают книги, и на
 * длинном тексте это заметно: строка ровная, а начало абзаца видно, не
 * вглядываясь в пустоту между ними.
 *
 * Найденное подсвечивается прямо в тексте: поиск нашёл главу, а глазами
 * искать в ней ту самую строку — работа, от которой поиск и должен избавить.
 */
@Composable
private fun BlockText(
    block: BookBlock,
    style: ReaderStyle,
    palette: ReaderPalette,
    highlight: String,
) {
    val family = if (style.serif) FontFamily.Serif else FontFamily.SansSerif

    when (block) {
        is BookBlock.Heading -> Text(
            text = highlighted(block.text, highlight),
            fontFamily = FontFamily.Serif,
            fontSize = (style.fontSize + headingBump(block.level)).sp,
            lineHeight = (style.fontSize + headingBump(block.level) + 8).sp,
            fontWeight = if (block.level <= 2) FontWeight.SemiBold else FontWeight.Medium,
            color = palette.ink,
            modifier = Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp),
        )

        is BookBlock.Paragraph -> Text(
            text = highlighted(block.text, highlight),
            color = palette.ink,
            // Красная строка и выключка по формату задаются стилем целиком:
            // отступа первой строки у самого `Text` в подписи нет.
            style = TextStyle(
                fontFamily = family,
                fontSize = style.fontSize.sp,
                lineHeight = (style.fontSize * 1.6f).sp,
                textAlign = TextAlign.Justify,
                textIndent = TextIndent(firstLine = (style.fontSize * 1.2f).sp),
                // Выключка по формату без переносов рвёт русские строки
                // пробелами в палец шириной: слова длинные, экран узкий.
                // Переносы возвращают строке плотность — так и набирают книги.
                hyphens = Hyphens.Auto,
                lineBreak = LineBreak.Paragraph,
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        BookBlock.Divider -> Text(
            text = "* * *",
            fontFamily = FontFamily.Serif,
            fontSize = style.fontSize.sp,
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
    }
}

/** Насколько заглавие крупнее текста — по своему уровню. */
private fun headingBump(level: Int): Int = when (level) {
    1 -> 8
    2 -> 6
    3 -> 4
    else -> 2
}

/** Конец главы: сколько осталось книги и куда дальше. */
@Composable
private fun ChapterEnd(
    book: BookText,
    chapter: Int,
    palette: ReaderPalette,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(1.dp)
                .background(palette.border),
        )

        if (chapter < book.chapters.lastIndex) {
            Text(
                text = "Дальше: " + book.chapters[chapter + 1].title,
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = palette.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onNext)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        } else {
            Text(
                text = "Книга дочитана",
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = palette.muted,
                modifier = Modifier.padding(top = 18.dp),
            )
        }
    }
}

/** Шапка читалки: куда вернуться, что читаем и чем управлять. */
@Composable
private fun TopBar(
    title: String,
    chapter: String,
    palette: ReaderPalette,
    onBack: () -> Unit,
    onContents: () -> Unit,
    onSearch: () -> Unit,
    onStyle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.page.copy(alpha = 0.96f))
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderIcon(Icons.AutoMirrored.Outlined.ArrowBack, "Назад", palette, onBack)

        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chapter,
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        ReaderIcon(Icons.AutoMirrored.Outlined.List, "Оглавление", palette, onContents)
        ReaderIcon(Icons.Outlined.Search, "Поиск по книге", palette, onSearch)
        ReaderIcon(Icons.Outlined.TextFields, "Как набрано", palette, onStyle)
    }
}

/**
 * Подошва: где мы в книге и как перейти к соседней главе.
 *
 * Проценты считаются по главам и месту в текущей, а не по буквам: точная доля
 * прочитанного потребовала бы мерить всю книгу заранее, а человеку нужно
 * знать, много ли осталось, — и для этого главы хватает.
 */
@Composable
private fun BottomBar(
    book: BookText,
    chapter: Int,
    inChapter: Float,
    palette: ReaderPalette,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val done = ((chapter + inChapter) / book.chapters.size).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.page.copy(alpha = 0.96f))
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(palette.border)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(done)
                    .fillMaxHeight()
                    .background(palette.accent),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderIcon(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                label = "Прошлая глава",
                palette = palette,
                enabled = chapter > 0,
                onClick = onPrevious,
            )
            Text(
                text = "Глава ${chapter + 1} из ${book.chapters.size} · ${(done * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            ReaderIcon(
                icon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                label = "Следующая глава",
                palette = palette,
                enabled = chapter < book.chapters.lastIndex,
                onClick = onNext,
            )
        }
    }
}

/** Оглавление: все главы книги, читаемая — цветом. */
@Composable
private fun ContentsPanel(
    book: BookText,
    chapter: Int,
    palette: ReaderPalette,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ReaderPanelCard(title = "Оглавление", palette = palette, onDismiss = onDismiss) {
        FadingColumn(modifier = Modifier.fillMaxWidth()) {
            items(book.chapters.size) { index ->
                val here = index == chapter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(index) }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (here) palette.accent else palette.muted,
                        modifier = Modifier.width(34.dp),
                    )
                    Text(
                        text = book.chapters[index].title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 17.sp,
                        color = if (here) palette.accent else palette.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Поиск по книге.
 *
 * Ищет по всем главам сразу и показывает кусок текста вокруг найденного — тем
 * же способом, каким Scroll показывает найденное в записях. Открытая глава
 * оставляет подсветку в тексте: найденное слово должно быть видно и на
 * странице, а не только в списке.
 */
@Composable
private fun SearchPanel(
    book: BookText,
    query: String,
    found: List<Found>,
    palette: ReaderPalette,
    onQuery: (String) -> Unit,
    onFound: (List<Found>) -> Unit,
    onPick: (Found) -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Ищется в стороне от главного потока: книга — это мегабайт букв, и на
    // каждой набранной букве перебирать его на кадре нельзя.
    LaunchedEffect(query, book) {
        val needle = query.trim()
        if (needle.length < 2) {
            onFound(emptyList())
            return@LaunchedEffect
        }
        onFound(withContext(Dispatchers.Default) { search(book, needle) })
    }

    ReaderPanelCard(title = "Поиск по книге", palette = palette, onDismiss = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.page)
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (query.isEmpty()) {
                Text(text = "Что искать", fontSize = 17.sp, color = palette.muted)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    color = palette.ink,
                ),
                cursorBrush = SolidColor(palette.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }

        if (query.trim().length >= 2 && found.isEmpty()) {
            Text(
                text = "В книге такого нет.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        FadingColumn(modifier = Modifier.fillMaxWidth()) {
            items(found.size) { index ->
                val place = found[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(place) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = book.chapters[place.chapter].title,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = highlighted(place.text, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Сколько найдено — не больше сотни: тому, кто ищет слово «и», список из
 * тысячи строк не поможет, а собирать его — работа на секунды.
 */
private fun search(book: BookText, needle: String): List<Found> {
    val places = ArrayList<Found>()

    book.chapters.forEachIndexed { chapterIndex, chapter ->
        chapter.blocks.forEachIndexed { blockIndex, block ->
            if (places.size >= MAX_FOUND) return places
            val text = when (block) {
                is BookBlock.Heading -> block.text
                is BookBlock.Paragraph -> block.text
                BookBlock.Divider -> return@forEachIndexed
            }
            if (text.isEmpty()) return@forEachIndexed
            if (!text.contains(needle, ignoreCase = true)) return@forEachIndexed
            places += Found(
                chapter = chapterIndex,
                block = blockIndex,
                text = snippet(text, needle),
            )
        }
    }

    return places
}

/** Как набрана страница: кегль, свет и шрифт. */
@Composable
private fun StylePanel(
    style: ReaderStyle,
    palette: ReaderPalette,
    onFontSize: (Int) -> Unit,
    onPage: (ReaderPage) -> Unit,
    onSerif: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ReaderPanelCard(
        title = "Как набрано",
        palette = palette,
        onDismiss = onDismiss,
        tall = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Кегль",
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = palette.ink,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = "Мельче",
                sign = "А−",
                palette = palette,
                onClick = { onFontSize(style.fontSize - 1) },
            )
            Text(
                text = "${style.fontSize}",
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = palette.accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(44.dp),
            )
            Stepper(
                label = "Крупнее",
                sign = "А+",
                palette = palette,
                onClick = { onFontSize(style.fontSize + 1) },
            )
        }

        Text(
            text = "Страница",
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            color = palette.ink,
            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageChoice("Кремовая", ReaderPage.CREAM, style.page, palette, onPage)
            PageChoice("Сепия", ReaderPage.SEPIA, style.page, palette, onPage)
            PageChoice("Ночь", ReaderPage.NIGHT, style.page, palette, onPage)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSerif(!style.serif) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Засечки",
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    color = palette.ink,
                )
                Text(
                    text = "Книжный шрифт. Выключить — станет как в приложениях",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
            Text(
                text = if (style.serif) "Вкл" else "Выкл",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                color = if (style.serif) palette.accent else palette.muted,
            )
        }
    }
}

@Composable
private fun Stepper(label: String, sign: String, palette: ReaderPalette, onClick: () -> Unit) {
    Text(
        text = sign,
        fontFamily = FontFamily.Serif,
        fontSize = 19.sp,
        color = palette.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, palette.border, CircleShape)
            .clickable(onClick = onClick, onClickLabel = label)
            .padding(top = 9.dp),
    )
}

@Composable
private fun PageChoice(
    label: String,
    page: ReaderPage,
    chosen: ReaderPage,
    palette: ReaderPalette,
    onPick: (ReaderPage) -> Unit,
) {
    val sample = paletteOf(page)
    val here = page == chosen

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 54.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(sample.page)
                .border(
                    width = if (here) 2.dp else 1.dp,
                    color = if (here) palette.accent else palette.border,
                    shape = RoundedCornerShape(10.dp),
                )
                .clickable { onPick(page) },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Аа", fontFamily = FontFamily.Serif, fontSize = 16.sp, color = sample.ink)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (here) palette.accent else palette.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Створка поверх страницы: оглавление, поиск, набор.
 *
 * Той же формы, что все карточки Askya, — 28 скруглений, затемнение позади,
 * тап мимо закрывает, — но в цветах страницы: створка в кремовой рамке поверх
 * ночной книги светила бы в лицо ровно тем, от чего ночную страницу и
 * включили.
 */
@Composable
private fun ReaderPanelCard(
    title: String,
    palette: ReaderPalette,
    onDismiss: () -> Unit,
    // Оглавление и поиск — списки, им нужна высота; настройки набора кончаются
    // на третьей строке, и пустая карточка в пол-экрана под ними выглядела бы
    // недогруженной.
    tall: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.page.copy(alpha = 0.88f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .then(if (tall) Modifier.fillMaxHeight(0.78f) else Modifier.heightIn(max = 620.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(palette.panel)
                .border(1.dp, palette.border, RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    color = palette.ink,
                    modifier = Modifier.weight(1f),
                )
                ReaderIcon(Icons.Outlined.Close, "Закрыть", palette, onDismiss)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ReaderIcon(
    icon: ImageVector,
    label: String,
    palette: ReaderPalette,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) palette.muted else palette.border,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Строчка вместо книги: ещё читаем или прочитать не вышло. */
@Composable
private fun Trouble(
    text: String,
    action: String?,
    palette: ReaderPalette,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.muted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Text(
                text = action,
                fontFamily = FontFamily.Serif,
                fontSize = 17.sp,
                color = palette.accent,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * Светлые системные значки на время ночной страницы — и обратно на выходе.
 *
 * Тот же приём, что у Echo: раздел меняет свет под системными панелями, а
 * уходя, возвращает всё как было — остальная Askya остаётся светлой.
 */
@Composable
private fun ReaderSystemBars(dark: Boolean) {
    val view = LocalView.current

    DisposableEffect(view, dark) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val wasLightStatus = controller?.isAppearanceLightStatusBars
        val wasLightNavigation = controller?.isAppearanceLightNavigationBars

        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark

        onDispose {
            wasLightStatus?.let { controller?.isAppearanceLightStatusBars = it }
            wasLightNavigation?.let { controller?.isAppearanceLightNavigationBars = it }
        }
    }
}

/** Доля прочитанного внутри главы — по первому видимому абзацу. */
private fun progressIn(first: Int, size: Int): Float =
    if (size <= 1) 0f else (first.toFloat() / (size - 1)).coerceIn(0f, 1f)

/** Цвета страницы: бумага, буквы и всё, что вокруг них. */
private class ReaderPalette(
    val page: Color,
    val ink: Color,
    val muted: Color,
    val panel: Color,
    val border: Color,
    val accent: Color,
)

/**
 * Три света страницы.
 *
 * Кремовая — та же бумага, что во всей Askya. Сепия темнее и теплее: при
 * лампе белая страница слепит, а жёлтая читается спокойно. Ночь — та же
 * палитра, что в Echo: у приложения уже есть своя темнота, и придумывать
 * читалке вторую незачем.
 */
private fun paletteOf(page: ReaderPage): ReaderPalette = when (page) {
    // Сами краски, а не роли темы: у страницы книги свой свет
    // (кремовая, сепия, ночь), и он выбирается тут же, в читалке. Тема
    // приложения его не трогает — иначе «кремовая» страница чернела бы от
    // настройки, к чтению отношения не имеющей.
    ReaderPage.CREAM -> ReaderPalette(
        page = PaperCream,
        ink = PaperInk,
        muted = PaperMuted,
        panel = Color(0xFFFFFFFF),
        border = CoralSoft,
        accent = CoralAccent,
    )

    ReaderPage.SEPIA -> ReaderPalette(
        page = Color(0xFFF3E7D0),
        ink = Color(0xFF3B2F22),
        muted = Color(0xFF7C6A52),
        panel = Color(0xFFFAF2E3),
        border = Color(0xFFDFCCA9),
        accent = Color(0xFFA9642F),
    )

    ReaderPage.NIGHT -> ReaderPalette(
        page = Night,
        ink = NightInk,
        muted = NightMuted,
        panel = NightPanel,
        border = NightBorder,
        accent = Sunset,
    )
}

/** Сколько найденного показывает поиск. */
private const val MAX_FOUND = 100
