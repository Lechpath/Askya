package app.askya.ui.echo

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.askya.echo.EchoBeat
import app.askya.echo.coverColors
import app.askya.ui.theme.Sunset
import app.askya.ui.theme.SunsetDeep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * Цвета обложки для вспышек — считаются один раз на обложку.
 *
 * В стороне от главного потока: подсчёт быстрый, но он выпадает ровно на тот
 * миг, когда обложка появилась и её показывают, — а это самый неудачный момент
 * что-либо считать на кадре.
 *
 * Пока обложки нет (или она чёрно-белая настолько, что цвета в ней не нашлось),
 * вспышки идут закатом — тем же, которым горит весь раздел.
 */
@Composable
fun rememberPulseColors(cover: ImageBitmap?): List<Color> {
    var colors by remember(cover) { mutableStateOf(SUNSET_COLORS) }

    LaunchedEffect(cover) {
        if (cover == null) {
            colors = SUNSET_COLORS
            return@LaunchedEffect
        }
        val found = withContext(Dispatchers.Default) {
            runCatching { coverColors(cover.asAndroidBitmap()) }.getOrDefault(emptyList())
        }
        colors = if (found.isEmpty()) SUNSET_COLORS else found.map { Color(it) }
    }

    return colors
}

/**
 * Вспышки вокруг обложки — в ритм музыки.
 *
 * Свет идёт из-под обложки наружу: сама она остаётся картинкой, которую
 * разглядывают, а живёт вокруг неё воздух. Обратное — рябь поверх обложки —
 * означало бы, что плеер мешает смотреть на то, что сам же показывает.
 *
 * Три слоя, и каждый отвечает своей части звука:
 *
 * — свечение, дышащее общей громкостью: оно не мигает, а наливается и опадает,
 *   и по нему видно, громко сейчас или тихо;
 * — лепестки по кругу, каждый на своей полосе: бас качает нижние, верхи —
 *   верхние, и обложка оказывается в венце, который переливается вместе с
 *   песней, а не пульсирует целиком;
 * — круги, выходящие из-под обложки на каждом ударе: это и есть «в ритм» —
 *   бочка выталкивает кольцо, оно расходится и гаснет.
 *
 * Цвета — самой обложки ([rememberPulseColors]): вспышка должна выглядеть
 * светом, который эта картинка отбрасывает, а не подсветкой из чужого
 * плеера.
 *
 * Без разрешения на микрофон спектр телефон не отдаёт (см.
 * [app.askya.echo.EchoPulse]) — тогда [hearing] выключен, и обложка дышит
 * ровно и медленно: не в ритм, но и не мёртво. Тишина и пауза гасят всё
 * плавно, а не обрывают.
 *
 * Рисуется кадрами вручную, а не набором `animate*AsState`: значений пять,
 * они меняются двадцать раз в секунду, и каждое своим спрингом дало бы пять
 * анимаций, спорящих друг с другом. Здесь один цикл кадров, и всё сглаживание
 * в нём — быстрый подъём, медленный спад, как у самого слуха.
 */
@Composable
fun CoverPulse(
    beat: EchoBeat,
    colors: List<Color>,
    playing: Boolean,
    hearing: Boolean,
    modifier: Modifier = Modifier,
) {
    val visual = remember { PulseVisual() }
    val current by rememberUpdatedState(beat)
    val alive by rememberUpdatedState(playing)
    val real by rememberUpdatedState(hearing)

    LaunchedEffect(visual) {
        var previous = 0L
        var seenHit = 0
        var clock = 0f

        while (true) {
            withFrameNanos { now ->
                // Первый кадр не с чем сравнивать: шаг берётся обычный, иначе
                // на нём всё скакнуло бы сразу в цель.
                val step = if (previous == 0L) FRAME else {
                    ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.1f)
                }
                previous = now
                clock += step

                val heard = current
                val target = when {
                    !alive -> Bands(0f, 0f, 0f, 0f)
                    real -> Bands(heard.bass, heard.mid, heard.high, heard.level)
                    // Без спектра — ровное дыхание: три волны разной длины,
                    // чтобы венец переливался, а не мигал целиком.
                    else -> Bands(
                        bass = 0.30f + 0.22f * sin(clock * 2.1f),
                        mid = 0.26f + 0.18f * sin(clock * 1.4f + 1.2f),
                        high = 0.22f + 0.16f * sin(clock * 2.8f + 2.4f),
                        level = 0.30f + 0.16f * sin(clock * 1.1f),
                    )
                }

                visual.bass = ease(visual.bass, target.bass, step)
                visual.mid = ease(visual.mid, target.mid, step)
                visual.high = ease(visual.high, target.high, step)
                visual.level = ease(visual.level, target.level, step)
                // Венец медленно поворачивается: неподвижный, он читался бы
                // рисунком вокруг обложки, а не светом.
                visual.turn += step * TURN_SPEED

                if (alive && real && heard.hit != seenHit) {
                    seenHit = heard.hit
                    visual.ring(colors, heard.bass)
                }
                visual.advance(step)
            }
        }
    }

    Canvas(modifier = modifier) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val middle = Offset(size.width / 2, size.height / 2)
        val palette = colors.ifEmpty { SUNSET_COLORS }

        glow(middle, side, palette.first(), visual.level)
        petals(middle, side, palette, visual)
        rings(middle, side, visual)
    }
}

