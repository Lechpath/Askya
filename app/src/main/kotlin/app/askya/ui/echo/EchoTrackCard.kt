package app.askya.ui.echo

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.Track
import app.askya.echo.formatDuration
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightDanger
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.launch

/**
 * Что показывает карточка дорожки: сами действия, выбор плейлиста, сведения
 * или вопрос об удалении.
 */
private enum class TrackStep { ACTIONS, PLAYLIST, DETAILS, DELETE }

/**
 * Дорожка, раскрытая карточкой: всё, что с песней можно сделать.
 *
 * До сих пор строку песни можно было только нажать — и она начинала играть.
 * Всё остальное, чего ждут от плеера («поставь эту следующей», «положи в
 * плейлист», «отметь»), делалось долгим путём через другой раздел или не
 * делалось вовсе.
 *
 * Карточка, а не выпадающее меню: в Askya раскрытое — это карточка, и меню
 * Material со списком серых строк выглядело бы здесь чужим. Сверху обложка и
 * подписи — видно, о какой песне речь, а не «действия для чего-то, на что ты
 * только что нажал».
 *
 * [queue] — очередь, в которой дорожку нашли: «играть» должно включать её
 * вместе с соседями, а не одну посреди пустоты.
 *
 * [onRemoved] — песню стёрли с телефона: список, из которого её открыли, надо
 * перечитать, иначе в нём останется строка, за которой уже ничего нет.
 */
