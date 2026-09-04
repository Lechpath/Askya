package app.askya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Цветок Askya, пока она думает: дышит и проворачивается в такт дыханию.
 *
 * Оборот ровно на 72 градуса за полный вдох-выдох. Лепестков пять и они
 * одинаковые, поэтому такой шаг возвращает рисунок в себя — движение читается
 * как непрерывное вращение, хотя каждый цикл начинается с чистого листа.
 *
 * Полминуты ожидания ответа модели — это долго, и крутящийся спиннер тут
 * читался бы как «приложение занято». Дыхание читается как «думает».
 *
 * Живёт в общих компонентах, а не в одном экране: дышать так может любое
 * ожидание. Знаку, нарисованному обводкой, дыхание отдаётся [BreathingIcon] —
 * там нет поворота, потому что поворачивать в себя нечего.
 */
@Composable
fun BreathingFlower(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val breath = rememberBreath(turnDegrees = 72f)

    AskyaFlower(
        contentDescription = "Askya думает",
        modifier = modifier
            .size(size)
            .scale(breath.scale)
            .rotate(breath.turn)
            .alpha(breath.glow),
    )
}

/**
 * То же дыхание у знака, нарисованного обводкой и тонируемого темой.
 *
 * Так ждёт сборка дня в шапке AskyaDay: у кнопки свой знак, и подменять его на
 * время ожидания чужим значило бы моргать в шапке картинкой. Дышит то, на что
 * нажали.
 *
 * Без поворота: у пяти одинаковых лепестков оборот на 72 градуса возвращает
 * рисунок в себя, а у восходящего солнца возвращать нечего — повёрнутое, оно
 * просто ляжет набок.
 */
@Composable
fun BreathingIcon(
    painter: Painter,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val breath = rememberBreath(turnDegrees = 0f)

    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(size)
            .scale(breath.scale)
            .alpha(breath.glow),
    )
}

/** Вдох-выдох одним значением: размер, яркость и поворот на текущий миг. */
private data class Breath(val scale: Float, val glow: Float, val turn: Float)

@Composable
private fun rememberBreath(turnDegrees: Float): Breath {
    val breath = rememberInfiniteTransition(label = "breath")

    // Вдох и выдох — половины цикла, поэтому длительность вдвое короче оборота.
    val scale by breath.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val turn by breath.animateFloat(
        initialValue = 0f,
        targetValue = turnDegrees,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "turn",
    )
    // На вдохе знак чуть ярче — иначе дыхание видно только по размеру.
    val glow by breath.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    return Breath(scale = scale, glow = glow, turn = turn)
}

/** Полный вдох-выдох. Медленнее человеческого — торопить тут нечего. */
private const val BREATH_MS = 3_200
