package app.askya.ui.echo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.delay

/**
 * Панель играющей дорожки — нижняя строка лаборатории и настроек Echo.
 *
 * ## Зачем она
 *
 * Музыку включают в Echo и тут же открывают то, что лежит поверх плеера:
 * лабораторию — подобрать следующее, переложить файлы, поправить список; или
 * настройки — прибавить баса, завести таймер сна. И то и другое занимает
 * экран, а собственные кнопки плеера остаются под ними. Дальше начинается то,
 * ради чего плеер в телефоне вообще нужен, — «эту не хочу», «повтори», — и за
 * каждым таким движением приходилось закрывать открытое и открывать заново.
 *
 * Панель — те же три кнопки, вынесенные в низ этих двух экранов. Ничего сверх
 * них она не умеет и не должна: громкость, очередь, эквалайзер, таймер сна
 * остались на своих местах — их правят раз в вечер, а не раз в песню.
 *
 * ## Часть экрана, а не поверх него
 *
 * Прежде панель висела плавающей карточкой поверх всего приложения. Так она
 * закрывала собой нижнюю строку того, над чем висела: последнюю папку в
 * списке, последний ползунок настроек — до них приходилось доскролливать
 * вслепую. Теперь она стоит в разметке обычной строкой: экран над ней
 * становится ровно на её высоту короче, и закрывать ей нечего.
 *
 * Отсюда и вид: не карточка с полями и скруглением, а полоса во всю ширину с
 * волосяной чертой сверху — граница между тем, что делают, и тем, что играет.
 * И появляется она не выездом снизу, а раздвигая себе место
 * ([expandVertically]): то, что стало частью экрана, не может на него
 * наплывать.
 *
 * ## Почему её нет в самом плеере и в других разделах
 *
 * На экране плеера те же кнопки уже есть, и вдвое больше: панель под ними была
 * бы второй «паузой» на одном экране. В заметках, в расписании, в списках
 * человек занят не музыкой — там панель отнимала бы низ экрана и накрывала его
 * чужим, ночным цветом; управлять играющим оттуда есть чем и без неё — шторка
 * системы.
 *
 * ## Почему она не уходит на паузе
 *
 * Карточка голосовой заметки ([EchoMini]) уходит сама, когда заметка
 * кончилась, и это правило для неё верное: заметку слушают один раз. Музыку
 * ставят на паузу, чтобы вернуться, — и панель, исчезнувшая вместе с нажатием
 * «пауза», унесла бы с собой кнопку «продолжить». Уходит она вместе с самой
 * дорожкой: очередь кончилась или плеер остановили.
 *
 * ## Плеер спрашивается здесь
 *
 * Не передаётся параметрами: панель живёт только в разделе Echo и только у
 * его же плеера — тем же правилом, что и подсветка играющей строки в
 * [EchoLabCard]. Иначе состояние и четыре обработчика пришлось бы тащить через
 * карточку настроек, которой до них нет дела.
 */
@Composable
fun EchoBar(modifier: Modifier = Modifier) {
    val player = appContainer().echoPlayer
    val state by player.state.collectAsStateWithLifecycle()
    val track = state.track

    // Последняя дорожка помнится до конца ухода: `track` обнуляется в тот же
    // миг, когда плеер остановили, а полоса ещё складывается — и без памяти
    // она успела бы мигнуть пустотой. То же, что у карточки заметки.
    var kept by remember { mutableStateOf(track) }
    LaunchedEffect(track) { if (track != null) kept = track }

    var at by remember { mutableLongStateOf(0L) }
    // Место спрашивается тиком и только пока играет — тем же правилом, что у
    // плеера: поток на секунду держал бы процессор занятым и тогда, когда на
    // панель никто не смотрит.
    LaunchedEffect(track, state.playing) {
        at = player.position()
        while (track != null && state.playing) {
            at = player.position()
            delay(TICK_MS)
        }
    }

    AnimatedVisibility(
        visible = track != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val playing = track ?: kept ?: return@AnimatedVisibility

        Column(modifier = Modifier.fillMaxWidth().background(Night)) {
            // Черта сверху, а не рамка кругом: панель прижата к низу экрана
            // тремя сторонами из четырёх, и обводить их нечем.
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NightBorder))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            ) {
                CoverThumb(
                    albumId = playing.albumId,
                    uri = playing.uri,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                )

                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = playing.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NightInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Исполнитель мельче и только если он есть: у половины
                    // скачанного отдельными песнями в теге пусто, и пустая
                    // вторая строка ровняла бы панель по несуществующему.
                    if (playing.artist.isNotBlank()) {
                        Text(
                            text = playing.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = NightMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                BarButton(
                    icon = Icons.Outlined.SkipPrevious,
                    description = "Предыдущая",
                    onClick = player::previous,
                )

                // Пауза крупнее и залита: из трёх кнопок в неё попадают
                // чаще всех, и попадают не глядя.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Sunset)
                        .clickable(onClick = player::toggle),
                ) {
                    Icon(
                        imageVector = if (state.playing) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (state.playing) "Пауза" else "Играть",
                        tint = Night,
                        modifier = Modifier.size(22.dp),
                    )
                }

                BarButton(
                    icon = Icons.Outlined.SkipNext,
                    description = "Следующая",
                    onClick = { player.next() },
                )
            }

            // Полоска по самому низу панели, а не отдельной строкой: сколько
            // песни осталось, глаз читает краем, не переводя взгляда, — а
            // цифры пришлось бы читать.
            val done = if (state.durationMs > 0) {
                (at.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else {
                0f
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(NightBorder),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(done)
                        .height(2.dp)
                        .background(Sunset),
                )
            }
        }
    }
}

/** Кнопка «назад» и «вперёд»: без заливки — они реже, чем пауза. */
@Composable
private fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = NightInk,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Полсекунды: полоска в двести точек шириной чаще и не меняется. */
private const val TICK_MS = 500L
