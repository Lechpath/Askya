package app.askya.ui.echo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.repository.asTrack
import app.askya.echo.EchoLibrary
import app.askya.echo.Track
import app.askya.echo.formatDuration
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.FadingGrid
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.launch

/**
 * Страница раздела. Разделов три, но внутри каждого есть куда углубиться —
 * в плейлист, в папку, в выбор песен, — поэтому это стек, а не одно значение.
 */
private sealed interface EchoPage {

    data object AllMusic : EchoPage

    data object Playlists : EchoPage

    data object Favorites : EchoPage

    data class Playlist(val id: Long, val title: String) : EchoPage

    /** Выбор песен в плейлист: тот же список всей музыки, но с галочками. */
    data class AddTracks(val id: Long, val title: String) : EchoPage

    data object Folders : EchoPage

    data class Folder(val name: String) : EchoPage
}

/**
 * Списки песен поверх плеера — карточками, как всё раскрытое в Askya.
 *
 * Слой, а не отдельный маршрут навигации: список открывают, чтобы что-нибудь
 * включить, и после выбора он должен исчезнуть, вернув то самое, ради чего
 * человек сюда шёл, — играющую песню.
 *
 * Величина карточки говорит, сколько за ней стоит. «Вся музыка» — большая
 * карточка почти во весь экран: там сотни строк, и щель для подглядывания
 * листать невозможно. «Плейлисты» — небольшая, с обложками: их несколько, и
 * узнают их в лицо, а не читают. «Папки» — что-то посередине: имена читают, но
 * их не сотни.
 *
 * Своя глубина — своим стеком: «назад» внутри слоя поднимает на страницу выше
 * и лишь с верхней закрывает слой целиком. Карточка знает об этом: на верхней
 * странице в её шапке крестик, глубже — стрелка назад.
 *
 * [onLibraryChanged] — музыки на телефоне стало меньше (песню удалили): списки
 * собраны из того, что прочитано один раз при входе, и без этого в них
 * оставалась бы строка, за которой уже нет файла.
 */
@Composable
fun EchoSectionLayer(
    section: EchoSection,
    library: List<Track>?,
    onClose: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onLibraryChanged: () -> Unit = {},
) {
    val stack: SnapshotStateList<EchoPage> = remember(section) {
        listOf(
            when (section) {
                EchoSection.ALL_MUSIC -> EchoPage.AllMusic
                EchoSection.PLAYLISTS -> EchoPage.Playlists
                EchoSection.FOLDERS -> EchoPage.Folders
            },
        ).toMutableStateList()
    }

    val back: () -> Unit = {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else onClose()
    }
    val deep = stack.size > 1

    // Раскрытая дорожка — поверх списка, из которого её раскрыли: карточка
    // поверх карточки, как альбом поверх полки.
    var menu by remember { mutableStateOf<TrackMenu?>(null) }
    val openMenu: (List<Track>, Track) -> Unit = { queue, track -> menu = TrackMenu(queue, track) }

    when (val page = stack.last()) {
        EchoPage.AllMusic -> AllMusicPage(
            library = library,
            deep = deep,
            onBack = back,
            onPlay = onPlay,
            onMenu = openMenu,
        )

        EchoPage.Playlists -> PlaylistsPage(
            deep = deep,
            onBack = back,
            onOpen = { id, title -> stack.add(EchoPage.Playlist(id, title)) },
            onFavorites = { stack.add(EchoPage.Favorites) },
        )

        EchoPage.Favorites -> FavoritesPage(
            onBack = back,
            onPlay = onPlay,
            onMenu = openMenu,
        )

        is EchoPage.Playlist -> PlaylistPage(
            page = page,
            onBack = back,
            onAdd = { stack.add(EchoPage.AddTracks(page.id, page.title)) },
            onPlay = onPlay,
            onMenu = openMenu,
        )

        is EchoPage.AddTracks -> AddTracksPage(
            page = page,
            library = library,
            onBack = back,
        )

        EchoPage.Folders -> FoldersPage(
            library = library,
            deep = deep,
            onBack = back,
            onOpen = { name -> stack.add(EchoPage.Folder(name)) },
        )

        is EchoPage.Folder -> FolderPage(
            page = page,
            library = library,
            onBack = back,
            onPlay = onPlay,
            onMenu = openMenu,
        )
    }

    menu?.let { open ->
        EchoTrackCard(
            track = open.track,
            queue = open.queue,
            onDismiss = { menu = null },
            onPlay = onPlay,
            onOpenFolder = { name ->
                menu = null
                stack.add(EchoPage.Folder(name))
            },
            onRemoved = onLibraryChanged,
        )
    }
}

