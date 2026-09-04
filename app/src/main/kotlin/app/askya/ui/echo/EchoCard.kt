package app.askya.ui.echo

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightDanger
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset

/**
 * Карточка Echo — та же карточка, что и во всей Askya, только в темноте.
 *
 * Раздел раньше открывал списки страницами во весь экран, и это выбивалось из
 * приложения: везде — в дне, в библиотеке, в альбомах — нажатое раскрывается
 * карточкой поверх того места, откуда его нажали, и закрывается обратно в
 * него. Плеер тем более: списки открывают, чтобы что-нибудь включить, и
 * играющая песня не должна пропадать с глаз ради выбора следующей.
 *
 * Те же 28 скруглений, тот же рост пружиной из ничего и то же затемнение
 * позади, что у `CardDialog` и `FileCard`, — но на ночной палитре: тёмная
 * подложка, закатный акцент, светлые буквы.
 *
 * Размер задаётся снаружи ([width], [height] — доли экрана): «Вся музыка» —
 * большая карточка со списком, плейлисты и папки — поменьше. Это не прихоть
 * оформления: величина карточки говорит, сколько за ней стоит.
 *
 * [height] = `null` — «по написанному»: карточка ровно такой высоты, сколько
 * заняло содержимое, но не выше [FIT_SHARE] экрана. Так открываются карточки
 * с рядом действий: шесть строк, растянутые на четыре пятых экрана, — это
 * половина пустого листа под ними, а в Askya раскрытое всегда по размеру того,
 * что раскрыли. Доля остаётся у списков: им место нужно всегда, и карточка,
 * прыгающая по высоте вслед за числом найденных песен, читалась бы как сбой.
 */
@Composable
fun EchoCard(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    width: Float = 0.94f,
    height: Float? = 0.9f,
    back: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
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
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f),
        label = "grow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(Night.copy(alpha = 0.9f))
            // Тап мимо карточки — закрыть. Без indication: рябь во весь экран
            // выглядела бы дико.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Потолок для карточки «по написанному»: выше него содержимое
        // прокручивается внутри карточки, а не выезжает за края экрана.
        val fit = (LocalConfiguration.current.screenHeightDp * FIT_SHARE).dp

        Column(
            modifier = Modifier
                .fillMaxWidth(width)
                .then(
                    if (height != null) Modifier.fillMaxHeight(height)
                    else Modifier.heightIn(max = fit),
                )
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .clip(RoundedCornerShape(28.dp))
                .background(NightPanel)
                .border(1.dp, NightBorder, RoundedCornerShape(28.dp))
                // Тап по самой карточке не закрывает её: иначе выбор
                // обрывался бы от промаха мимо строки.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 24.sp,
                        color = NightInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = NightMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                actions()
                EchoIcon(
                    icon = if (back) Icons.AutoMirrored.Outlined.ArrowBack else Icons.Outlined.Close,
                    label = if (back) "Назад" else "Закрыть",
                    onClick = onDismiss,
                )
            }

            // Черта под шапкой: содержимое начинается ниже, и без неё первая
            // строка списка читалась бы продолжением заголовка.
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NightBorder),
            )

            content()
        }
    }
}

/** Выше этого карточка «по написанному» не растёт — дальше она прокручивается. */
private const val FIT_SHARE = 0.9f

/**
 * Маленькое окно Echo: вопрос или одно поле, без списка.
 *
 * [EchoCard] задан долями экрана — он для списков, которым нужно место. Здесь
 * карточка ровно по написанному: спросить «удалить плейлист?» во весь экран
 * значит сделать из вопроса событие.
 *
 * Всё остальное то же самое: 28 скруглений, рост пружиной, ночь позади, — окно
 * Askya, только в темноте.
 */
@Composable
fun EchoDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "scrim",
    )
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 340f),
        label = "grow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(Night.copy(alpha = 0.9f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // Клавиатура приходит вместе с окном имени и без этого накрыла бы
            // и поле, и ответ под ним.
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .clip(RoundedCornerShape(28.dp))
                .background(NightPanel)
                .border(1.dp, NightBorder, RoundedCornerShape(28.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(22.dp),
        ) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontSize = 22.sp,
                color = NightInk,
            )
            content()
        }
    }
}

/**
 * Вопрос Echo с двумя ответами. Необратимое — красным словом, оставить всё как
 * было — обычным: цвет отвечает раньше, чем прочитана надпись.
 */
@Composable
fun EchoAsk(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    EchoDialog(title = title, onDismiss = onDismiss) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = NightMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EchoPill(
                label = confirm,
                chosen = false,
                danger = true,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
            EchoPill(
                label = "Оставить",
                chosen = false,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Строка, которую в окне Echo заполняют, — на подложке, а не в рамке Material:
 * поле с обводкой и всползающей подписью светит днём посреди ночного раздела.
 */
@Composable
fun EchoField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = NightInk)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            Text(text = hint, style = style.copy(color = NightMuted))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(Sunset),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
    }
}

/** Круглая кнопка в шапке карточки — тот же круг, что в шапке экрана. */
@Composable
fun EchoIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = NightMuted,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

/**
 * Слово в рамке — выбор одним касанием: заготовка эквалайзера, длина сна,
 * скорость. Выбранное залито закатом, остальные стоят обводкой.
 *
 * [danger] красит слово и рамку в красный: так помечено необратимое — «удалить»
 * рядом с «оставить» должно отличаться не только надписью.
 */
@Composable
fun EchoPill(
    label: String,
    chosen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    val edge = when {
        chosen -> Sunset
        danger -> NightDanger
        else -> NightBorder
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            chosen -> Night
            danger -> NightDanger
            else -> NightInk
        },
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (chosen) Sunset else NightPanel)
            .border(1.dp, edge, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Заголовок кучки настроек внутри карточки. */
@Composable
fun EchoGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            color = Sunset,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}
