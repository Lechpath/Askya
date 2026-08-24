package app.askya.ui.echo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.echo.Track
import app.askya.echo.formatDuration
import app.askya.ui.components.EmptyState
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset

/**
 * Очередь — что играет и что за ним.
 *
 * Появилась вместе с «играть следующей» и «в очередь» в карточке песни: класть
 * что-то в очередь, которую нельзя увидеть, — значит просить верить на слово.
 *
 * Порядок здесь тот, в котором дорожки прозвучат, а не тот, в каком они лежат
 * в папке: при включённом «вперемешку» это разные списки, и показывать вместо
 * очереди исходный значило бы соврать о том, что будет дальше.
 *
 * Список открывается на играющей дорожке: очередь смотрят, чтобы понять, что
 * дальше, а «дальше» начинается от неё.
 */
@Composable
fun EchoQueueCard(onDismiss: () -> Unit, onTrack: (Track) -> Unit) {
    val player = appContainer().echoPlayer
    val state by player.state.collectAsStateWithLifecycle()

    val list = rememberLazyListState()
    LaunchedEffect(state.queueAt) {
        if (state.queueAt >= 0) list.scrollToItem(state.queueAt)
    }

    EchoCard(
        title = "Очередь",
        subtitle = if (state.queue.isEmpty()) null else songs(state.queue.size),
        onDismiss = onDismiss,
        width = 0.94f,
        height = 0.82f,
    ) {
        if (state.queue.isEmpty()) {
            EmptyState(
                title = "В очереди пусто",
                hint = "Включи что-нибудь — и соседние песни встанут сюда сами.",
            )
        } else {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                itemsIndexed(state.queue, key = { index, track -> "$index-${track.uri}" }) { index, track ->
                    val current = index == state.queueAt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (current) NightPanelSoft else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { player.jump(index) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // У играющей вместо номера — знак звука: место в
                        // очереди у неё и так видно, а вот что играет именно
                        // она, из номера не следует.
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            if (current) {
                                Icon(
                                    imageVector = Icons.Outlined.GraphicEq,
                                    contentDescription = "Играет",
                                    tint = Sunset,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NightMuted,
                                )
                            }
                        }

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
                        )

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onTrack(track) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "Что с песней",
                                tint = NightMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
