package app.askya.ui.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.app.appContainer
import app.askya.data.preferences.SplashWhen
import app.askya.domain.model.Greeting
import app.askya.ui.components.ASKYA_SHARE
import app.askya.ui.components.FLOWER_RADIUS
import app.askya.ui.components.FLOWER_SHARE
import app.askya.ui.components.FlowerWord
import app.askya.ui.components.breathingScale
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.Cream
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Заставка приложения: цветок Askya, на лепестке которого пишется имя.
 *
 * Порядок один и тот же и читается как одно движение: дышащий цветок, вдоль
 * лепестка пером дописывается «Askya», и уже под готовым именем появляется
 * приветствие — по времени суток, по правилу [Greeting].
 *
 * Цветок здесь не крутится, в отличие от занавеса AskyaEcho: там оборот нужен,
 * чтобы имя приложения доросло до названия раздела, а тут дописывать нечего —
 * приложение уже названо, и вращение читалось бы как ожидание, будто что-то
 * грузится. Дыхание остаётся: оно и есть лицо Askya.
 *
 * **Заставка ничего не начинает — она продолжает.** До неё цветок держала
 * системная заставка Android (`values-v31/themes.xml`), и он там уже дышал.
 * Поэтому здесь нет ни расцветания из точки, ни сдвига: цветок стоит той же
 * стороны и ровно в середине экрана, и подхватывается прямо на ходу. Любое
 * «появление» на этом месте читалось бы стыком двух заставок, а не одним
 * цветком, который всё это время был на экране.
 *
 * Лист кремовый, как всё приложение, поэтому заставка не «мигает» другим
 * цветом перед первым экраном — она в него растворяется. Приложение собирается
 * под ней, начиная с [onGreeted]: к концу приветствия открывать нечего, всё
 * готово.
 *
 * Касание пропускает церемонию: открывший приложение в третий раз за вечер
 * пришёл к делу, а не смотреть, как пишется имя.
 */
@Composable
fun AskyaSplash(onGreeted: () -> Unit, onDone: () -> Unit) {
    val written = remember { Animatable(0f) }
    val hello = remember { Animatable(0f) }
    val veil = remember { Animatable(1f) }
    var skip by remember { mutableStateOf(false) }

    // Время спрашивается один раз: заставка живёт секунды, и пересчитывать
    // приветствие на каждой перерисовке незачем.
    //
    // Сперва берётся первая фраза поры — само приветствие: набор перебирается
    // по счётчику из DataStore, а тот читается с диска, и пока он не пришёл,
    // на экране должно стоять что-то верное, а не пустое место. Подмена
    // успевает случиться задолго до того, как приветствие проступит: письмо
    // имени идёт почти секунду, а чтение счётчика — миллисекунды.
    val settings = appContainer().settings
    var greeting by remember { mutableStateOf(Greeting.now()) }
    LaunchedEffect(Unit) { greeting = Greeting.now(settings.advanceGreeting()) }

    // Краска цветка на заставке — своя, если её выбрали, и общая, если нет.
    //
    // Заставка единственное место, где цветок стоит один и во весь экран: там
    // он не знак раздела, а картинка, и выбирать её порознь осмысленно ровно
    // поэтому. Системная заставка Android 12+ красится той же настройкой, но
    // не отсюда — темой, и со следующего запуска (`MainActivity`).
    val chosen by settings.settings.collectAsStateWithLifecycle(
        initialValue = settings.state.value,
    )
    val flower = (chosen.splashFlower ?: chosen.flower).color

    // Заставка пропускается тем же путём, что и касанием, — насовсем её не
    // убирает ни одно состояние: начинать запуск пустым кремовым листом хуже,
    // чем секунда цветка.
    //
    // «Раз в день» спрашивает у диска, был ли уже сегодня заход, и потому
    // решается не сразу. Пока диск молчит, церемония идёт: полсекунды письма
    // имени человек всё равно увидит, а пропуск догонит её раньше, чем
    // проступит приветствие. Обратный порядок — сперва пусто, потом «а, надо
    // было показать» — читался бы как сбой.
    LaunchedEffect(Unit) {
        skip = when (settings.state.value.splash) {
            SplashWhen.NEVER -> true
            SplashWhen.ALWAYS -> false
            SplashWhen.DAILY -> !settings.splashDueToday()
        }
    }

    // Пропуск — это перезапуск сценария с другой ветки: касание отменяет
    // текущий шаг вместе со всем, что за ним стояло, и досказывать анимацию
    // из двух мест не приходится.
    LaunchedEffect(skip) {
        if (skip) {
            // Церемонию пропустили — приложение нужно немедленно, и собирается
            // оно уже под уходящим занавесом.
            onGreeted()
            launch { written.animateTo(1f, tween(140)) }
            launch { hello.animateTo(1f, tween(140)) }
            veil.animateTo(0f, tween(200))
            onDone()
            return@LaunchedEffect
        }

        written.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        // Пауза на прочтение: имя дописано, и его дают увидеть целиком.
        delay(220)
        hello.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
        // Приветствие сказано — за ним собирается приложение ([onGreeted]).
        // Письмо к этому времени закончено, и на экране остаётся одно дыхание:
        // сборка, если и отнимет кадр-другой, заметна тут меньше всего.
        onGreeted()
        // Полторы секунды на приветствие: оно короткое, но читают его не
        // глазами, а как обращение — и уходить сразу после него значит не дать
        // ему прозвучать.
        delay(1_500)
        veil.animateTo(0f, tween(400))
        onDone()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .alpha(veil.value)
            .background(Cream)
            .pointerInput(Unit) { detectTapGestures { skip = true } },
        contentAlignment = Alignment.Center,
    ) {
        // Сторона цветка — та же, какой его держала системная заставка, а не
        // доля экрана: у системы значок заставки одного размера в dp на любом
        // телефоне, и подхватывать его надо тем же размером. Доля ширины
        // остаётся ограничением сверху — на узком экране цветок не должен
        // упираться в края.
        val side = minOf(SYSTEM_ICON, maxWidth * FLOWER_SHARE)

        // Цветок стоит ровно в середине экрана — там же, где его держала
        // системная заставка. Столбиком с подписью его сдвигало вверх на
        // половину её высоты, и на стыке двух заставок это читалось прыжком.
        // Поэтому подпись не толкает цветок, а висит под ним: отступ считается
        // от края нарисованных лепестков ([FLOWER_RADIUS]).
        FlowerWord(
            side = side,
            // Дописывается ровно имя приложения: дальше в той же картинке
            // идёт «Echo», и оно принадлежит разделу, а не заставке.
            shown = ASKYA_SHARE * written.value,
            flowerTint = flower,
            modifier = Modifier.scale(breathingScale()),
            contentDescription = "Askya",
        )
        Text(
            text = greeting,
            fontFamily = FontFamily.Serif,
            fontSize = 22.sp,
            letterSpacing = (-0.2).sp,
            color = AccentInk,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = side * FLOWER_RADIUS + 64.dp)
                .alpha(hello.value),
        )
    }
}

/**
 * Сторона цветка на системной заставке Android 12+.
 *
 * Столько система отводит значку без подложки — и рисует его этим размером на
 * любом телефоне, независимо от ширины экрана. Своя заставка подхватывает
 * цветок ровно им: иначе на стыке двух заставок он прыгает в размере. Сверено
 * замером на устройстве — нарисованный цветок совпал по ширине до пикселя.
 */
private val SYSTEM_ICON = 288.dp
