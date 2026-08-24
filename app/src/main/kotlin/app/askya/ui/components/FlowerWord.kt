package app.askya.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import app.askya.R

/**
 * Цветок Askya со словом вдоль правого лепестка — тот же кадр, что на иконке
 * приложения, только крупно и в движении.
 *
 * Слово — цельная каллиграфия «AskyaEcho» (`ic_wordmark_echo`), обрезанная по
 * ширине: [shown] — доля картинки, которую уже видно, и слово открывается
 * слева направо, будто его дописывают пером. На [ASKYA_SHARE] написано ровно
 * «Askya», дальше идёт «Echo». Две отдельные картинки пришлось бы стыковать
 * вручную и на каждом размере заново, а так стык нарисован шрифтом.
 *
 * Пользуются им двое, и оба пишут одно и то же слово: заставка приложения
 * доводит его до имени и на стыке останавливается, AskyaEcho продолжает до
 * названия раздела. Держать на это две копии геометрии значило бы дать им
 * разъехаться на первой же правке иконки.
 *
 * Числа — доля стороны цветка — печатает `tools/make-icon.ps1`: место слова на
 * лепестке задано там же, где рисуется иконка, и порознь они разъехались бы.
 */
@Composable
fun FlowerWord(
    side: Dp,
    shown: Float,
    modifier: Modifier = Modifier,
    spin: Float = 0f,
    // Белым, как на иконке: слово лежит на оранжевом лепестке, и белое
    // читается на нём и на любом листе, куда лепесток не достаёт.
    tint: Color = Color.White,
    contentDescription: String? = null,
) {
    val word = painterResource(R.drawable.ic_wordmark_echo)
    val wordWidth = side * WORD_FULL
    val wordHeight = wordWidth * (word.intrinsicSize.height / word.intrinsicSize.width)

    Box(modifier = modifier.size(side).rotate(spin)) {
        Image(
            painter = painterResource(R.drawable.ic_flower),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = word,
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = side * WORD_START)
                .size(width = wordWidth, height = wordHeight)
                .drawWithContent {
                    clipRect(right = size.width * shown.coerceIn(0f, 1f)) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

/**
 * Дыхание: цветок медленно набирает и отдаёт размер.
 *
 * Медленнее человеческого вдоха — торопить тут нечего, и то же дыхание у
 * цветка, которым Askya думает ([BreathingFlower]).
 *
 * Вдох и выдох идут по кривой, а не ровным ходом: при ровном цветок доходил до
 * края и разворачивался рывком — было видно, где кончается вдох. С замедлением
 * к краям разворот не заметен вовсе, и движение читается как одно непрерывное
 * дыхание.
 */
@Composable
fun breathingScale(): Float {
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(BREATH_MS / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    return scale
}

/**
 * Граница между «Askya» и «Echo» в картинке слова: разрез проходит по пустому
 * месту между росчерком «a» и началом «E», а не по перу.
 */
const val ASKYA_SHARE = 0.5627f

/** Сторона цветка от ширины экрана: со словом он занимает вдвое меньше листа. */
const val FLOWER_SHARE = 0.62f

/**
 * Радиус нарисованного цветка долей его стороны: лепестки не доходят до края
 * — кончик стоит на 34 из 54 от центра, остальное поле. По этому числу под
 * цветком ставят подпись, не подгоняя отступ на глаз.
 */
const val FLOWER_RADIUS = 0.295f

/** Полный вдох-выдох. Медленнее человеческого — торопить тут нечего. */
private const val BREATH_MS = 4_400

// Геометрия слова на лепестке. Печатает tools\make-icon.ps1 — там же, где
// собирается иконка; при смене шрифта, слова или лепестков числа берутся из
// его вывода, а не подгоняются на глаз.
//
// WORD_START — левый край слова, WORD_FULL — ширина всего «AskyaEcho», обе
// доли стороны цветка.
private const val WORD_START = 0.5278f
private const val WORD_FULL = 0.4124f
