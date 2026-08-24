package app.askya.ui.echo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.askya.R
import app.askya.echo.EchoBeat
import app.askya.echo.Track
import app.askya.echo.loadArtwork
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Обложка дорожки.
 *
 * Сначала спрашивается у MediaStore (`albumart`) — там она уже разобрана и
 * лежит готовой картинкой. Не нашлось — читается из самого файла
 * `MediaMetadataRetriever`: у скачанных отдельными песнями обложка часто вшита
 * в тег, а альбома у них нет вовсе.
 *
 * `null` значит и «ещё ищу», и «обложки нет»: показывается в обоих случаях
 * одно и то же — вордмарк Askya, и различать эти состояния экрану незачем.
 */
@Composable
fun rememberCover(track: Track?): ImageBitmap? =
    rememberCover(albumId = track?.albumId ?: 0, uri = track?.uri)

/**
 * Та же обложка, но по альбому и файлу, а не по дорожке.
 *
 * Нужна карточке плейлиста: у плейлиста своей обложки нет, и он показывает
 * ту, что у первой сложенной в него песни, — целой дорожки для этого не надо.
 */
@Composable
fun rememberCover(albumId: Long, uri: String?): ImageBitmap? {
    val context = LocalContext.current
    var cover by remember(uri, albumId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(uri, albumId) {
        cover = loadArtwork(context, albumId, uri)?.asImageBitmap()
    }

    return cover
}

/**
 * Что стоит на месте обложки, пока её нет.
 *
 * Вордмарк, а не серый квадрат с нотой: пустое место в середине экрана всё
 * равно на что-то смотрит, и пусть это будет знак приложения.
 */
@Composable
fun CoverPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_wordmark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Sunset),
            modifier = Modifier.size(width = 150.dp, height = 68.dp),
        )
        Text(
            text = "Echo",
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            letterSpacing = 1.sp,
            color = NightMuted,
        )
        Image(
            painter = painterResource(R.drawable.ic_flower),
            contentDescription = null,
            colorFilter = ColorFilter.tint(NightMuted),
            modifier = Modifier.padding(top = 12.dp).size(26.dp),
        )
    }
}

/** Обложка внутри квадрата: картинка, а если её нет — вордмарк. */
@Composable
fun CoverImage(track: Track?, modifier: Modifier = Modifier) {
    val cover = rememberCover(track)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (cover == null) {
            CoverPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            Image(
                bitmap = cover,
                contentDescription = "Обложка",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Обложка размером с ноготь — для строк списка и карточек плейлистов.
 *
 * Пока обложки нет (или её нет вовсе), на её месте стоит цветок Askya: тот же
 * знак, что на большой обложке плеера, только мелко. Серый квадрат с нотой
 * выглядел бы заглушкой из чужого приложения.
 */
@Composable
fun CoverThumb(albumId: Long, uri: String?, modifier: Modifier = Modifier) {
    val cover = rememberCover(albumId, uri)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NightPanelSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (cover == null) {
            Image(
                painter = painterResource(R.drawable.ic_flower),
                contentDescription = null,
                colorFilter = ColorFilter.tint(Sunset.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxSize(0.5f),
            )
        } else {
            Image(
                bitmap = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Обложка плеера: то, на что смотрят, пока играет песня.
 *
 * Живёт отдельно от экрана, потому что она у Echo одна на два места — на
 * плеере и в полноэкранной карточке ([EchoFullCard]). Разойдись эти две
 * обложки хоть жестом, хоть скруглением — и вход в полный экран перестал бы
 * читаться как увеличение той же вещи.
 *
 * Смахивается вбок: влево — следующая песня, вправо — прошлая. Так листают
 * всё, что показано по одному: снимок в галерее, страницу, песню. Кнопки
 * `Back` и `Next` остаются на месте — смахивание не заменяет их, а добавляет
 * то же движение к самой большой вещи на экране: до середины экрана палец
 * достаёт из любого положения руки, а до слова внизу — не всегда.
 *
 * Обложка идёт за пальцем вполсилы и возвращается пружиной: движение отвечает,
 * но картинка не уезжает с экрана — уходит из-под пальца песня, а не она.
 *
 * Порог в 56 точек, а не любой сдвиг: палец, лежащий на обложке, дрожит, и
 * менять от этого песню нельзя.
 *
 * Нажатие ([onTap]) уводит в полноэкранную карточку и возвращает обратно:
 * увеличить картинку нажатием на неё же — единственное, чего от неё ждут.
 *
 * Вокруг неё — вспышки в ритм ([CoverPulse]); они рисуются под обложкой и
 * выходят наружу за её края, поэтому места им отведено больше, чем сама
 * обложка занимает.
 */
@Composable
fun PlayerCover(
    track: Track?,
    side: Dp,
    beat: EchoBeat,
    playing: Boolean,
    hearing: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Обложка читается здесь одна на всех: и картинкой, и цветами вспышек.
    // Дважды её не разбирают — то же изображение отдаёт кэш `loadArtwork`.
    val cover = rememberCover(track)
    val colors = rememberPulseColors(cover)
    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val enough = with(LocalDensity.current) { 56.dp.toPx() }

    Box(modifier = modifier.size(side), contentAlignment = Alignment.Center) {
        CoverPulse(
            beat = beat,
            colors = colors,
            playing = playing,
            hearing = hearing,
            // Свет едет вместе с обложкой: отставший от неё венец выглядел бы
            // дыркой в экране, из которой обложку вынули.
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shift.value.roundToInt(), 0) },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shift.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(28.dp))
                .background(NightPanel)
                .border(1.dp, NightBorder, RoundedCornerShape(28.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            scope.launch { shift.snapTo(shift.value + delta * 0.5f) }
                        },
                        onDragEnd = {
                            val moved = shift.value
                            scope.launch {
                                shift.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                                )
                            }
                            when {
                                moved <= -enough -> onNext()
                                moved >= enough -> onPrevious()
                            }
                        },
                        onDragCancel = { scope.launch { shift.animateTo(0f) } },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { onTap() }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (cover == null) {
                CoverPlaceholder(modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    bitmap = cover,
                    contentDescription = "Обложка",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * Обложка, размытая до пятна цвета, — фон полноэкранной карточки.
 *
 * Размывается уменьшением, а не `Modifier.blur`: тот появился в Android 12, а
 * Askya живёт с восьмого, и на телефонах постарше фон остался бы резкой
 * картинкой во весь экран. Обложка, сжатая до трёх десятков точек и
 * растянутая обратно сглаживанием, даёт ровно то, что нужно, — свет от
 * картинки вместо самой картинки, и стоит это доли миллисекунды.
 *
 * Считается в стороне от главного потока и заново только при смене обложки:
 * фон не должен пересчитываться от того, что кто-то нажал «дальше».
 */
@Composable
fun rememberBlurredCover(cover: ImageBitmap?): ImageBitmap? {
    var blurred by remember(cover) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(cover) {
        if (cover == null) {
            blurred = null
            return@LaunchedEffect
        }
        blurred = withContext(Dispatchers.IO) {
            runCatching {
                android.graphics.Bitmap
                    .createScaledBitmap(cover.asAndroidBitmap(), BLUR_SIDE, BLUR_SIDE, true)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    return blurred
}

/** До скольких точек сжимается обложка, чтобы стать размытием. */
private const val BLUR_SIDE = 28
