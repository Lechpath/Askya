package app.askya.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Знак части дня: солнце восходит, стоит в зените и сменяется месяцем.
 *
 * Три разные, всем известные фигуры — восход, солнце с лучами, месяц. Первая
 * попытка была другой (путь солнца дугой и диск на нём в трёх местах), и в
 * подписи размером в двадцать два пикселя все три читались одинаково: как
 * холмик. Знак, который надо разглядывать, не работает.
 *
 * ## Почему рисунком, а не картинкой
 *
 * Раньше это были три `drawable` (`ic_part_morning`, `ic_part_day`,
 * `ic_part_evening`) — те же самые фигуры, только неподвижные. Движение
 * пришлось перенести в код: `AnimatedVectorDrawable` умеет анимировать
 * готовый путь, но не умеет ни считать длину луча от фазы, ни обрезать диск
 * горизонтом, а собирать это из десятка `objectAnimator` в xml дольше и
 * читается хуже, чем двадцать строк рисования. Геометрия перенесена из тех
 * файлов один в один: те же радиусы, та же черта горизонта, тот же месяц.
 *
 * ## Что и как долго движется
 *
 * У каждого знака два движения. Первое — выход, играется один раз, когда
 * часть дня появилась на экране: солнце поднимается из-за горизонта, диск
 * зенита разворачивает лучи, месяц всплывает и проявляется. Второе — жизнь,
 * идущая всегда, пока часть дня видна: лучи дышат, по солнцу в зените ходит
 * волна, звёздочка у месяца мерцает.
 *
 * Жизнь нарочно медленная (два с половиной — четыре секунды на круг) и
 * маленькая по размаху. Знак стоит в подписи над сеткой дел, и то, что дёргает
 * глаз, здесь мешало бы читать расписание. Compose останавливает бесконечную
 * анимацию, когда её нечему рисовать: части, уехавшие за край списка, не
 * тратят ни кадра.
 *
 * Выход играется каждый раз, когда часть дня возвращается на экран: список
 * выбрасывает уехавшие строки, и вместе с ними уходит счётчик. Это не изъян —
 * солнце, встающее ровно тогда, когда до утра долистали, лучше солнца,
 * вставшего один раз за запуск и потом стоящего неподвижно.
 *
 * Рисуется всё в сетке 24×24 — той же, в которой были нарисованы прежние
 * картинки, — и один раз масштабируется до нужного размера. Числа ниже поэтому
 * можно сверять с прежними файлами прямо глазами.
 */
@Composable
fun DayPartIcon(
    part: DayPart,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    // Выход. Ключ по части дня: в списке одна и та же ячейка не превращается
    // из утра в вечер, но подпись может смениться при пересборке дня, и тогда
    // новый знак должен выйти, а не появиться уже вышедшим.
    val entry = remember(part) { Animatable(0f) }
    LaunchedEffect(part) {
        entry.animateTo(1f, tween(durationMillis = ENTRY_MILLIS, easing = FastOutSlowInEasing))
    }

    // Жизнь. Одна доля от нуля до единицы на круг; что из неё выйдет — дело
    // рисования: у каждой части своя длина круга и свой смысл этой доли.
    val phase by rememberInfiniteTransition(label = "part-${part.name}").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = part.cycleMillis(), easing = LinearEasing),
        ),
        label = "phase",
    )

    val crescent = remember { PathParser().parsePathString(CRESCENT).toPath() }
    val sparkle = remember { sparklePath() }

    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            when (part) {
                DayPart.MORNING -> drawMorning(tint, entry.value, wave(phase))
                DayPart.DAY -> drawDay(tint, entry.value, phase)
                DayPart.EVENING -> drawEvening(tint, crescent, sparkle, entry.value, phase)
            }
        }
    }
}

/** Длина круга жизни: у мерцания он короче, у дыхания лучей — длиннее. */
private fun DayPart.cycleMillis(): Int = when (this) {
    DayPart.MORNING -> 4200
    DayPart.DAY -> 2600
    DayPart.EVENING -> 3000
}

