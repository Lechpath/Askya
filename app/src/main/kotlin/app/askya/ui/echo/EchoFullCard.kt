package app.askya.ui.echo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.R
import app.askya.echo.EchoBeat
import app.askya.echo.EchoRepeat
import app.askya.echo.Track
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.delay

/**
 * Плеер во весь экран — то, что открывается нажатием на обложку.
 *
 * Обычный экран Echo делит место с кнопками разделов и шапкой: он для того,
 * чтобы выбрать музыку и управлять ею. Этот — для того, чтобы слушать: на
 * экране остаётся сама песня, а всё, чем ей управляют, опускается вниз и
 * встаёт одной строкой.
 *
 * Это по-прежнему карточка, а не «полноэкранный режим» без краёв: у неё те же
 * 28 скруглений и та же обводка, что у всех карточек Askya, и края её видно.
 * Карточка, растянутая до краёв экрана, перестала бы быть предметом, лежащим
 * поверх раздела, — а закрывается она в тот же плеер, из которого выросла.
 *
 * Позади — обложка, размытая до пятна цвета. Резкая картинка во весь экран
 * спорила бы с той же картинкой в середине; размытая она даёт не изображение,
 * а свет от него — и он у каждой песни свой.
 *
 * Обложки нет — позади заставка Echo: цветок и вордмарк, тот же знак, которым
 * раздел открывается. Пустой чёрный экран на её месте выглядел бы сломанным
 * размытием.
 *
 * Обложка здесь живая так же, как на плеере: вокруг неё те же вспышки в ритм
 * ([CoverPulse]) — на весь экран им наконец есть куда разойтись.
 */
