package app.askya.ui.echo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.echo.Aside
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Ink
import kotlinx.coroutines.delay

/**
 * Карточка голосовой заметки, играющей поверх экрана.
 *
 * ## Почему она, а не переход в раздел
 *
 * Заметку слушают посреди дела: стоя в списке, в записи, в расписании. Уводить
 * ради двадцати секунд в AskyaEcho — с занавесом, цветком и очередью — значит
 * прервать дело ради одной фразы и потом искать дорогу обратно. Поэтому раздел
 * не открывается вовсе: над экраном на время заметки висит карточка в две
 * строки, и всё.
 *
 * **Уходит она сама.** Кончилась заметка — карточки нет. Кнопка «закрыть» на
 * ней есть, но она для тех, кто передумал слушать; то, что кончилось, не
 * должно требовать нажатия. Это и отличает её от плеера в шторке: плеер висит,
 * пока его не выключат, а здесь висеть после конца нечему.
 *
 * ## Почему посередине
 *
 * Карточка стоит по центру экрана, ровно между краями. Внизу её было плохо
 * видно: там же кнопка «завести новое», там же полоска «Вернуть», там же
 * панель системы, — и три вещи, поделившие один угол, читаются как одна
 * сломанная. Посередине она ничего не отнимает и попадается на глаза сразу:
 * туда и смотрят, когда нажали на заметку.
 *
 * Появляется она теперь не снизу, а из себя самой: выезжать с края к середине
 * дольше, чем заметку слушают.
 *
 * ## Тёмная
 *
 * Чёрное с коралловым — тем же, чем набрана кнопка «завести новое». Это язык
 * приложения для «сейчас происходит вот что», и заметке он подходит ровно так
 * же. Заодно она этим и связана с
 * AskyaEcho: раздел звука в Askya тёмный при любой теме.
 */
@Composable
fun EchoMini(
    aside: Aside?,
    position: () -> Long,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Последнее, что играло, помнится до конца ухода: `aside` обнуляется в тот
    // же миг, когда заметка кончилась, а карточка ещё съезжает вниз — и без
    // памяти она успела бы мигнуть пустотой.
    var kept by remember { mutableStateOf<Aside?>(null) }
    LaunchedEffect(aside) { if (aside != null) kept = aside }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = aside != null,
            enter = scaleIn(initialScale = 0.92f) + fadeIn(),
            exit = scaleOut(targetScale = 0.92f) + fadeOut(),
        ) {
            val shown = aside ?: kept ?: return@AnimatedVisibility

            var at by remember(shown.noteId) { mutableLongStateOf(0L) }

            // Место спрашивается по тику, а не приходит потоком, — тем же
            // правилом, что у плеера: секундный тик держал бы процессор
            // занятым и тогда, когда на полоску никто не смотрит. Тика нет
            // вовсе, пока заметка на паузе.
            LaunchedEffect(shown.noteId, shown.playing) {
                while (shown.playing) {
                    at = position()
                    delay(TICK_MS)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Столько же слева, сколько справа: карточка стоит посередине,
                    // и сдвинутая на волос читалась бы как промах разметки.
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Ink)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (shown.playing) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (shown.playing) "Пауза" else "Слушать",
                        tint = Accent,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(
                        text = shown.title.ifBlank { "Голос" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Полоска, а не цифры: сколько осталось, глаз читает по
                    // длине быстрее, чем вычитает «0:31 из 0:48».
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Accent.copy(alpha = 0.30f)),
                    ) {
                        val done = if (shown.durationMs > 0) {
                            (at.toFloat() / shown.durationMs).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(done)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Accent),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Закрыть",
                        tint = Accent.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private const val TICK_MS = 200L