/** Дорожка, раскрытая поверх списка, и очередь, в которой её нашли. */
private data class TrackMenu(val queue: List<Track>, val track: Track)

/** Вся музыка на телефоне — так, как её видит система. */
@Composable
private fun AllMusicPage(
    library: List<Track>?,
    deep: Boolean,
    onBack: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onMenu: (List<Track>, Track) -> Unit,
) {
    EchoCard(
        title = "Вся музыка",
        subtitle = library?.let { songs(it.size) },
        onDismiss = onBack,
        back = deep,
        width = 0.94f,
        height = 0.9f,
    ) {
        TrackList(
            tracks = library,
            empty = "Музыки не нашлось",
            hint = "Echo играет файлы с телефона. Скачай что-нибудь — и он их увидит.",
            onPlay = onPlay,
            onMenu = onMenu,
        )
    }
}

/**
 * Плейлисты — те, что человек собрал сам.
 *
 * Небольшая карточка с обложками: плейлистов у человека три-пять, и узнают их
 * по картинке первой сложенной песни, а не по строчке текста. Первым стоит
 * избранное — оно есть всегда, и заводить его не нужно.
 */
@Composable
private fun PlaylistsPage(
    deep: Boolean,
    onBack: () -> Unit,
    onOpen: (Long, String) -> Unit,
    onFavorites: () -> Unit,
) {
    val container = appContainer()
    val repository = container.echoRepository
    val scope = rememberCoroutineScope()

    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sizes by remember(repository) { repository.sizes() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val covers by remember(repository) { repository.covers() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val favorites by remember(repository) { repository.favorites() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var naming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Long?>(null) }

    EchoCard(
        title = "Плейлисты",
        subtitle = "Свой порядок песен",
        onDismiss = onBack,
        back = deep,
        width = 0.9f,
        height = 0.68f,
        actions = {
            EchoIcon(
                icon = Icons.Outlined.Add,
                label = "Новый плейлист",
                tint = Sunset,
                onClick = { naming = true },
            )
        },
    ) {
        FadingGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "favorites") {
                CoverTile(
                    title = "Избранное",
                    subtitle = songs(favorites.size),
                    albumId = favorites.firstOrNull()?.albumId ?: 0,
                    uri = favorites.firstOrNull()?.uri,
                    badge = Icons.Outlined.Favorite,
                    onClick = onFavorites,
                )
            }

            items(playlists, key = { it.id }) { playlist ->
                val cover = covers[playlist.id]
                CoverTile(
                    title = playlist.title.ifBlank { "Без названия" },
                    subtitle = songs(sizes[playlist.id] ?: 0),
                    albumId = cover?.albumId ?: 0,
                    uri = cover?.uri,
                    onClick = { onOpen(playlist.id, playlist.title) },
                    onLongClick = { deleting = playlist.id },
                )
            }

            if (playlists.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(2) }) {
                    Text(
                        text = "Своих плейлистов пока нет. Плюс в шапке заведёт первый.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NightMuted,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }

    if (naming) {
        NameDialog(
            title = "Новый плейлист",
            onDismiss = { naming = false },
            onDone = { name ->
                naming = false
                scope.launch { repository.addPlaylist(name) }
            },
        )
    }

    deleting?.let { id ->
        EchoAsk(
            title = "Удалить плейлист?",
            text = "Уйдёт список, а не песни: сами файлы на телефоне останутся.",
            confirm = "Удалить",
            onConfirm = {
                scope.launch { repository.deletePlaylist(id) }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/** Избранное — отмеченные песни, лежащие рядом с плейлистами. */
@Composable
private fun FavoritesPage(
    onBack: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onMenu: (List<Track>, Track) -> Unit,
) {
    val repository = appContainer().echoRepository
    val favorites by remember(repository) { repository.favorites() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val tracks = remember(favorites) { favorites.map { it.asTrack() } }

    EchoCard(
        title = "Избранное",
        subtitle = songs(tracks.size),
        onDismiss = onBack,
        back = true,
        width = 0.94f,
        height = 0.9f,
    ) {
        TrackList(
            tracks = tracks,
            empty = "Отмеченного пока нет",
            hint = "Сердце в карточке песни кладёт её сюда.",
            onPlay = onPlay,
            onMenu = onMenu,
        )
    }
}

/** Один плейлист: его песни в том порядке, в каком их сложили. */
@Composable
private fun PlaylistPage(
    page: EchoPage.Playlist,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onMenu: (List<Track>, Track) -> Unit,
) {
    val container = appContainer()
    val repository = container.echoRepository
    val scope = rememberCoroutineScope()

    val rows by remember(repository, page.id) { repository.tracks(page.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    EchoCard(
        title = page.title.ifBlank { "Плейлист" },
        subtitle = songs(rows.size),
        onDismiss = onBack,
        back = true,
        width = 0.94f,
        height = 0.9f,
        actions = {
            EchoIcon(
                icon = Icons.Outlined.Add,
                label = "Добавить песни",
                tint = Sunset,
                onClick = onAdd,
            )
        },
    ) {
        if (rows.isEmpty()) {
            EmptyState(
                title = "Плейлист пуст",
                hint = "Добавь песни плюсом в шапке — они встанут в том порядке, в каком их выбрали.",
            )
        } else {
            val queue = rows.map { it.asTrack() }

            FadingColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                items(rows, key = { it.id }) { row ->
                    val track = row.asTrack()
                    TrackRow(
                        track = track,
                        onClick = { onPlay(queue, track) },
                        onMenu = { onMenu(queue, track) },
                        trailing = {
                            IconAction(
                                icon = Icons.Outlined.Close,
                                label = "Убрать из плейлиста",
                                onClick = { scope.launch { repository.remove(row.id) } },
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Выбор песен в плейлист.
 *
 * Тап добавляет и оставляет список открытым: песни в плейлист кладут пачкой, и
 * возвращаться в него после каждой значило бы открывать этот список заново.
 * Уже добавленные помечены — иначе одна и та же песня ложится дважды незаметно
 * для того, кто её кладёт.
 */
@Composable
private fun AddTracksPage(page: EchoPage.AddTracks, library: List<Track>?, onBack: () -> Unit) {
    val container = appContainer()
    val repository = container.echoRepository
    val scope = rememberCoroutineScope()

    val inside by remember(repository, page.id) { repository.tracks(page.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val added = remember(inside) { inside.map { it.uri }.toSet() }

    EchoCard(
        title = "В «${page.title.ifBlank { "плейлист" }}»",
        subtitle = "Тап кладёт песню, список остаётся открытым",
        onDismiss = onBack,
        back = true,
        width = 0.94f,
        height = 0.9f,
    ) {
        val list = library

        when {
            list == null -> EmptyState(title = "Ищу музыку", hint = "Смотрю, что есть на телефоне.")

            list.isEmpty() -> EmptyState(
                title = "Музыки не нашлось",
                hint = "Класть в плейлист пока нечего.",
            )

            else -> FadingColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                items(list, key = { it.id }) { track ->
                    val chosen = track.uri in added
                    TrackRow(
                        track = track,
                        current = chosen,
                        onClick = { if (!chosen) scope.launch { repository.add(page.id, track) } },
                        trailing = {
                            Icon(
                                imageVector = if (chosen) Icons.Outlined.GraphicEq else Icons.Outlined.Add,
                                contentDescription = if (chosen) "Уже в плейлисте" else "Добавить",
                                tint = if (chosen) Sunset else NightMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Папки устройства — музыка так, как она лежит на телефоне.
 *
 * Карточками с именами: у папки нет обложки и быть не должно — она про место,
 * а не про звучание, и картинка первой песни врала бы о том, что внутри.
 */
@Composable
private fun FoldersPage(
    library: List<Track>?,
    deep: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val folders = remember(library) { library?.let { EchoLibrary.folders(it) } }

    EchoCard(
        title = "Папки",
        subtitle = folders?.let { "${it.size} на телефоне" },
        onDismiss = onBack,
        back = deep,
        width = 0.92f,
        height = 0.8f,
    ) {
        when {
            folders == null -> EmptyState(
                title = "Ищу музыку",
                hint = "Смотрю, что есть на телефоне.",
            )

            folders.isEmpty() -> EmptyState(
                title = "Папок с музыкой нет",
                hint = "Как только на телефоне появятся песни, здесь появятся их папки.",
            )

            else -> FadingGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(folders, key = { it.name }) { folder ->
                    FolderTile(
                        name = folder.name,
                        subtitle = songs(folder.tracks.size),
                        onClick = { onOpen(folder.name) },
                    )
                }
            }
        }
    }
}

/** Одна папка: всё, что в ней лежит. */
@Composable
private fun FolderPage(
    page: EchoPage.Folder,
    library: List<Track>?,
    onBack: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onMenu: (List<Track>, Track) -> Unit,
) {
    val inside = remember(library, page.name) { library?.filter { it.folder == page.name } }

    EchoCard(
        title = page.name,
        subtitle = inside?.let { songs(it.size) },
        onDismiss = onBack,
        back = true,
        width = 0.94f,
        height = 0.9f,
    ) {
        TrackList(
            tracks = inside,
            empty = "Папка пуста",
            hint = "Похоже, песни из неё убрали.",
            onPlay = onPlay,
            onMenu = onMenu,
        )
    }
}

/** Список песен с общей очередью: включённая песня ведёт за собой соседние. */
@Composable
internal fun TrackList(
    tracks: List<Track>?,
    empty: String,
    hint: String,
    onPlay: (List<Track>, Track) -> Unit,
    onMenu: ((List<Track>, Track) -> Unit)? = null,
) {
    val container = appContainer()
    val state by container.echoPlayer.state.collectAsStateWithLifecycle()

    when {
        tracks == null -> EmptyState(title = "Ищу музыку", hint = "Смотрю, что есть на телефоне.")

        tracks.isEmpty() -> EmptyState(title = empty, hint = hint)

        else -> FadingColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    current = track.uri == state.track?.uri,
                    onClick = { onPlay(tracks, track) },
                    onMenu = onMenu?.let { open -> { open(tracks, track) } },
                )
            }
        }
    }
}

/**
 * Строка песни: обложка, название, исполнитель и длительность.
 *
 * Справа — три точки: за ними всё, что с песней делают помимо «включить», —
 * очередь, плейлист, отметка, отправка. Раньше строка умела только одно, и
 * плеер этим заметно отличался от любого другого в худшую сторону.
 */
@Composable
internal fun TrackRow(
    track: Track,
    current: Boolean = false,
    onClick: () -> Unit,
    onMenu: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverThumb(
            albumId = track.albumId,
            uri = track.uri,
            modifier = Modifier.size(44.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (current) Sunset else NightInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(start = 8.dp),
        )
        onMenu?.let {
            IconAction(icon = Icons.Outlined.MoreVert, label = "Что с песней", onClick = it)
        }
        trailing?.let {
            Box(modifier = Modifier.padding(start = 4.dp)) { it() }
        }
    }
}

/**
 * Карточка с обложкой — плейлист и избранное.
 *
 * Квадрат обложки, под ним имя и счёт: та же плитка, что у альбома картинок в
 * Scroll, только ночная. Долгое нажатие — удалить; крестик на каждой плитке
 * стоял бы поверх обложки и мешал её видеть.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CoverTile(
    title: String,
    subtitle: String,
    albumId: Long,
    uri: String?,
    onClick: () -> Unit,
    badge: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            CoverThumb(albumId = albumId, uri = uri, modifier = Modifier.fillMaxSize())
            badge?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = Sunset,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = NightInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            maxLines = 1,
        )
    }
}

/** Карточка папки: имя крупно, счёт под ним. Обложки у места не бывает. */
@Composable
private fun FolderTile(name: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            tint = Sunset,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = name,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            color = NightInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = NightMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Окно с одним полем — названием плейлиста. Поле получает курсор сразу:
 * плейлист заводят, уже зная, как его назвать.
 */
@Composable
private fun NameDialog(title: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val ready = name.isNotBlank()

    EchoDialog(title = title, onDismiss = onDismiss) {
        EchoField(
            value = name,
            onValueChange = { name = it },
            hint = "Например, «В дорогу»",
            modifier = Modifier.padding(top = 14.dp),
            onDone = { if (ready) onDone(name) },
        )

        // Без названия плейлист не завести: пустая полка не отличима от
        // соседней. Слово гаснет, пока в поле ничего нет.
        EchoPill(
            label = "Создать",
            chosen = ready,
            onClick = { if (ready) onDone(name) },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/** «7 песен» — с правильным окончанием: список читают, а не считают. */
internal fun songs(count: Int): String {
    val hundred = count % 100
    val ten = count % 10
    return when {
        hundred in 11..14 -> "$count песен"
        ten == 1 -> "$count песня"
        ten in 2..4 -> "$count песни"
        else -> "$count песен"
    }
}
