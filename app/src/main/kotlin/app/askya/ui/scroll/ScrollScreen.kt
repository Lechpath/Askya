package app.askya.ui.scroll

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.R
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.yet.countLine
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

/**
 * Scroll — оглавление записанного. Три подраздела, каждый открывается
 * отдельным экраном.
 *
 * Хаб, а не один длинный список: изображения смотрят сеткой, книги и файлы
 * читают с полки, списки отмечают. Свалить это в один экран значило бы
 * выбрать одну раскладку из трёх и испортить две.
 *
 * Книги и файлы были двумя подразделами и стали одним — «Библиотекой»: и то и
 * другое читают, а лежит запись в книге или сама по себе, человек на входе не
 * помнит и искал в двух местах подряд.
 *
 * Подразделы — окна во весь рост, одно на экран, выбор прокруткой. Внутри окна
 * не подпись «6 записей», а последние три записи в лицо: цифра говорит, сколько
 * там всего, а карточки — что именно. Стрелки сверху и снизу говорят, что окно
 * не единственное, а список названий в правом верхнем углу — какие они всего и
 * на котором сейчас находишься: у полноэкранной страницы нет иного способа об
 * этом сказать.
 */
@Composable
fun ScrollScreen(
    onOpenMenu: () -> Unit,
    onOpenImages: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLists: () -> Unit,
) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(appContainer()))
    val images by viewModel.images.collectAsStateWithLifecycle()
    val loose by viewModel.loose.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    val listSizes by viewModel.listSizes.collectAsStateWithLifecycle()

    val sections = listOf(
        ScrollSection(
            icon = R.drawable.ic_scroll_images,
            title = "Изображения",
            about = "Все картинки разом, а рядом альбомы, по которым их разложили.",
            count = countOf(images.size, "картинка", "картинки", "картинок"),
            preview = if (images.isEmpty()) Preview.Empty else Preview.Images(images.take(3)),
            onOpen = onOpenImages,
        ),
        ScrollSection(
            icon = R.drawable.ic_scroll_library,
            title = "Библиотека",
            about = "Книги — папки на полке; под ними файлы, ни к одной не приписанные.",
            count = shelfCount(books = topics.size, files = loose.size),
            preview = if (topics.isEmpty() && loose.isEmpty()) {
                Preview.Empty
            } else {
                // Книги вперёд — на полке они и стоят сверху. Но одно место
                // из трёх оставлено файлу, если файлы есть: окно должно
                // показывать обе половины раздела, а не только верхнюю.
                Preview.Library(
                    books = topics.take(if (loose.isEmpty()) SLOTS else SLOTS - 1)
                        .map { it.title },
                    files = loose.take(SLOTS),
                )
            },
            onOpen = onOpenLibrary,
        ),
        ScrollSection(
            icon = R.drawable.ic_scroll_lists,
            title = "Списки",
            about = "Всё, что ещё предстоит: ещё купить, ещё посмотреть, ещё не забыть.",
            count = countOf(lists.size, "список", "списка", "списков"),
            preview = if (lists.isEmpty()) {
                Preview.Empty
            } else {
                Preview.Lists(
                    lists.take(3).map { list ->
                        ListPreview(
                            title = list.title,
                            left = remaining[list.id] ?: 0,
                            total = listSizes[list.id] ?: 0,
                        )
                    },
                )
            },
            onOpen = onOpenLists,
        ),
    )

    val pager = rememberPagerState(pageCount = { sections.size })
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "Scroll", onNavigationClick = onOpenMenu) {
        Box(modifier = Modifier.fillMaxSize()) {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 56.dp),
                pageSpacing = 12.dp,
            ) { page ->
                SectionWindow(
                    section = sections[page],
                    // Отступ от центра считается во время отрисовки, а не в
                    // композиции: чтение состояния снаружи `graphicsLayer`
                    // пересобирало бы страницу на каждом кадре прокрутки.
                    offset = {
                        ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                    },
                )
            }

            // Стрелки говорят, что за краем экрана есть ещё окна: соседнее
            // выглядывает лишь краем, и без стрелки страница выглядит
            // единственной.
            Arrow(
                icon = Icons.Outlined.KeyboardArrowUp,
                visible = pager.currentPage > 0,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            Arrow(
                icon = Icons.Outlined.KeyboardArrowDown,
                visible = pager.currentPage < sections.lastIndex,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            SectionMarks(
                titles = sections.map { it.title },
                pager = pager,
                onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

/**
 * Названия подразделов столбиком в правом верхнем углу: сколько их всего и на
 * котором ты сейчас.
 *
 * Стрелка говорит только «есть ещё одно», и на четырёх окнах по ней не понять
 * ни сколько их, ни куда ты уже дошёл. Названия отвечают на оба вопроса разом
 * и стоят там, где не спорят с содержимым окна: середина занята знаком,
 * названием и карточками, верх и низ — стрелками.
 *
 * Выделенным считается ближайшее к середине окно, а не осевшее: отметка должна
 * переезжать в тот же миг, когда окно переваливает середину экрана, а не после
 * того, как прокрутка остановится.
 *
 * Прозрачность считается во время отрисовки по тому же отступу от центра, что
 * и у самих окон (см. fadeByOffset): в композиции это пересобирало бы столбец
 * на каждом кадре прокрутки, а так подписи гаснут заодно со своим окном.
 */
@Composable
private fun SectionMarks(
    titles: List<String>,
    pager: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by remember(pager) {
        derivedStateOf { (pager.currentPage + pager.currentPageOffsetFraction).roundToInt() }
    }

    Column(
        modifier = modifier.padding(top = 8.dp, end = 12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        titles.forEachIndexed { index, title ->
            val active = index == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    // Без indication: подпись мелкая, рябь под ней выглядит
                    // крупнее самой подписи.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .padding(horizontal = 6.dp, vertical = 3.dp)
                    .graphicsLayer {
                        val away = ((pager.currentPage + pager.currentPageOffsetFraction) - index)
                            .absoluteValue
                            .coerceIn(0f, 1f)
                        alpha = 1f - 0.55f * away
                    },
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    // Начертание одно на все подписи: от смены жирности
                    // подпись меняет ширину, и столбец дёргался бы посреди
                    // прокрутки. Выделяют цвет и точка.
                    fontWeight = FontWeight.Medium,
                    color = if (active) AccentInk else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (active) Accent else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun Arrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Accent,
        modifier = modifier
            .padding(vertical = 6.dp)
            .size(28.dp)
            .graphicsLayer { alpha = if (visible) 0.75f else 0f },
    )
}

private fun countOf(size: Int, one: String, few: String, many: String): String =
    if (size == 0) "Пока пусто" else "$size ${plural(size, one, few, many)}"

/**
 * Сколько на полке. Книги и файлы считаются порознь: «8 записей» не сказало бы,
 * восемь это книг или лежащих отдельно файлов, а ищут именно то или другое.
 */
private fun shelfCount(books: Int, files: Int): String {
    val shelf = countOf(books, "книга", "книги", "книг")
    val loose = countOf(files, "файл", "файла", "файлов")
    return when {
        books == 0 && files == 0 -> "Пока пусто"
        books == 0 -> loose
        files == 0 -> shelf
        else -> "$shelf · $loose"
    }
}

/** Что показать внутри окна подраздела. */
/** Список в окне подраздела: название и сколько в нём осталось из скольких. */
private data class ListPreview(val title: String, val left: Int, val total: Int)

private sealed interface Preview {
    data object Empty : Preview
    data class Images(val notes: List<Note>) : Preview
    data class Library(val books: List<String>, val files: List<Note>) : Preview
    data class Lists(val lists: List<ListPreview>) : Preview
}

private data class ScrollSection(
    @DrawableRes val icon: Int,
    val title: String,
    val about: String,
    val count: String,
    val preview: Preview,
    val onOpen: () -> Unit,
)

/**
 * Окно подраздела: знак, название, о чём он и последние записи.
 *
 * Подложка растворяется к верхнему и нижнему краю — тем же приёмом, что окно
 * вопроса в разговоре: резкая рамка читалась бы как чужая карточка, а плавная
 * — как проявленное место на странице.
 *
 * Соседние окна бледнеют и мельчают по отступу от центра. Приём тот же, что в
 * «AskyaKnewClaude»: выбранное — то, что в середине, и это видно без подписи.
 */
@Composable
private fun SectionWindow(section: ScrollSection, offset: () -> Float) {
    val panel = AccentSoft.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .fadeByOffset(offset)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.14f to panel,
                    0.86f to panel,
                    1f to Color.Transparent,
                ),
            )
            // Без indication: рябь во весь экран выглядела бы дико.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = section.onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(section.icon),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            PreviewView(section.preview)
        }
    }
}

/**
 * Последние записи подраздела — карточками, тремя в ряд.
 *
 * Карточки те же, что у дел в AskyaDay: тот же радиус, та же тень, та же
 * ширина на троих. Раздел и день должны выглядеть одним приложением, а не
 * двумя разными списками.
 *
 * Пустые места добираются пустыми карточками: ряд из двух растянутых карточек
 * читался бы как другой раздел, а не как «здесь пока две записи».
 */
@Composable
private fun PreviewView(preview: Preview) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        val cards: List<(@Composable () -> Unit)?> = when (preview) {
            Preview.Empty -> emptyList()
            is Preview.Images -> preview.notes.map { note -> { ImageCard(note) } }
            is Preview.Library -> buildList {
                preview.books.forEach { title -> add { BookCard(title) } }
                preview.files.forEach { note -> add { RecordCard(note) } }
            }
            is Preview.Lists -> preview.lists.map { list -> { ListCard(list) } }
        }

        repeat(SLOTS) { index ->
            PreviewSlot(modifier = Modifier.weight(1f), content = cards.getOrNull(index))
        }
    }
}

