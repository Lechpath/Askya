package app.askya.ui.echo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.preferences.EchoLastTrack
import app.askya.echo.EchoBundle
import app.askya.echo.EchoRules
import app.askya.echo.EchoShelf
import app.askya.echo.Track
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingGrid
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.delay

/**
 * С чего начать — вопрос, которым Echo встречает вошедшего.
 *
 * Плеер открывают не с пустой головой: человек уже знает, чего хочет, — и
 * почти всегда это одно из семи. Дослушать вчерашнее; посмотреть всё подряд;
 * поставить свой список; включить папку, куда всё скачано; вспомнить
 * исполнителя; послушать пластинку целиком; попасть в настроение. Прежде на
 * входе стоял молчащий плеер с кнопкой «играть», и любой из этих ответов
 * начинался с похода в списки.
 *
 * ## Почему карточками, а не строками меню
 *
 * Семь строк с галочками — это форма, которую заполняют. Семь карточек,
 * падающих на экран, — это раскладка, из которой выбирают, и разница здесь не
 * в красоте: у строки нет размера, а у карточки есть, и по числу под именем
 * сразу видно, что за ней стоит — «34 исполнителя» или «ни одного».
 *
 * Падают они водопадом: сверху, по одной, с задержкой в несколько кадров, и
 * встают рядами. Это не украшение ради украшения — это то же, что делает
 * заставка и занавес раздела: на секунду занимает глаз, пока читается музыка
 * телефона. Порядок падения задан смыслом: первым — «продолжить», потому что
 * чаще всего хотят именно этого.
 *
 * ## Что за какой карточкой
 *
 * «Продолжить», «Вся музыка», «Плейлист» и «Папка» ведут туда, что уже есть: в
 * память плеера и в готовые разделы. Три остальные — «Исполнитель», «Альбом»,
 * «Жанр» — таких разделов не имеют, и полки для них выводятся из подписей
 * файлов по правилам ([EchoRules]). Правила и заведены ради того, чтобы
 * скачанная завтра песня легла на ту же полку, а не завела рядом свою.
 *
 * «Вся музыка» стоит второй и отвечает тем, кто не выбирает: ни списка, ни
 * полки — всё, что лежит на телефоне, одной лентой. Прежде такого ответа у
 * вопроса не было, и человек, которому всё равно, гасил карточку крестиком и
 * шёл за той же самой библиотекой в Lab. Спрашивать «по какому признаку?» у
 * того, кто хочет просто включить музыку, — значит спрашивать лишнее.
 *
 * Вопрос пропускается: крестик в углу закрывает его, оставляя тот самый
 * молчащий плеер, — а совсем его отключают выключателем в настройках Echo.
 */
@Composable
fun EchoStartCard(
    library: List<Track>?,
    last: EchoLastTrack?,
    onResume: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onSection: (EchoSection) -> Unit,
    onDismiss: () -> Unit,
) {
    // Полка, которую открыли, и на ней выбранное. Стек короткий и всего на два
    // шага, поэтому парой значений, а не списком страниц.
    var shelf by remember { mutableStateOf<EchoShelf?>(null) }
    var bundle by remember { mutableStateOf<EchoBundle?>(null) }

    // Правила выводятся один раз на библиотеку: проход по всем песням стоит
    // недёшево, а меняется библиотека только вместе с самим списком.
    val rules = remember(library) { EchoRules.of(library.orEmpty()) }

    when {
        bundle != null -> {
            val open = bundle!!
            EchoCard(
                title = open.name,
                subtitle = songs(open.tracks.size),
                onDismiss = { bundle = null },
                back = true,
                width = 0.94f,
                height = 0.9f,
            ) {
                TrackList(
                    tracks = open.tracks,
                    empty = "Пусто",
                    hint = "Похоже, эти песни с телефона убрали.",
                    onPlay = onPlay,
                )
            }
        }

        shelf != null -> {
            val open = shelf!!
            val shelves = remember(library, open) { rules.shelve(library.orEmpty(), open) }
            EchoCard(
                title = open.title,
                subtitle = shelvesCount(shelves.size, open),
                onDismiss = { shelf = null },
                back = true,
                width = 0.92f,
                height = 0.86f,
            ) {
                if (shelves.isEmpty()) {
                    EmptyState(title = "Разложить не по чему", hint = open.nothing)
                } else {
                    FadingGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(shelves, key = { _, it -> it.name }) { _, found ->
                            val first = found.tracks.firstOrNull()
                            CoverTile(
                                title = found.name,
                                subtitle = songs(found.tracks.size),
                                albumId = first?.albumId ?: 0,
                                uri = first?.uri,
                                onClick = { bundle = found },
                            )
                        }
                    }
                }
            }
        }

        else -> Waterfall(
            library = library,
            last = last,
            rules = rules,
            onChoose = { choice ->
                when (choice) {
                    EchoStartChoice.CONTINUE -> {
                        onResume()
                        onDismiss()
                    }

                    EchoStartChoice.ALL -> onSection(EchoSection.ALL_MUSIC)
                    EchoStartChoice.PLAYLIST -> onSection(EchoSection.PLAYLISTS)
                    EchoStartChoice.FOLDER -> onSection(EchoSection.FOLDERS)
                    EchoStartChoice.ARTIST -> shelf = EchoShelf.ARTIST
                    EchoStartChoice.ALBUM -> shelf = EchoShelf.ALBUM
                    EchoStartChoice.GENRE -> shelf = EchoShelf.GENRE
                }
            },
            onDismiss = onDismiss,
        )
    }
}

