package app.askya.ui.video

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.androidContainer
import app.askya.data.entity.VideoPlaylist
import app.askya.data.repository.asClip
import app.askya.echo.formatDuration
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingGrid
import app.askya.ui.echo.EchoAsk
import app.askya.ui.echo.EchoDialog
import app.askya.ui.echo.EchoField
import app.askya.ui.echo.EchoPill
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.video.Clip
import kotlinx.coroutines.launch

/**
 * Плейлисты AskyaV — то, что человек сложил смотреть подряд.
 *
 * Папки уже есть, и плейлисты не повторяют их: папка говорит, где файл лежит,
 * а плейлист — что и в каком порядке смотреть. Три серии из разных папок,
 * отложенный на вечер фильм, подборка мультфильмов ребёнку — всё это в папках
 * не выражается никак, потому что папку раскладывал не человек, а тот, кто
 * файлы скачивал.
 *
 * Сеткой с кадрами, а не списком строк: плейлистов у человека три-пять, и
 * узнают их по картинке первого сложенного ролика, а не по строчке текста —
 * ровно как плейлисты в Echo узнают по обложке.
 *
 * Долгое нажатие раскрывает плейлист карточкой: смотреть подряд, переименовать,
 * удалить. Коротко — открыть и посмотреть, что внутри.
 */
@Composable
fun VideoPlaylistsGrid(
    onOpen: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val repository = androidContainer().videoRepository

    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val sizes by remember(repository) { repository.sizes() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val lengths by remember(repository) { repository.lengths() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val covers by remember(repository) { repository.covers() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())

    var menu by remember { mutableStateOf<VideoPlaylist?>(null) }

    FadingGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (playlists.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(2) }) {
                EmptyState(
                    title = "Плейлистов пока нет",
                    hint = "Плюс в шапке заведёт первый. Складывать в него ролики — " +
                        "долгим нажатием по строчке списка.",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            val count = sizes[playlist.id] ?: 0
            PlaylistTile(
                title = playlist.title.ifBlank { "Без названия" },
                subtitle = listOfNotNull(
                    clipsWord(count),
                    lengths[playlist.id]?.takeIf { it > 0 }?.let { formatDuration(it) },
                ).joinToString(" · "),
                cover = covers[playlist.id]?.asClip(),
                onClick = { onOpen(playlist.id) },
                onLongClick = { menu = playlist },
            )
        }
    }

    menu?.let { playlist ->
        VideoPlaylistCard(
            playlist = playlist,
            count = sizes[playlist.id] ?: 0,
            onDismiss = { menu = null },
            onOpen = {
                menu = null
                onOpen(playlist.id)
            },
            onPlay = {
                menu = null
                onPlay(playlist.id)
            },
        )
    }
}

/**
 * Карточка плейлиста: кадр первого ролика, имя под ним и счёт.
 *
 * Кадр берётся у первого сложенного, а своей картинки у плейлиста нет: человек
 * складывает фильмы, а не рисует папке лицо. Пустой плейлист показывает свой
 * знак — пустой прямоугольник читался бы как незагрузившаяся картинка.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTile(
    title: String,
    subtitle: String,
    cover: Clip?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(NightPanelSoft)
                .border(1.dp, NightBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (cover != null) {
                ClipFrame(clip = cover, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = NightMuted,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
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
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Плейлист, раскрытый карточкой: что с ним можно сделать целиком.
 *
 * Переименование и удаление живут здесь, а не в шапке открытого плейлиста:
 * шапка — про то, что внутри, и складывать в неё же управление самим списком
 * значило бы дать три значка на одну строку.
 */
@Composable
private fun VideoPlaylistCard(
    playlist: VideoPlaylist,
    count: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val repository = androidContainer().videoRepository
    val scope = rememberCoroutineScope()

    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(playlist.title) }

    when {
        renaming -> EchoDialog(title = "Имя плейлиста", onDismiss = { renaming = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EchoField(
                    value = name,
                    onValueChange = { name = it },
                    hint = "Как назвать",
                    onDone = {
                        scope.launch { repository.rename(playlist.id, name) }
                        onDismiss()
                    },
                )
                EchoPill(
                    label = "Назвать",
                    chosen = true,
                    onClick = {
                        scope.launch { repository.rename(playlist.id, name) }
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        deleting -> EchoAsk(
            title = "Удалить плейлист?",
            text = "Уйдёт список, а не файлы: само видео на телефоне останется.",
            confirm = "Удалить",
            onConfirm = {
                scope.launch { repository.deletePlaylist(playlist.id) }
                onDismiss()
            },
            onDismiss = { deleting = false },
        )

        else -> EchoDialog(
            title = playlist.title.ifBlank { "Без названия" },
            onDismiss = onDismiss,
        ) {
            Text(
                text = clipsWord(count),
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaylistAction(
                    icon = Icons.Outlined.PlayArrow,
                    title = "Смотреть подряд",
                    about = "С первого ролика; дальше пойдут следом",
                    tint = Sunset,
                    onClick = onPlay,
                )
                PlaylistAction(
                    icon = Icons.Outlined.VideoLibrary,
                    title = "Открыть",
                    about = "Посмотреть, что внутри",
                    onClick = onOpen,
                )
                PlaylistAction(
                    icon = Icons.Outlined.DriveFileRenameOutline,
                    title = "Переименовать",
                    about = "Имя списка, не файлов",
                    onClick = {
                        name = playlist.title
                        renaming = true
                    },
                )
                PlaylistAction(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Удалить плейлист",
                    about = "Файлы на телефоне останутся",
                    onClick = { deleting = true },
                )
            }
        }
    }
}

/** Строка действия в карточке плейлиста — та же, что в карточке ролика. */
@Composable
private fun PlaylistAction(
    icon: ImageVector,
    title: String,
    about: String,
    onClick: () -> Unit,
    tint: Color = NightMuted,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = NightInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = about,
                style = MaterialTheme.typography.bodySmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Окно нового плейлиста: одно поле и одно слово. */
@Composable
fun NewPlaylistDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    EchoDialog(title = "Новый плейлист", onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EchoField(
                value = name,
                onValueChange = { name = it },
                hint = "Например, «Смотреть вечером»",
                onDone = { onDone(name) },
            )
            EchoPill(
                label = "Завести",
                chosen = true,
                onClick = { onDone(name) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** «7 роликов» — с правильным окончанием: список читают, а не считают. */
internal fun clipsWord(count: Int): String {
    val tens = count % 100
    if (tens in 11..14) return "$count роликов"
    return when (count % 10) {
        1 -> "$count ролик"
        2, 3, 4 -> "$count ролика"
        else -> "$count роликов"
    }
}
