package app.askya.ui.echo

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import app.askya.ui.components.ASKYA_SHARE
import app.askya.ui.components.FLOWER_SHARE
import app.askya.ui.components.FlowerWord
import app.askya.ui.components.breathingScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Занавес AskyaEcho: цветок, которым раздел открывается и закрывается.
 *
 * Плеер — единственный раздел, в который входят не мельком: сюда приходят
 * слушать, а не посмотреть и уйти. Поэтому вход обставлен: цветок Askya
 * раскручивается посреди чёрного листа, дышит, останавливается ровно — и
 * надпись на лепестке, та же, что на иконке приложения, дописывается до
 * «AskyaEcho». Название раздела не написано в шапке заранее, а собирается на
 * глазах из имени приложения: Echo — это Askya, ушедшая в темноту.
 *
 * Оборот ровно на два круга, а не на 72 градуса, как у цветка ожидания
 * (`BreathingFlower`): там цветок безымянный и любой шаг между лепестками
 * возвращает рисунок в себя, здесь на одном лепестке слово, и горизонтальным
 * оно бывает только через полный круг. Поэтому цветок ожидания сюда и не
 * подошёл — ему нечем остановиться в нужном месте и нечего дописывать; само
 * слово на лепестке рисует общий [app.askya.ui.components.FlowerWord], тот же,
 * которым заставка приложения пишет «Askya».
 *
 * Занавес непрозрачный и повторяет фон раздела, поэтому под ним ничего не
 * видно: плеер живёт своей жизнью, а не мигает сквозь анимацию. Вход
 * пропускается касанием — тому, кто пришёл дослушать песню, церемония нужна
 * не каждый раз.
 */
@Composable
fun EchoOpening(onDone: () -> Unit) {
    val spin = remember { Animatable(0f) }
    val bloom = remember { Animatable(0.5f) }
    val written = remember { Animatable(0f) }
    val veil = remember { Animatable(1f) }
    var skip by remember { mutableStateOf(false) }

    // Пропуск — это перезапуск сценария с другой ветки: касание отменяет
    // текущий шаг вместе со всем, что за ним стояло, и досказывать анимацию
    // из двух мест не приходится.
    LaunchedEffect(skip) {
        if (skip) {
            launch { written.animateTo(1f, tween(160)) }
            veil.animateTo(0f, tween(220))
            onDone()
            return@LaunchedEffect
        }

        launch { bloom.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        spin.animateTo(TURN, tween(1_700, easing = Settle))
        written.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        // Пауза на прочтение: слово дописано, и его дают увидеть целиком.
        delay(320)
        launch { bloom.animateTo(1.22f, tween(460, easing = FastOutSlowInEasing)) }
        veil.animateTo(0f, tween(460))
        onDone()
    }

    Curtain(veil = veil.value, onTap = { skip = true }) { side ->
        FlowerWord(
            side = side,
            // Слово уже написано до имени приложения — заставкой, с которой
            // Askya открылась. Здесь дописывается только название раздела.
            shown = ASKYA_SHARE + (1f - ASKYA_SHARE) * written.value,
            spin = spin.value,
            modifier = Modifier.scale(bloom.value * breathingScale()),
            contentDescription = "AskyaEcho",
        )
    }
}

/**
 * Уход из раздела: цветок в шапке сворачивает Echo обратно в Askya.
 *
 * Обратный ход входа: ночь наливается поверх плеера, «Echo» стирается с
 * лепестка, цветок откручивается назад и уходит в точку. Слово стирается до
 * имени приложения — на нём Echo и заканчивается, оставляя Askya.
 *
 * Уход происходит не в конце, а на самом схлопывании ([onLeave]) — иначе
 * между исчезнувшим разделом и появившимся днём зияет пустая секунда.
 *
 * После этого занавес не снимается сразу: сначала он держит чёрное, пока день
 * не встал на место, и лишь потом тает. Иначе плеер, который никуда не делся,
 * успевает моргнуть там, откуда его только что убрали.
 */
@Composable
fun EchoClosing(onLeave: () -> Unit, onDone: () -> Unit) {
    val spin = remember { Animatable(0f) }
    val bloom = remember { Animatable(1f) }
    val written = remember { Animatable(1f) }
    val veil = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        veil.animateTo(1f, tween(280))
        written.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        launch { bloom.animateTo(0.1f, tween(540, easing = FastOutSlowInEasing)) }
        spin.animateTo(-TURN / 2, tween(540, easing = FastOutSlowInEasing))
        onLeave()
        delay(240)
        veil.animateTo(0f, tween(420))
        onDone()
    }

    Curtain(veil = veil.value, onTap = {}) { side ->
        FlowerWord(
            side = side,
            shown = ASKYA_SHARE + (1f - ASKYA_SHARE) * written.value,
            spin = spin.value,
            modifier = Modifier.scale(bloom.value * breathingScale()),
            contentDescription = "AskyaEcho",
        )
    }
}

/**
 * Чёрный лист занавеса во весь экран с цветком посередине.
 *
 * Фон тот же, что у раздела, — занавес не «наезжает» на плеер чужим цветом, а
 * подменяет его собой. Касание отдаётся наружу целиком: во время церемонии под
 * ней нечего нажимать, и любой промах по невидимой кнопке был бы неожиданностью.
 */
@Composable
private fun Curtain(
    veil: Float,
    onTap: () -> Unit,
    flower: @Composable (Dp) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .alpha(veil)
            .sunsetBackground()
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        flower(maxWidth * FLOWER_SHARE)
    }
}

/** Полный оборот слова: лепестков пять, но надпись на одном, и горизонталь — только круг. */
private const val TURN = 720f

/**
 * Замедление вращения: быстрый старт и долгая остановка.
 *
 * Цветок не тормозит равномерно — он проворачивается по инерции и замирает,
 * поэтому кривая почти всё время выбирает в начале.
 */
private val Settle = CubicBezierEasing(0.12f, 0.72f, 0.16f, 1f)