@Composable
fun EchoTrackCard(
    track: Track,
    queue: List<Track>,
    onDismiss: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onOpenFolder: ((String) -> Unit)? = null,
    onRemoved: () -> Unit = {},
) {
    val container = appContainer()
    val player = container.echoPlayer
    val repository = container.echoRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(TrackStep.ACTIONS) }

    val favorites by remember(repository) { repository.favoriteUris() }
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val favorite = track.uri in favorites

    val playlists by remember(repository) { repository.playlists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Файл ушёл — за ним уходят и все следы: строки в плейлистах, отметка в
    // избранном и место в очереди плеера. Ссылка на пропавший файл — это
    // строка, которая молча не играет.
    val remove = rememberTrackRemover { removed ->
        scope.launch { repository.forget(removed.uri) }
        player.forget(removed.uri)
        onRemoved()
        onDismiss()
    }

    EchoCard(
        title = track.title,
        subtitle = track.artist,
        onDismiss = { if (step == TrackStep.ACTIONS) onDismiss() else step = TrackStep.ACTIONS },
        back = step != TrackStep.ACTIONS,
        width = 0.9f,
        height = when (step) {
            TrackStep.ACTIONS -> 0.78f
            TrackStep.DELETE -> 0.42f
            else -> 0.6f
        },
    ) {
        when (step) {
            TrackStep.ACTIONS -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingVerticalScroll()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CoverThumb(
                        albumId = track.albumId,
                        uri = track.uri,
                        modifier = Modifier.size(64.dp),
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            text = track.album.ifBlank { track.folder.ifBlank { "Одиночная запись" } },
                            style = MaterialTheme.typography.bodyMedium,
                            color = NightInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatDuration(track.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = NightMuted,
                        )
                    }
                }

                TrackAction(Icons.Outlined.PlayArrow, "Играть", "Сейчас же, с этого места списка") {
                    onPlay(queue, track)
                    onDismiss()
                }
                TrackAction(
                    icon = Icons.Outlined.PlaylistPlay,
                    title = "Играть следующей",
                    about = "Встанет сразу за той, что играет",
                ) {
                    player.playNext(track)
                    onDismiss()
                }
                TrackAction(
                    icon = Icons.Outlined.QueueMusic,
                    title = "В очередь",
                    about = "В конец того, что уже поставлено",
                ) {
                    player.enqueue(track)
                    onDismiss()
                }
                TrackAction(
                    icon = if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    title = if (favorite) "Убрать из избранного" else "В избранное",
                    about = "Избранное лежит первым в плейлистах",
                    tint = if (favorite) Sunset else NightMuted,
                ) {
                    scope.launch { repository.toggleFavorite(track, !favorite) }
                }
                TrackAction(
                    icon = Icons.Outlined.PlaylistAdd,
                    title = "В плейлист",
                    about = "В один из собранных",
                ) { step = TrackStep.PLAYLIST }

                if (onOpenFolder != null && track.folder.isNotBlank()) {
                    TrackAction(
                        icon = Icons.Outlined.FolderOpen,
                        title = "Показать папку",
                        about = track.folder,
                    ) { onOpenFolder(track.folder) }
                }

                TrackAction(
                    icon = Icons.Outlined.Share,
                    title = "Поделиться",
                    about = "Отправить файл, как он лежит на телефоне",
                ) {
                    share(context, track)
                    onDismiss()
                }
                TrackAction(
                    icon = Icons.Outlined.Info,
                    title = "Сведения",
                    about = "Исполнитель, альбом, длительность, файл",
                ) { step = TrackStep.DETAILS }

                // Удаление стоит последним и отмечено красным: промах пальцем
                // по дороге к «сведениям» не должен попадать в необратимое.
                TrackAction(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Удалить с телефона",
                    about = "Файл будет стёрт, а не убран из Echo",
                    tint = NightDanger,
                ) {
                    // С Android 11 своё окно спрашивает система, и наше было бы
                    // вторым вопросом об одном и том же.
                    if (systemAsksBeforeDelete()) remove(track) else step = TrackStep.DELETE
                }
            }

            TrackStep.PLAYLIST -> if (playlists.isEmpty()) {
                Text(
                    text = "Плейлистов пока нет. Заведи первый в разделе «Плейлисты» — " +
                        "и песни будут ложиться туда.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightMuted,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                FadingColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        TrackAction(
                            icon = Icons.Outlined.QueueMusic,
                            title = playlist.title.ifBlank { "Без названия" },
                            about = "Положить в конец",
                        ) {
                            scope.launch { repository.add(playlist.id, track) }
                            onDismiss()
                        }
                    }
                }
            }

            TrackStep.DELETE -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Удалить файл с телефона?",
                    style = MaterialTheme.typography.titleMedium,
                    color = NightInk,
                )
                Text(
                    text = "«${track.title}» исчезнет из памяти телефона — не только из " +
                        "Echo. Вернуть не получится.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        EchoPill(
                            label = "Оставить",
                            chosen = false,
                            onClick = { step = TrackStep.ACTIONS },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        EchoPill(
                            label = "Удалить",
                            chosen = false,
                            danger = true,
                            onClick = { remove(track) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            TrackStep.DETAILS -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fadingVerticalScroll()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Detail("Название", track.title)
                Detail("Исполнитель", track.artist)
                Detail("Альбом", track.album.ifBlank { "—" })
                Detail("Папка", track.folder.ifBlank { "—" })
                Detail("Длительность", formatDuration(track.durationMs))
                Detail("Файл", track.uri)
            }
        }
    }
}

/** Строка действия: знак, что случится, и строчка пояснения под ним. */
@Composable
private fun TrackAction(
    icon: ImageVector,
    title: String,
    about: String,
    tint: Color = NightMuted,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
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

/** Строка сведений: слева чем является, справа что записано. */
@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = NightMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = NightInk,
        )
    }
}

/**
 * Отдать песню наружу.
 *
 * Отправляется ссылка на файл в MediaStore, а не копия: файл и так лежит у
 * системы, и второй его экземпляр в папке приложения не нужен никому. Право
 * на чтение выдаётся вместе с намерением и только тому, кого выберут.
 */
private fun share(context: android.content.Context, track: Track) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(track.uri))
        putExtra(Intent.EXTRA_TITLE, track.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(send, track.title))
    }
}
