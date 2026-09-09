package app.askya.ui.yet

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.data.entity.YetList
import app.askya.domain.model.ListMark
import app.askya.ui.components.ActionButton
import app.askya.ui.components.MarkView
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge

/**
 * Карточка списка: как назвать и чем отмечать.
 *
 * Не диалог Material с обведённым полем и кнопками по углам — такая карточка
 * читалась бы куском чужого приложения поверх Askya. Здесь та же кремовая
 * страница, что у раскрытого файла: крупный серифный заголовок, строка без
 * рамки и знаки, выбранные глазами.
 *
 * Название и знак стоят рядом, потому что решают их одним движением: список
 * называют и тут же выбирают, чем в нём отмечать. Оба ответа необязательны по
 * очереди — важно только имя, знак и так стоит на квадрате.
 *
 * [list] `null` означает новый список.
 */
@Composable
fun ListDialog(
    list: YetList?,
    onDismiss: () -> Unit,
    onConfirm: (String, ListMark) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var draft by remember(list) { mutableStateOf(list?.title.orEmpty()) }
    var mark by remember(list) { mutableStateOf(list?.mark ?: ListMark.SQUARE) }
    val focus = remember { FocusRequester() }

    BackHandler(onBack = onDismiss)

    // Появление: без переключения флага animateFloatAsState стартовал бы уже
    // в цели и не анимировал ничего.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        // Новый список начинается с названия — как новая заметка в Библиотеке
        // открывается прямо на строке имени.
        if (list == null) focus.requestFocus()
    }
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

    fun confirm() {
        if (draft.isBlank()) return
        onConfirm(draft.trim(), mark)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            // Тап мимо карточки — закрыть. Без indication: рябь во весь экран
            // выглядела бы дико.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Cream),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .cardEdge(RoundedCornerShape(28.dp))
                // Тап по самой карточке её не закрывает: иначе выбор знака
                // обрывался бы от промаха мимо кружка.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
                Text(
                    text = if (list == null) "Новый список" else "Список",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                    color = Ink,
                )

                // Строка имени без рамки — как ответ в разговоре: курсор,
                // подсказка бледным и черта под строкой вместо коробки.
                Box(modifier = Modifier.padding(top = 18.dp)) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Как назовём?",
                            style = MaterialTheme.typography.titleMedium,
                            color = Muted,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(color = Ink),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AccentSoft),
                )

                Text(
                    text = "Чем отмечать",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
                )
                MarkPalette(chosen = mark, onPick = { mark = it })

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        ActionButton(
                            icon = Icons.Outlined.DeleteOutline,
                            label = "Удалить",
                            color = MaterialTheme.colorScheme.error,
                            onClick = onDelete,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    ActionButton(
                        icon = Icons.Outlined.Check,
                        label = if (list == null) "Завести" else "Сохранить",
                        accent = true,
                        enabled = draft.isNotBlank(),
                        onClick = ::confirm,
                    )
                }
            }
        }
    }
}

/**
 * Выбор знака — самими знаками, а не словами.
 *
 * Показаны отмеченными: выбирают то, как список будет выглядеть, когда в нём
 * начнут отмечать, а серый знак у всех четырёх видов почти одинаков. Выбранная
 * плитка подсвечена и обведена — на кремовой странице одной обводки мало.
 */
@Composable
private fun MarkPalette(chosen: ListMark, onPick: (ListMark) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ListMark.entries.forEach { option ->
            val picked = option == chosen
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (picked) AccentSoft else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (picked) 2.dp else 0.dp,
                        color = if (picked) Ink else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onPick(option) },
            ) {
                MarkView(mark = option, done = true)
            }
        }
    }
}
