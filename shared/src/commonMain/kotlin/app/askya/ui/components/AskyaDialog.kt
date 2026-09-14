package app.askya.ui.components

import app.askya.platform.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Danger
import app.askya.ui.theme.ModeRedSoft
import app.askya.ui.theme.cardEdge

/**
 * Окно Askya: то, что раскрывается поверх экрана и ждёт ответа.
 *
 * Одна карточка на всё приложение. Раньше половина вопросов задавалась
 * системным `AlertDialog`: серая плашка с двумя словами по правому краю. Так
 * спрашивает телефон, а не Askya, — и человек посреди кремовой страницы вдруг
 * видел чужое окно. Здесь те же 28 скруглений, тот же рост пружиной из ничего
 * и то же затемнение позади, что у карточки дела (`CardDialog`), альбома и
 * файла: раскрытое всегда выглядит одинаково, о чём бы ни спрашивали.
 *
 * Затемнение — кремовый фон приложения, а не чёрная кисея Material: страница
 * под окном не гаснет, а отступает.
 *
 * [badge] — знак в круге над содержимым: по нему окно узнают раньше, чем
 * прочитают. [DialogBadge] рисует его из значка или из своего рисунка.
 */
@Composable
fun AskyaDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Float = 0.82f,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    // Появление: без переключения флага animateFloatAsState стартовал бы уже
    // в цели и не анимировал ничего.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "scrim",
    )
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.84f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "grow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            // Тап мимо карточки — закрыть. Жестом, а не `clickable`: см.
            // [tapOnly] — пробел из поля закрывал бы окно.
            .tapOnly(onDismiss)
            // Клавиатура открывается вместе с окном, где что-то набирают, и без
            // этого она накрывала бы и поле, и значки под ним.
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth(width)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .cardEdge(RoundedCornerShape(28.dp))
                // Тап по самой карточке не закрывает её: иначе набранное
                // терялось бы от промаха мимо строки.
                .keepTaps(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                badge?.invoke()
                content()
            }
        }
    }
}

/**
 * Знак окна — кружок со значком, тот же, что у альбома и у карточки дела.
 *
 * [danger] меняет краску на красную: необратимое должно отличаться от
 * обыкновенного вопроса раньше, чем человек дочитает заголовок.
 */
@Composable
fun DialogBadge(icon: ImageVector, danger: Boolean = false) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (danger) ModeRedSoft else AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) Danger else Accent,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Тот же знак, но нарисованный пером: свои значки лежат рисунками. */
@Composable
fun DialogBadge(painter: Painter) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(AccentSoft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(34.dp),
        )
    }
}

/** Заголовок окна — пером, как название раздела в шапке. */
@Composable
fun DialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(top = 16.dp),
    )
}

/** Маленькая подпись над содержимым: чем окно занято. */
@Composable
fun DialogCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/** Что случится, если ответить «да». Объясняет заголовок, а не повторяет его. */
@Composable
fun DialogText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 10.dp),
    )
}

/**
 * Строка, которую в окне заполняют: своё поле вместо `OutlinedTextField`.
 *
 * У Material поле обведено рамкой, в разрыв которой всползает подпись, — на
 * кремовой карточке это чужой прибор. Здесь написанное лежит на той же плашке,
 * что подсвечивает правку в карточке дела: место, куда пишут, показано
 * заливкой, а не обводкой.
 *
 * [autoFocus] открывает клавиатуру сразу: в окне с одним полем ждать тапа по
 * нему незачем.
 *
 * [keyboard] меняет саму клавиатуру: в поле, куда пишут вес или пульс, буквы
 * не нужны вовсе, а тянуться до цифрового ряда на каждой строке подхода —
 * работа, которой не должно быть.
 */
@Composable
fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focus.requestFocus() }

    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(
                text = hint,
                style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(Accent),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboard,
                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/**
 * Ряд ответов внизу окна — значками с подписью, как в карточке дела.
 *
 * Ответы стоят у правого края: палец приходит справа снизу.
 */
@Composable
fun DialogButtons(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Строка выбора: значок, что это, и строчка про то, чем обернётся.
 *
 * Ею набраны окна «что заводим» и «что добавим»: тап по строке и есть ответ,
 * подтверждать его отдельным словом «ОК» незачем.
 */
@Composable
fun DialogChoice(
    icon: ImageVector,
    title: String,
    about: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    picked: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = if (picked) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (about.isNotBlank()) {
                Text(
                    text = about,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Вопрос с двумя ответами: «да» и «передумал».
 *
 * Согласие отодвинуто от правого края, а под пальцем у края стоит «Отмена»:
 * промах по необратимому стоит дороже промаха по отказу.
 */
@Composable
fun AskyaAsk(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Outlined.DeleteOutline,
    danger: Boolean = true,
) {
    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(icon, danger = danger) }) {
        DialogTitle(title)
        DialogText(text)

        DialogButtons {
            ActionButton(
                icon = icon,
                label = confirm,
                color = if (danger) MaterialTheme.colorScheme.error else Accent,
                accent = !danger,
                onClick = onConfirm,
            )
            Spacer(modifier = Modifier.width(16.dp))
            ActionButton(
                icon = Icons.Outlined.Close,
                label = "Отмена",
                onClick = onDismiss,
            )
        }
    }
}

/**
 * Сообщение без выбора: приложению нечего спросить, ему есть что сказать.
 *
 * Один ответ — «Понятно»: другого здесь и быть не может, а окно совсем без
 * ответа нечем закрыть, кроме промаха мимо него.
 */
@Composable
fun AskyaNotice(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    icon: ImageVector = Icons.Outlined.ErrorOutline,
) {
    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(icon) }) {
        DialogTitle(title)
        DialogText(text)

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Понятно",
                accent = true,
                onClick = onDismiss,
            )
        }
    }
}