/** Свечение под обложкой: чем громче, тем шире и ярче. */
private fun DrawScope.glow(middle: Offset, side: Float, color: Color, level: Float) {
    if (level <= 0.01f) return
    val radius = side * (0.52f + 0.42f * level)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.42f * level),
                color.copy(alpha = 0.16f * level),
                Color.Transparent,
            ),
            center = middle,
            radius = radius,
        ),
        radius = radius,
        center = middle,
    )
}

/**
 * Венец из пятен по кругу. Пятен всегда шесть, а цветов у обложки бывает и
 * один: они идут по кругу и повторяются — так венец остаётся венцом даже у
 * одноцветной картинки.
 */
private fun DrawScope.petals(middle: Offset, side: Float, palette: List<Color>, visual: PulseVisual) {
    val bands = floatArrayOf(
        visual.bass,
        visual.mid,
        visual.high,
        visual.bass,
        visual.mid,
        visual.high,
    )

    repeat(PETALS) { index ->
        val strength = bands[index]
        if (strength <= 0.02f) return@repeat

        val angle = visual.turn + index * (2 * Math.PI.toFloat() / PETALS)
        // Пятно выходит из-под края обложки и отъезжает наружу тем дальше, чем
        // сильнее его полоса.
        val distance = side * (0.40f + 0.16f * strength)
        val center = Offset(
            middle.x + cos(angle) * distance,
            middle.y + sin(angle) * distance,
        )
        val radius = side * (0.14f + 0.20f * strength)
        val color = palette[index % palette.size]

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.55f * strength),
                    color.copy(alpha = 0.18f * strength),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

/** Круги, вытолкнутые ударом: расходятся от обложки и гаснут. */
private fun DrawScope.rings(middle: Offset, side: Float, visual: PulseVisual) {
    visual.rings.forEach { ring ->
        val grown = side * (0.5f + 0.5f * ring.progress)
        val fade = (1f - ring.progress) * ring.strength
        if (fade <= 0.01f) return@forEach
        drawCircle(
            color = ring.color.copy(alpha = 0.5f * fade),
            radius = grown,
            center = middle,
            style = Stroke(width = (side * 0.035f) * (1f - ring.progress * 0.7f)),
        )
    }
}

/** Полосы одним значением — чтобы цель кадра считалась в одном месте. */
private data class Bands(val bass: Float, val mid: Float, val high: Float, val level: Float)

/** Круг от удара: докуда дошёл, каким цветом и насколько сильным был удар. */
private class Ring(val color: Color, val strength: Float) {
    var progress: Float = 0f
}

/**
 * Что нарисовано сейчас. Отдельный держатель, а не пять `remember`: значения
 * читает только `Canvas`, и запись в них должна перерисовывать картинку, не
 * пересобирая экран.
 */
private class PulseVisual {
    var bass by mutableFloatStateOf(0f)
    var mid by mutableFloatStateOf(0f)
    var high by mutableFloatStateOf(0f)
    var level by mutableFloatStateOf(0f)
    var turn by mutableFloatStateOf(0f)

    /** Круги от последних ударов. Список короткий: старые уходят сами. */
    val rings = mutableStateListOf<Ring>()

    fun ring(colors: List<Color>, strength: Float) {
        // Больше горстки колец на экране не разобрать, а слабый удар кольца не
        // заслуживает: иначе на плотной музыке экран заливает сплошным светом.
        if (rings.size >= MAX_RINGS || strength < 0.15f) return
        val palette = colors.ifEmpty { SUNSET_COLORS }
        rings += Ring(color = palette[rings.size % palette.size], strength = strength)
    }

    fun advance(step: Float) {
        if (rings.isEmpty()) return
        rings.forEach { it.progress += step * RING_SPEED }
        rings.removeAll { it.progress >= 1f }
    }
}

/**
 * Сглаживание: вверх быстро, вниз медленно.
 *
 * Так слышит ухо и так выглядит живым свет: удар приходит мгновенно, а гаснет
 * послесвечением. Одинаковая скорость в обе стороны давала бы дребезг на
 * каждой шестнадцатой.
 */
private fun ease(current: Float, target: Float, step: Float): Float {
    val speed = if (target > current) RISE else FALL
    val moved = current + (target - current) * (step * speed).coerceIn(0f, 1f)
    return if (moved < 0.001f) 0f else moved
}

/** Пятен в венце. */
private const val PETALS = 6

/** Колец разом. */
private const val MAX_RINGS = 5

/** Долей секунды на полный проход кольца наружу. */
private const val RING_SPEED = 1.4f

/** Скорость набора и спада — в долях расстояния за секунду. */
private const val RISE = 18f
private const val FALL = 5f

/** Оборот венца — примерно круг за двадцать секунд. */
private const val TURN_SPEED = 0.3f

/** Шаг первого кадра, пока сравнивать не с чем. */
private const val FRAME = 1f / 60f

/** Чем вспыхивает обложка, у которой своего цвета не нашлось. */
private val SUNSET_COLORS = listOf(Sunset, SunsetDeep, Color(0xFFF2C14E))