/** Шесть ответов на вопрос «что играть». */
private enum class EchoStartChoice(
    val title: String,
    val about: String,
    val icon: ImageVector,
) {
    CONTINUE("Продолжить", "На чём остановились", Icons.Outlined.PlayCircleOutline),
    ALL("Вся музыка", "Всё, что лежит на телефоне", Icons.Outlined.LibraryMusic),
    PLAYLIST("Плейлист", "Свой порядок песен", Icons.Outlined.QueueMusic),
    FOLDER("Папка", "Как лежит на телефоне", Icons.Outlined.FolderOpen),
    ARTIST("Исполнитель", "Кто это играет", Icons.Outlined.Person),
    ALBUM("Альбом", "Пластинки целиком", Icons.Outlined.Album),
    GENRE("Жанр", "На что это похоже", Icons.Outlined.GraphicEq),
}

/**
 * Сами карточки, падающие на экран.
 *
 * Не в [EchoCard], а во весь лист: это не «раскрытое поверх плеера», а первый
 * экран раздела, и рамка вокруг него была бы рамкой вокруг всего, что человек
 * сейчас видит.
 */
@Composable
private fun Waterfall(
    library: List<Track>?,
    last: EchoLastTrack?,
    rules: EchoRules,
    onChoose: (EchoStartChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val tracks = library.orEmpty()

    // Число под именем карточки — то самое, что говорит, стоит ли туда идти.
    // Считается раз на библиотеку: три прохода по ней на каждую перерисовку
    // экрана — это заметно.
    val counts = remember(library) {
        mapOf(
            EchoShelf.ARTIST to rules.shelve(tracks, EchoShelf.ARTIST).size,
            EchoShelf.ALBUM to rules.shelve(tracks, EchoShelf.ALBUM).size,
            EchoShelf.GENRE to rules.shelve(tracks, EchoShelf.GENRE).size,
        )
    }

    val playlists = rememberPlaylistCount()
    val folders = remember(library) { library?.map { it.folder }?.distinct()?.size }

    // «Продолжить» показывается только тогда, когда есть что продолжать: карточка,
    // за которой ничего нет, — обещание, которое нельзя сдержать.
    val choices = remember(last) {
        EchoStartChoice.entries.filter { it != EchoStartChoice.CONTINUE || last != null }
    }

    Box(modifier = Modifier.fillMaxSize().sunsetBackground()) {
        // Отступы под часы и кнопки системы — те же, что даёт разделам
        // ScreenScaffold: вопрос стоит во весь экран, а не в его карточке, и
        // без них заголовок ложился бы прямо на часы.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Что поставить?",
                        fontFamily = FontFamily.Serif,
                        fontSize = 28.sp,
                        letterSpacing = (-0.3).sp,
                        color = NightInk,
                    )
                    Text(
                        text = library?.let { songs(it.size) + " на телефоне" } ?: "Смотрю, что есть",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NightMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                EchoIcon(
                    icon = Icons.Outlined.Close,
                    label = "Не спрашивать сейчас",
                    onClick = onDismiss,
                )
            }

            FadingGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(choices, key = { _, it -> it.name }) { at, choice ->
                    Falling(index = at) {
                        ChoiceTile(
                            choice = choice,
                            count = when (choice) {
                                EchoStartChoice.CONTINUE -> last?.title?.ifBlank { null }
                                EchoStartChoice.ALL -> library?.let { songs(it.size) }
                                EchoStartChoice.PLAYLIST -> playlists?.let { lists(it) }
                                EchoStartChoice.FOLDER -> folders?.let { "$it на телефоне" }
                                EchoStartChoice.ARTIST -> counts[EchoShelf.ARTIST]?.let { people(it) }
                                EchoStartChoice.ALBUM -> counts[EchoShelf.ALBUM]?.let { records(it) }
                                EchoStartChoice.GENRE -> counts[EchoShelf.GENRE]?.let { kinds(it) }
                            },
                            onClick = { onChoose(choice) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Падение одной карточки.
 *
 * Сверху, с задержкой по номеру и с недолётом-перелётом пружины: карточка
 * доходит до места, чуть проваливается и встаёт. Ровное появление читалось бы
 * как «экран дорисовался», а падение — как «раскладываю перед тобой».
 *
 * Задержка мелкая: шесть карточек по девяносто миллисекунд — это полсекунды на
 * всю раскладку, ровно столько, сколько человек и так тратит, переводя взгляд.
 */
@Composable
private fun Falling(index: Int, content: @Composable () -> Unit) {
    val drop = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(index * STAGGER_MS)
        drop.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f))
    }

    Box(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            // Высота своя у каждой карточки, и путь считается от неё же: иначе
            // карточки второго ряда падали бы дольше первых.
            translationY = -(1f - drop.value) * size.height * FALL
            alpha = drop.value.coerceIn(0f, 1f)
        },
    ) {
        content()
    }
}

/** Карточка ответа: знак, имя засечным и число под ним. */
@Composable
private fun ChoiceTile(
    choice: EchoStartChoice,
    count: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            // Ширина — вся, что дала ячейка: без этого карточка сжимается по
            // самому длинному слову внутри, и ряд выходит рваным.
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NightPanel)
            .border(1.dp, NightBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NightPanelSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = choice.icon,
                contentDescription = null,
                tint = Sunset,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = choice.title,
            fontFamily = FontFamily.Serif,
            fontSize = 19.sp,
            color = NightInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = choice.about,
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = count.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Sunset,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Место под числом занято всегда, даже когда числа ещё нет: иначе
            // карточки первого ряда подпрыгивали бы, дочитав библиотеку.
            modifier = Modifier.padding(top = 8.dp).alpha(if (count == null) 0f else 1f),
        )
    }
}

