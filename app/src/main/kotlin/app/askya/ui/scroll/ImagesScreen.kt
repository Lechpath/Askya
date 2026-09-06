package app.askya.ui.scroll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.R
import app.askya.app.appContainer
import app.askya.data.entity.ImageAlbum
import app.askya.data.entity.Note
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingGrid
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink
import app.askya.ui.theme.cardShade

/**
 * «Изображения» — все картинки Scroll сеткой и альбомы, по которым их
 * разложили.
 *
 * Раздел живёт сам по себе и с «Книгами» не связан. Раньше картинку при
 * добавлении спрашивали «в какую книгу?», и чтобы собрать снимки поездки,
 * приходилось заводить книгу — полку, на которой вообще-то лежат заметки и
 * pdf'ы, а картинки в её списке даже не показывались. Теперь у картинок свои
 * папки — альбомы, и заводят их здесь же.
 *
 * Сетка в три столбца: узнать картинку хватает, а листать нужно меньше.
 *
 * [albumId] `null` означает весь раздел: сверху альбомы, под ними все картинки
 * разом — и разложенные, и нет. С номером альбома тот же экран показывает один
 * альбом; разница между ними ровно в том, какие картинки в сетке, поэтому
 * экран один, как у «Файлов» и книги.
 */
@Composable
fun ImagesScreen(
    albumId: Long? = null,
    onBack: () -> Unit,
    onOpenCard: (Long) -> Unit,
    onViewImage: (Long) -> Unit,
    onOpenAlbum: (Long) -> Unit = {},
    onCollage: () -> Unit = {},
) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(appContainer()))
    val store = appContainer().imageStore
    val folder = store.folderName
    val context = LocalContext.current

    val images by if (albumId == null) {
        viewModel.images.collectAsStateWithLifecycle()
    } else {
        remember(albumId) { viewModel.inAlbum(albumId) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
    }
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val sizes by viewModel.albumSizes.collectAsStateWithLifecycle()
    val covers by viewModel.albumCovers.collectAsStateWithLifecycle()
    val album by remember(albumId) {
        viewModel.album(albumId ?: -1L)
    }.collectAsStateWithLifecycle(initialValue = null)

    // Сколько картинок из пачки не скопировалось. Числом, а не «да/нет»: у
    // пачки важно, сколько именно не дошло.
    var failed by remember { mutableStateOf(0) }
    // Пачку только что добавили и спрашиваем, куда её положить.
    var placing by remember { mutableStateOf(false) }
    // Номера только что заведённых записей — до того, как они появятся в сетке.
    var added by remember { mutableStateOf(emptySet<Long>()) }
    var creatingAlbum by remember { mutableStateOf(false) }
    var editingAlbum by remember { mutableStateOf<ImageAlbum?>(null) }

    // Выбранные картинки. Номерами, а не записями: список приходит из базы
    // заново на каждое изменение, и хранить в выборе старые копии записей
    // значило бы убирать не то, что выбрано, а то, что было выбрано раньше.
    var selected by remember(albumId) { mutableStateOf(emptySet<Long>()) }
    var moving by remember { mutableStateOf(false) }
    var noShare by remember { mutableStateOf(false) }
    var movingToNew by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }

    val selecting = selected.isNotEmpty()

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    // Выбранное, которого в сетке уже нет (убрали, переложили), уходит из
    // выбора само: иначе счётчик считал бы то, чего не видно.
    val present = images.map { it.id }.toSet()
    if (selected.any { it !in present }) selected = selected intersect present

    // Добавленная пачка показывается выбранной — но только когда она уже в
    // сетке. Записи заводятся раньше, чем база успевает отдать новый список, и
    // выбор, поставленный сразу, тут же вычистила бы строка выше.
    LaunchedEffect(added, present) {
        if (added.isEmpty() || !present.containsAll(added)) return@LaunchedEffect
        // Стоя в альбоме, спрашивать нечего: пачка уже в нём.
        if (albumId == null) {
            selected = added
            placing = true
        }
        added = emptySet()
    }

    // «Назад» сначала снимает выбор, а не уводит с экрана: пока что-то
    // выбрано, это самое близкое к «отменить».
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Картинки копируются в папку Askya, а не остаются ссылками на чужие файлы
    // (см. rememberImageImport): раздел должен держать их сам.
    val pickImages = rememberImageImport(onFailed = { failed = it }) { picked ->
        if (picked.size == 1) {
            // Одна — дальше карточка: подписать и выбрать альбом. Иначе
            // картинка осталась бы с именем вроде IMG_20260813_2231.jpg.
            // Стоя в альбоме, кладут в него же: в карточке он уже выбран.
            viewModel.addFile(picked.first(), topicId = null, albumId = albumId, onCreated = onOpenCard)
        } else {
            // Пачку не подписывают: подпись у каждой своя, и спрашивать её
            // десять раз подряд — не разговор, а допрос. Общий у пачки только
            // альбом, про него и спрашиваем — один раз и на всех. Добавленное
            // остаётся выбранным: чаще всего с ним тут же что-то делают.
            viewModel.addFiles(picked, topicId = null, albumId = albumId) { ids ->
                added = ids.toSet()
            }
        }
    }

    ScreenScaffold(
        title = when {
            selecting -> "Выбрано ${selected.size}"
            albumId == null -> "Изображения"
            else -> album?.title.orEmpty().ifEmpty { "Альбом" }
        },
        // Пока идёт выбор, стрелка снимает его, а не уводит назад: она в том
        // же месте, где и «назад», и делает то же самое — шаг из выбора.
        onNavigationClick = if (selecting) ({ selected = emptySet() }) else onBack,
        navigationIsBack = true,
        actions = {
            if (selecting) {
                HeaderIcon(
                    icon = Icons.Outlined.Share,
                    contentDescription = "Поделиться",
                    onClick = {
                        // Ссылки берутся у хранилища: наружу отдают не то, что
                        // записано в Scroll, а то, что чужое приложение сможет
                        // прочитать (см. ImageStore.shareable).
                        val links = images
                            .filter { it.id in selected }
                            .mapNotNull { store.shareable(it.uri) }
                        if (links.isEmpty() || !shareImages(context, links)) noShare = true
                    },
                )
                HeaderIcon(
                    icon = Icons.Outlined.DriveFileMove,
                    contentDescription = "Переложить в альбом",
                    onClick = { moving = true },
                )
                HeaderIcon(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = "Убрать",
                    onClick = { removing = true },
                )
            } else if (albumId == null) {
                // Коллаж собирается из того, что уже в разделе, поэтому вход в
                // него здесь, а не в общем меню.
                HeaderIcon(
                    icon = Icons.Outlined.Dashboard,
                    contentDescription = "Коллаж",
                    onClick = onCollage,
                )
            } else {
                album?.let { current ->
                    HeaderIcon(
                        icon = Icons.Outlined.EditNote,
                        contentDescription = "Переименовать альбом",
                        onClick = { editingAlbum = current },
                    )
                }
            }
        },
        floatingActionButton = {
            // Пока выбирают, кнопки нет: добавлять картинку посреди выбора
            // незачем, а место под нижним рядом сетки занято выбранным.
            if (selecting) return@ScreenScaffold
            // Та же чёрная «таблетка» в нижнем углу, что в AskyaDay, «Файлах» и
            // «Списках»: добавляют везде одним жестом и в одном месте экрана.
            // Раньше кнопка стояла первой строкой сетки — занимала ряд картинок
            // и уезжала вверх вместе с ними, как только сетку прокручивали.
            // Внутри плюс и знак раздела: плюс говорит «добавить», знак — что
            // именно добавляют, и на слово «изображение» места в таблетке нет.
            ExtendedFloatingActionButton(
                onClick = pickImages,
                shape = RoundedCornerShape(percent = 50),
                containerColor = Ink,
                contentColor = Accent,
                modifier = Modifier.width(104.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Добавить изображение",
                    modifier = Modifier.size(22.dp),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_scroll_images),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 6.dp).size(26.dp),
                )
            }
        },
    ) {
        val grid = rememberLazyGridState()
        FadingGrid(
            state = grid,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            // Снизу столько, чтобы последний ряд картинок выходил из-под
            // кнопки: она висит над сеткой, а не стоит в ней.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (albumId == null) {
                // Альбомы лентой над сеткой: их единицы, и вертикальный список
                // отобрал бы у картинок пол-экрана.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Albums(
                        albums = albums,
                        sizes = sizes,
                        covers = covers,
                        onOpen = onOpenAlbum,
                        onEdit = { editingAlbum = it },
                        onNew = { creatingAlbum = true },
                    )
                }

                if (albums.isNotEmpty() && images.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel("Все картинки")
                    }
                }
            }

            if (images.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = if (albumId == null) "Картинок пока нет" else "Альбом пуст",
                        hint = "Добавь первую кнопкой внизу.",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }

            items(images, key = { it.id }) { image ->
                Thumb(
                    note = image,
                    picked = image.id in selected,
                    // Долгое нажатие начинает выбор, дальше выбирают тапом:
                    // держать палец на каждой картинке пачки — это долго.
                    onClick = { if (selecting) toggle(image.id) else onViewImage(image.id) },
                    onLongClick = { toggle(image.id) },
                )
            }
        }
    }

    if (failed > 0) {
        AskyaNotice(
            title = if (failed == 1) "Не вышло добавить" else "Не всё добавилось",
            text = if (failed == 1) {
                "Картинку не удалось скопировать в $folder — файл не прочитался или на телефоне нет места."
            } else {
                "$failed ${plural(failed, "картинку", "картинки", "картинок")} " +
                    "не удалось скопировать в $folder — файлы не прочитались или на телефоне нет места."
            },
            onDismiss = { failed = 0 },
        )
    }

    if (placing) {
        AlbumPickDialog(
            count = selected.size,
            title = "В какой альбом?",
            albums = albums.map { it.id to it.title },
            onDismiss = { placing = false },
            onPick = { target ->
                viewModel.moveImages(selected, target)
                selected = emptySet()
                placing = false
            },
            onNew = {
                placing = false
                movingToNew = true
            },
        )
    }

    if (creatingAlbum) {
        AlbumDialog(
            title = "Новый альбом",
            initial = "",
            onDismiss = { creatingAlbum = false },
            onConfirm = {
                viewModel.addAlbum(it)
                creatingAlbum = false
            },
        )
    }

    editingAlbum?.let { current ->
        AlbumDialog(
            title = "Альбом",
            initial = current.title,
            hint = "Если убрать альбом, картинки не пропадут — вернутся в общую сетку.",
            onDismiss = { editingAlbum = null },
            onConfirm = {
                viewModel.renameAlbum(current, it)
                editingAlbum = null
            },
            onDelete = {
                viewModel.deleteAlbum(current.id)
                editingAlbum = null
                // Удалили альбом, стоя в нём же: показывать пустой экран
                // несуществующего альбома незачем.
                if (albumId == current.id) onBack()
            },
        )
    }

    if (noShare) NoShareDialog(onDismiss = { noShare = false })

    if (moving) {
        AlbumPickDialog(
            count = selected.size,
            albums = albums.map { it.id to it.title },
            onDismiss = { moving = false },
            onPick = { target ->
                viewModel.moveImages(selected, target)
                selected = emptySet()
                moving = false
            },
            onNew = {
                moving = false
                movingToNew = true
            },
        )
    }

    if (movingToNew) {
        AlbumDialog(
            title = "Новый альбом",
            initial = "",
            onDismiss = { movingToNew = false },
            // Альбом заводится, и выбранное сразу переезжает в него: его затем
            // и создавали.
            onConfirm = { name ->
                val moved = selected
                viewModel.addAlbum(name) { id -> viewModel.moveImages(moved, id) }
                selected = emptySet()
                movingToNew = false
            },
        )
    }

    if (removing) {
        val count = selected.size
        AskyaAsk(
            title = "Убрать $count ${plural(count, "картинку", "картинки", "картинок")}?",
            // Прямо: копии наши, и они удаляются вместе с записями.
            text = if (count == 1) {
                "Картинка уйдёт из Askya вместе с файлом в папке $folder."
            } else {
                "Картинки уйдут из Askya вместе с файлами в папке $folder."
            },
            confirm = "Убрать",
            onConfirm = {
                viewModel.delete(images.filter { it.id in selected })
                selected = emptySet()
                removing = false
            },
            onDismiss = { removing = false },
        )
    }
}

