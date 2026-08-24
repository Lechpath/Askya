package app.askya.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.askya.R

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
 * Живёт в общих компонентах, а не в интервью: тем же цветком отмечается
 * сборка дня в шапке AskyaDay — там [size] меньше, всё остальное то же.
 */
@Composable
fun BreathingFlower(modifier: Modifier = Modifier, size: Dp = 72.dp) {
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
        targetValue = 72f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "turn",
    )
    // На вдохе цветок чуть ярче — иначе дыхание видно только по размеру.
    val glow by breath.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS / 2, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Image(
        painter = painterResource(R.drawable.ic_flower),
        contentDescription = "Askya думает",
        modifier = modifier
            .size(size)
            .scale(scale)
            .rotate(turn)
            .alpha(glow),
    )
}

/** Полный вдох-выдох. Медленнее человеческого — торопить тут нечего. */
private const val BREATH_MS = 3_200