/** Сколько карточек в ряду. */
private const val SLOTS = 3

/**
 * Место под карточку. Пустое — тоже карточка, только приглушённая: так виден
 * ряд целиком и понятно, что записей меньше трёх.
 */
@Composable
private fun PreviewSlot(modifier: Modifier, content: (@Composable () -> Unit)?) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (content == null) {
                Cream.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (content == null) 0.dp else 6.dp,
        ),
        modifier = modifier.height(148.dp),
    ) {
        if (content != null) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

/** Картинка во всю карточку: её узнают по виду, а не по имени файла. */
@Composable
private fun ImageCard(note: Note) {
    Thumb(note)
}

/**
 * Карточка записи: метка формата и то, что в записи видно с первого взгляда.
 *
 * У файла метка — расширение: `MD` и `PDF` человек различает мгновенно, а
 * «файл» не говорит ничего. У заметки метка не нужна, за неё говорит текст. У
 * ссылки показывается сама ссылка: заголовок «Статья» без адреса не помогает
 * вспомнить, что за статья.
 */
@Composable
private fun RecordCard(note: Note) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        formatOf(note)?.let { badge ->
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AccentInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            text = note.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        glanceOf(note)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Книга: знак сверху, название под ним.
 *
 * Обложки у книги больше нет. Раньше ею была первая лежащая в книге картинка —
 * единственное, ради чего картинку вообще клали в книгу, хотя в её списке она
 * не показывалась. Теперь картинки живут своим разделом с альбомами, и книга
 * снова про то, что в ней читают.
 */
@Composable
private fun BookCard(title: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).background(Cream),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scroll_books),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ListCard(list: ListPreview) {
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Text(
            text = list.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            // Та же строка, что под названием списка на его полке: «3 из 12».
            text = countLine(left = list.left, total = list.total),
            style = MaterialTheme.typography.bodySmall,
            color = if (list.left == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}

/** Превью картинки. Читается в фоне и сразу уменьшается — как в сетке. */
@Composable
private fun Thumb(note: Note) {
    val uri = note.uri
    val bitmap = if (uri != null) rememberThumbnail(uri, targetPx = 256) else null

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        // Доступ к документу могли отозвать: имя файла честнее пустого квадрата.
        Text(
            text = note.title.ifBlank { "Файл" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )
    }
}

/** Строка «что внутри»: адрес у ссылки, первая строка у заметки. */
private fun glanceOf(note: Note): String? {
    val body = note.body.trim()
    if (body.isEmpty()) return null
    val link = LINK.find(body)?.value
    return link ?: body.lineSequence().first { it.isNotBlank() }
}

private val LINK = Regex("""https?://\S+""")

/** Бледнеет и мельчает по мере ухода от середины экрана. */
private fun Modifier.fadeByOffset(offset: () -> Float): Modifier = graphicsLayer {
    val away = offset()
    alpha = 1f - 0.6f * away
    scaleX = 1f - 0.08f * away
    scaleY = scaleX
}

/** Русское число словом: «1 книга», «3 книги», «5 книг». */
internal fun plural(count: Int, one: String, few: String, many: String): String {
    if (count % 100 in 11..14) return many
    return when (count % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}