/**
 * «Утро» — солнце, выходящее из-за черты.
 *
 * Диск целый, а над горизонтом видна только та его часть, что успела
 * подняться: не полудиск, которому пририсовали движение, а настоящий восход,
 * обрезанный чертой. К концу выхода середина диска встаёт ровно на черту — и
 * получается тот самый полудиск, что был на неподвижной картинке.
 *
 * Лучи выходят последними, когда солнце почти поднялось: у земли им ещё не
 * из чего светить.
 */
private fun DrawScope.drawMorning(tint: Color, entry: Float, breath: Float) {
    val center = Offset(12f, 17f + 4.6f * (1f - entry))

    // Обрезка по черте, а не по краю знака: всё, что ниже, ещё под землёй.
    clipRect(top = 0f, bottom = HORIZON) {
        drawCircle(
            color = tint,
            radius = 4f,
            center = center,
            style = Stroke(width = STROKE),
        )
    }

    // Черта поверх диска: она закрывает срез обводки, и восход опирается на
    // землю, а не висит над ней обрубком.
    drawLine(
        color = tint,
        start = Offset(3f, HORIZON),
        end = Offset(21f, HORIZON),
        strokeWidth = STROKE,
        cap = StrokeCap.Round,
    )

    val rays = ((entry - 0.55f) / 0.45f).coerceIn(0f, 1f)
    if (rays <= 0f) return

    val far = 6.8f + 0.45f * breath
    for (degrees in intArrayOf(45, 90, 135)) {
        drawRay(
            tint = tint,
            center = center,
            degrees = degrees.toFloat(),
            from = 5.2f,
            to = 5.2f + (far - 5.2f) * rays,
            alpha = rays,
        )
    }
}

/**
 * «День» — солнце в зените, излучающее лучи.
 *
 * Черты горизонта здесь нет намеренно: солнце в середине дня стоит высоко, и
 * земля к этому ничего не добавляет, зато отнимает место у лучей.
 *
 * Излучение — волна, обходящая круг: длина и яркость каждого луча берутся из
 * общей фазы, сдвинутой на его место в круге. Восемь лучей, мигающих разом,
 * читались бы как моргание лампы; волна читается как свет, идущий от солнца.
 */
private fun DrawScope.drawDay(tint: Color, entry: Float, phase: Float) {
    val center = Offset(12f, 12f)

    drawCircle(
        color = tint,
        radius = 3.5f * (0.8f + 0.2f * entry),
        center = center,
        style = Stroke(width = STROKE),
        alpha = entry,
    )

    for (i in 0 until RAYS) {
        val wave = wave(phase - i.toFloat() / RAYS)
        val far = 6.8f + 1.5f * wave
        drawRay(
            tint = tint,
            center = center,
            degrees = i * (360f / RAYS),
            from = 5.1f,
            to = 5.1f + (far - 5.1f) * entry,
            alpha = entry * (0.5f + 0.5f * wave),
        )
    }
}

/**
 * «Вечер» — месяц и звёздочка рядом.
 *
 * Заходящее солнце было бы точнее по часам — вечер начинается в шесть, когда
 * кончается работа, а не в темноте, — но на подписи оно неотличимо от восхода:
 * те же полудиск, лучи и черта, только диск левее. Два знака, различимые лишь
 * при разглядывании, хуже одного неточного. Месяц читается сразу и говорит
 * верное: этим часом день кончается. Ночные дела попадают сюда же (см.
 * [DayPart]), и им он подходит вовсе без оговорок.
 *
 * Месяц всплывает: снизу вверх и из прозрачности — так же, как утреннее
 * солнце встаёт, только без черты, из-за которой всходить. Звёздочка
 * загорается после него и дальше мерцает всегда: месяц может стоять
 * неподвижно, звезда — нет.
 *
 * Мерцание не синусоида: к волне круга добавлена вторая, вдвое частая и
 * сдвинутая. Ровное затухание и разгорание читается как дыхание, а звезда не
 * дышит — она вздрагивает. Стоит она в правом верхнем углу, в вырезе месяца,
 * и ни одной из двух дуг не касается.
 */