@Composable
fun EchoFullCard(
    track: Track?,
    playing: Boolean,
    durationMs: Long,
    shuffle: Boolean,
    repeat: EchoRepeat,
    beat: EchoBeat,
    hearing: Boolean,
    position: () -> Long,
    onSeek: (Long) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    // Обложка нужна здесь только фону: сама она живёт в PlayerCover, а
    // размытие берётся из того же разобранного изображения (`loadArtwork`
    // помнит последнее).
    val blurred = rememberBlurredCover(rememberCover(track))

    // Появление тем же движением, что у всех карточек раздела: вырастает из
    // обложки, на которую нажали, а не подменяет экран.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(220),
        label = "appear",
    )
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 320f),
        label = "grow",
    )

    var progress by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(playing, durationMs, track?.uri) {
        while (true) {
            if (!dragging && durationMs > 0) {
                progress = (position().toFloat() / durationMs).coerceIn(0f, 1f)
            }
            delay(1_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(appear)
            .background(Night)
            // Тап мимо карточки закрывает её — как и у всех карточек Echo.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Backdrop(blurred = blurred)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.96f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .clip(RoundedCornerShape(28.dp))
                // Полупрозрачная, а не глухая: размытая обложка светит и
                // сквозь карточку, иначе край её отрезал бы свет ножом.
                .background(Night.copy(alpha = 0.52f))
                // Обводка светлее и толще обычной: на размытой обложке тонкая
                // тёмная черта пропадает, а карточку от фона должно отделять
                // видимое ребро — иначе это не карточка, а просто экран.
                .border(1.5.dp, NightInk.copy(alpha = 0.22f), RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            val sideways = maxWidth > maxHeight
            val side: Dp = if (sideways) {
                minOf(maxHeight * 0.62f, maxWidth * 0.36f)
            } else {
                minOf(maxWidth * 0.74f, maxHeight * 0.46f)
            }
            // Кегль ряда кнопок считается от ширины: пять управлений в одну
            // строку на узком телефоне иначе не помещаются, а переносить их
            // по одному значило бы вернуть столбик.
            val scale = (maxWidth.value / 360f).coerceIn(0.78f, 1.2f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    EchoIcon(
                        icon = Icons.Outlined.CloseFullscreen,
                        label = "Свернуть",
                        onClick = onDismiss,
                    )
                }

                Spacer(modifier = Modifier.weight(0.5f))

                PlayerCover(
                    track = track,
                    side = side,
                    beat = beat,
                    playing = playing,
                    hearing = hearing,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    // Нажатие на обложку выводит из полноэкранного тем же
                    // движением, каким в него вошли.
                    onTap = onDismiss,
                )

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = track?.title ?: "Название трека",
                        fontFamily = FontFamily.Serif,
                        fontSize = 24.sp,
                        color = if (track == null) NightMuted else NightInk,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.artist ?: "Выбери песню в разделе выше",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NightMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                EchoProgress(
                    progress = progress,
                    durationMs = durationMs,
                    enabled = track != null,
                    onScrub = {
                        dragging = true
                        progress = it
                    },
                    onSeek = {
                        progress = it
                        onSeek((it * durationMs).toLong())
                        dragging = false
                    },
                    wordHeight = if (sideways) 40.dp else 52.dp,
                )

                OneRowControls(
                    playing = playing,
                    shuffle = shuffle,
                    repeat = repeat,
                    scale = scale,
                    onToggle = onToggle,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onShuffle = onShuffle,
                    onRepeat = onRepeat,
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

/**
 * Что позади карточки: размытая обложка — или заставка Echo, если обложки нет.
 *
 * Размытие сделано уменьшением ([rememberBlurredCover]), а не `Modifier.blur`:
 * тот появился только в Android 12, и на телефонах постарше фон остался бы
 * резкой картинкой во весь экран.
 *
 * Поверх — затемнение: на светлой обложке белые буквы карточки исчезли бы, а
 * раздел перестал бы быть ночным.
 */
@Composable
private fun Backdrop(blurred: androidx.compose.ui.graphics.ImageBitmap?) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (blurred == null) {
            Box(modifier = Modifier.fillMaxSize().sunsetBackground(), contentAlignment = Alignment.Center) {
                // Заставка раздела: тот же цветок и тот же вордмарк, которыми
                // Echo открывается, — только крупно и вполсилы.
                Image(
                    painter = painterResource(R.drawable.ic_flower),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Sunset.copy(alpha = 0.16f)),
                    modifier = Modifier.fillMaxWidth(0.9f).alpha(0.9f),
                )
            }
        } else {
            Image(
                bitmap = blurred,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // Высокое качество: пятно из тридцати точек, растянутое на
                // экран, должно расплываться, а не рассыпаться квадратами.
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.fillMaxSize().background(Night.copy(alpha = 0.55f)))
        }
    }
}

/**
 * Все управления одной строкой: вперемешку, назад, играть, дальше, повтор.
 *
 * Ровно то, ради чего полноэкранный режим и нужен: на обычном плеере кнопки
 * стоят в два ряда посреди экрана, здесь — одной строкой у нижнего края, где
 * их находит большой палец, и всё остальное место отдано песне.
 *
 * Слова, а не значки, — язык раздела не меняется от того, что карточка
 * выросла. Кегль считается от ширины экрана: пять слов в строку на узком
 * телефоне иначе не помещаются.
 */
@Composable
private fun OneRowControls(
    playing: Boolean,
    shuffle: Boolean,
    repeat: EchoRepeat,
    scale: Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Word(
            text = "Shuffle",
            label = "Вперемешку",
            size = 14.sp * scale,
            color = if (shuffle) Sunset else NightMuted,
            underline = shuffle,
            onClick = onShuffle,
            modifier = Modifier.weight(1.1f),
        )
        Word(
            text = "Back",
            label = "Прошлая",
            size = 19.sp * scale,
            color = NightInk,
            onClick = onPrevious,
            modifier = Modifier.weight(1f),
        )
        Word(
            text = if (playing) "Pause" else "Play",
            label = if (playing) "Пауза" else "Играть",
            size = 30.sp * scale,
            color = Sunset,
            onClick = onToggle,
            modifier = Modifier.weight(1.3f),
        )
        Word(
            text = "Next",
            label = "Следующая",
            size = 19.sp * scale,
            color = NightInk,
            onClick = onNext,
            modifier = Modifier.weight(1f),
        )
        Word(
            // В строке из пяти слов «Repeat All» не помещается: режим назван
            // одним словом, а какой он — видно по цвету и черте.
            text = repeat.shortCaption(),
            label = repeat.next().spokenNext(),
            size = 14.sp * scale,
            color = if (repeat == EchoRepeat.OFF) NightMuted else Sunset,
            underline = repeat != EchoRepeat.OFF,
            onClick = onRepeat,
            modifier = Modifier.weight(1.1f),
        )
    }
}

/** Слово-кнопка нижней строки: своя доля ширины, нажимается целиком. */
@Composable
private fun Word(
    text: String,
    label: String,
    size: androidx.compose.ui.unit.TextUnit,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    underline: Boolean = false,
) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = size,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        textDecoration = if (underline) TextDecoration.Underline else null,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, onClickLabel = label)
            .padding(vertical = 10.dp),
    )
}

/** Повтор одним словом — для строки, где пяти длинным словам не разойтись. */
private fun EchoRepeat.shortCaption(): String = when (this) {
    EchoRepeat.OFF -> "Repeat"
    EchoRepeat.QUEUE -> "All"
    EchoRepeat.TRACK -> "One"
}

/** Голосу называется то, что случится по нажатию, а не то, что стоит сейчас. */
private fun EchoRepeat.spokenNext(): String = when (this) {
    EchoRepeat.OFF -> "Без повтора"
    EchoRepeat.QUEUE -> "Повторять список"
    EchoRepeat.TRACK -> "Повторять дорожку"
}