/**
 * Лента альбомов. Первым — «новый»: заводят альбом чаще, чем кажется, и
 * прятать это в меню значило бы не завести ни одного.
 *
 * Тап открывает альбом, долгое нажатие правит — тот же уговор, что у книг.
 */
@Composable
private fun Albums(
    albums: List<ImageAlbum>,
    sizes: Map<Long, Int>,
    covers: Map<Long, Note>,
    onOpen: (Long) -> Unit,
    onEdit: (ImageAlbum) -> Unit,
    onNew: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel("Альбомы", top = 4.dp)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item {
                NewAlbumCard(onClick = onNew)
            }

            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    count = sizes[album.id] ?: 0,
                    cover = covers[album.id],
                    onClick = { onOpen(album.id) },
                    onLongClick = { onEdit(album) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 16.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = top, bottom = 6.dp),
    )
}

/** Обложка альбома — последняя положенная в него картинка, под ней подпись. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: ImageAlbum,
    count: Int,
    cover: Note?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(ALBUM_WIDTH)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(ALBUM_WIDTH)
                .cardShade(RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                // Не Cream: он совпадает с фоном экрана, и пустой альбом
                // выглядел бы значком в воздухе, а не карточкой.
                .background(AccentSoft.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = cover?.uri?.let { rememberThumbnail(it, targetPx = 384) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_album),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = album.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = if (count == 0) {
                "Пусто"
            } else {
                "$count ${plural(count, "картинка", "картинки", "картинок")}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewAlbumCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(ALBUM_WIDTH)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(ALBUM_WIDTH)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Новый альбом",
                tint = Accent,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = "Новый альбом",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Ширина карточки альбома: три штуки видно разом, четвёртая выглядывает. */
private val ALBUM_WIDTH = 108.dp

/**
 * Ячейка сетки. Пока картинка читается — и если прочитать не вышло — на её
 * месте стоит имя файла: пустой квадрат не сказал бы, что здесь вообще что-то
 * есть, а файл могли унести из папки чужим проводником.
 *
 * Выбранная притемняется и получает галочку в углу: одной галочки на светлой
 * картинке не видно, а одно притемнение не отличить от тени на снимке.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Thumb(
    note: Note,
    picked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = note.uri?.let { rememberThumbnail(it) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .cardShade(RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = note.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = note.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
            )
        }

        if (picked) {
            Box(modifier = Modifier.fillMaxSize().background(Ink.copy(alpha = 0.35f)))
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Выбрано",
                tint = Accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp),
            )
        }
    }
}