private fun DrawScope.drawEvening(
    tint: Color,
    crescent: Path,
    sparkle: Path,
    entry: Float,
    phase: Float,
) {
    translate(top = 2.6f * (1f - entry)) {
        scale(0.9f + 0.1f * entry, pivot = Offset(12f, 12f)) {
            drawPath(
                path = crescent,
                color = tint,
                alpha = entry,
                style = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    val lit = ((entry - 0.5f) / 0.5f).coerceIn(0f, 1f)
    if (lit <= 0f) return

    // Две волны: медленная задаёт мерцание, быстрая ломает его ровность.
    val flicker = (0.5f + 0.34f * sin(TAU * phase) + 0.16f * sin(TAU * (2f * phase + 0.21f)))
        .coerceIn(0f, 1f)

    withTransform({
        translate(STAR_X, STAR_Y)
        scale(
            scaleX = STAR_SIZE * (0.62f + 0.38f * flicker) * lit,
            scaleY = STAR_SIZE * (0.62f + 0.38f * flicker) * lit,
            pivot = Offset.Zero,
        )
    }) {
        drawPath(path = sparkle, color = tint, alpha = lit * (0.35f + 0.65f * flicker))
    }
}

/**
 * Луч: отрезок от [from] до [to] по лучу, отложенному от [center] под углом
 * [degrees] — вверх это девяносто, как в школе, а не как в экранных
 * координатах, где ось игреков смотрит вниз.
 */
private fun DrawScope.drawRay(
    tint: Color,
    center: Offset,
    degrees: Float,
    from: Float,
    to: Float,
    alpha: Float,
) {
    val radians = degrees * PI.toFloat() / 180f
    val dx = cos(radians)
    val dy = -sin(radians)
    drawLine(
        color = tint,
        start = Offset(center.x + dx * from, center.y + dy * from),
        end = Offset(center.x + dx * to, center.y + dy * to),
        strokeWidth = STROKE,
        cap = StrokeCap.Round,
        alpha = alpha,
    )
}

/** Доля круга, обращённая в мягкую волну от нуля до единицы и обратно. */
private fun wave(phase: Float): Float = 0.5f - 0.5f * cos(TAU * phase)

/**
 * Звёздочка: четыре луча с вогнутыми боками, в единичном круге вокруг нуля.
 *
 * Вогнутость даёт та же пара опорных точек, что и в знаках Material: бок идёт
 * от кончика не по прямой к соседнему, а прогибается к середине — иначе
 * получился бы ромб, а ромб рядом с месяцем читается как ошибка, а не как
 * звезда.
 */
private fun sparklePath(): Path = Path().apply {
    val k = 0.42f
    moveTo(0f, -1f)
    cubicTo(k * 0.25f, -k, k, -k * 0.25f, 1f, 0f)
    cubicTo(k, k * 0.25f, k * 0.25f, k, 0f, 1f)
    cubicTo(-k * 0.25f, k, -k, k * 0.25f, -1f, 0f)
    cubicTo(-k, -k * 0.25f, -k * 0.25f, -k, 0f, -1f)
    close()
}

/** Толщина обводки — общая у всех трёх знаков и у прежних картинок. */
private const val STROKE = 1.7f

/** Черта горизонта на «Утре». */
private const val HORIZON = 17f

/** Сколько лучей у солнца в зените. */
private const val RAYS = 8

private const val STAR_X = 18.6f
private const val STAR_Y = 6f
private const val STAR_SIZE = 2.6f

private const val ENTRY_MILLIS = 900

private val TAU = (2.0 * PI).toFloat()

/** Месяц — тот же путь, что был в `ic_part_evening`, до знака после запятой. */
private const val CRESCENT = "M9.81,5.86 A6.4,6.4 0 1 0 17.62,14.18 A5.9,5.9 0 0 1 9.81,5.86 Z"
