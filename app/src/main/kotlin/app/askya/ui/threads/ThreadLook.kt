package app.askya.ui.threads

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.askya.domain.model.MarkColor
import app.askya.domain.model.NodeShape
import app.askya.domain.model.NodeSize
import app.askya.domain.model.ThreadNodeKind
import app.askya.domain.model.ThreadState
import app.askya.ui.theme.markColor

/**
 * Как узлы и состояния выглядят: одна таблица на весь раздел.
 *
 * Вид узла собран из трёх вещей — цвет, форма и значок, — и все три говорят об
 * одном: что это за карточка. Три, а не одна, потому что карту смотрят с
 * разного расстояния. Отодвинутая карта — это цветные пятна разной формы, и по
 * ним уже видно, где искра, а где камни. Приближенная показывает значок и
 * подпись.
 *
 * Смайликов нет ни одного, хотя в замысле раздела они напрашивались. Эмодзи
 * рисует шрифт телефона: на одном они плоские, на другом объёмные, на третьем
 * половины нет вовсе — и кремовая бумага Askya мгновенно превращается в чат.
 * Значок из того же набора, что во всём приложении, ведёт себя одинаково везде.
 */

/**
 * Краска узла — из той же палитры, которой покрашены книги, счета и нити.
 *
 * Одна палитра на приложение и здесь: человек, узнающий зелёное в Ledger, не
 * должен переучиваться в Threads (см. [MarkColor]).
 *
 * Красок восемь, а типов девять, и совпадение одно — искра и поворот. Оно не
 * от нехватки: поворот и есть искра, случившаяся посреди пути. С него замысел
 * начинается заново, и цвет говорит об этом раньше подписи.
 */
fun nodeColor(kind: ThreadNodeKind): Color = markColor(
    when (kind) {
        ThreadNodeKind.SPARK -> MarkColor.CORAL
        ThreadNodeKind.TURN -> MarkColor.CORAL
        ThreadNodeKind.QUESTION -> MarkColor.PLUM
        ThreadNodeKind.THOUGHT -> MarkColor.BLUE
        ThreadNodeKind.RESULT -> MarkColor.GREEN
        ThreadNodeKind.PATH -> MarkColor.BROWN
        ThreadNodeKind.SNAG -> MarkColor.AMBER
        ThreadNodeKind.STEP -> MarkColor.INK
        ThreadNodeKind.INSIGHT -> MarkColor.ROSE
    },
)

/** Значок типа — тот, что стоит на карточке и в выборе. */
fun nodeIcon(kind: ThreadNodeKind): ImageVector = when (kind) {
    ThreadNodeKind.SPARK -> Icons.Outlined.LocalFireDepartment
    ThreadNodeKind.QUESTION -> Icons.AutoMirrored.Outlined.HelpOutline
    ThreadNodeKind.THOUGHT -> Icons.Outlined.Psychology
    ThreadNodeKind.RESULT -> Icons.Outlined.Flag
    ThreadNodeKind.PATH -> Icons.AutoMirrored.Outlined.AltRoute
    ThreadNodeKind.SNAG -> Icons.Outlined.WarningAmber
    ThreadNodeKind.STEP -> Icons.AutoMirrored.Outlined.DirectionsWalk
    ThreadNodeKind.INSIGHT -> Icons.Outlined.Lightbulb
    ThreadNodeKind.TURN -> Icons.Outlined.Bolt
}

/**
 * Форма карточки.
 *
 * Круглое — то, что случилось само; мягкое — придуманное; со срезанным углом —
 * то, что ломает ход; острое — шаг, единственное, что делается руками.
 * Рассуждение целиком — при [NodeShape].
 */
fun nodeShape(kind: ThreadNodeKind): Shape = when (kind.shape) {
    NodeShape.ROUND -> RoundedCornerShape(percent = 46)
    NodeShape.SOFT -> RoundedCornerShape(18.dp)
    NodeShape.CUT -> CutCornerShape(topStart = 18.dp, bottomEnd = 18.dp)
    NodeShape.SHARP -> RoundedCornerShape(5.dp)
}

/**
 * Ширина карточки на карте.
 *
 * Крупные держат карту и читаются издали; мелких бывает по десятку, и в полную
 * ширину они забили бы всё поле. Мерка тут, а не в домене: сколько это в
 * пикселях, знает только разметка.
 */
fun nodeWidth(size: NodeSize): Dp = when (size) {
    NodeSize.SMALL -> 124.dp
    NodeSize.PLAIN -> 144.dp
    NodeSize.BIG -> 168.dp
}

/** Значок состояния нити — им она помечена и в ленте, и в шапке карты. */
fun stateIcon(state: ThreadState): ImageVector = when (state) {
    ThreadState.BURNING -> Icons.Outlined.LocalFireDepartment
    ThreadState.GROWING -> Icons.Outlined.Grass
    ThreadState.WEAVING -> Icons.Outlined.Hub
    ThreadState.SMOULDERING -> Icons.Outlined.HourglassEmpty
    ThreadState.SLEEPING -> Icons.Outlined.Bedtime
    ThreadState.DONE -> Icons.Outlined.Check
    ThreadState.DROPPED -> Icons.Outlined.Close
}

/**
 * Краска состояния.
 *
 * Горит — коралловое, растёт — зелёное, плетётся — синее: три идущих состояния
 * различаются, потому что различается и то, что в них происходит. Тлеет и спит
 * — тёплое тусклое и чернильное: тишина не тревога, красным она не красится.
 * Завершена — зелёное, брошена — чернильное: **не красное**. Брошенная нить не
 * ошибка и не потеря, а честный конец, и пугать им незачем.
 */
fun stateColor(state: ThreadState): Color = markColor(
    when (state) {
        ThreadState.BURNING -> MarkColor.CORAL
        ThreadState.GROWING -> MarkColor.GREEN
        ThreadState.WEAVING -> MarkColor.BLUE
        ThreadState.SMOULDERING -> MarkColor.AMBER
        ThreadState.SLEEPING -> MarkColor.PLUM
        ThreadState.DONE -> MarkColor.GREEN
        ThreadState.DROPPED -> MarkColor.INK
    },
)