/**
 * Сколько собрано плейлистов.
 *
 * `null`, пока хранилище молчит: ноль на этом месте означал бы «плейлистов
 * нет», и человек, у которого их пять, увидел бы враньё в те доли секунды,
 * что читается база.
 */
@Composable
private fun rememberPlaylistCount(): Int? {
    val repository = appContainer().echoRepository
    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = null)
    return playlists?.size
}

/** «3 плейлиста» — счёт с правильным окончанием, как и всё остальное в Echo. */
private fun lists(count: Int): String = plural(count, "плейлист", "плейлиста", "плейлистов")

private fun people(count: Int): String = plural(count, "исполнитель", "исполнителя", "исполнителей")

private fun records(count: Int): String = plural(count, "альбом", "альбома", "альбомов")

private fun kinds(count: Int): String = plural(count, "жанр", "жанра", "жанров")

private fun shelvesCount(count: Int, shelf: EchoShelf): String = when (shelf) {
    EchoShelf.ARTIST -> people(count)
    EchoShelf.ALBUM -> records(count)
    EchoShelf.GENRE -> kinds(count)
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val hundred = count % 100
    val ten = count % 10
    return when {
        hundred in 11..14 -> "$count $many"
        ten == 1 -> "$count $one"
        ten in 2..4 -> "$count $few"
        else -> "$count $many"
    }
}

/** Задержка между соседними карточками — на неё и держится водопад. */
private const val STAGGER_MS = 90L

/** С какой высоты падает карточка, долей собственной высоты. */
private const val FALL = 2.2f
