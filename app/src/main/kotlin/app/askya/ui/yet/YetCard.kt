package app.askya.ui.yet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlaylistRemove
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.ui.components.ActionButton
import app.askya.ui.components.Composer
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.MarkdownTask
import app.askya.ui.components.rememberDictation
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge

/**
 * Список, раскрытый карточкой почти во весь экран.
 *
 * Не отдельный экран, а карточка поверх полки — как файл в «Библиотеке»: в
 * список заглядывают и закрывают, а не уходят в него. Уход экраном убирает из
 * виду полку, с которой список сняли, а человек возвращается к ней же — чаще
 * всего чтобы открыть соседний.
 *
 * Внутри всё то же, что и на полном экране: строки отмечаются тапом, убираются
 * долгим нажатием, а новые пишутся окном внизу — карточка не «просмотр», в
 * список дописывают ровно тогда, когда в него смотрят.
 *
 * «Во весь экран» остаётся для длинных списков: тридцать строк в карточку
 * помещаются прокруткой, но искать в них глазами удобнее на целом экране.
 */
@Composable
fun YetCard(
    list: YetList,
    items: List<YetItem>,
    onDismiss: () -> Unit,
    onToggle: (YetItem) -> Unit,
    onRemoveRow: (YetItem) -> Unit,
    onAdd: (String) -> Unit,
    onEdit: () -> Unit,
    onClearDone: () -> Unit,
    onOpenFull: () -> Unit,
    onProblem: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    var draft by remember(list.id) { mutableStateOf(TextFieldValue()) }
    var beforeVoice by remember(list.id) { mutableStateOf("") }
    val composerFocus = remember { FocusRequester() }

    val dictation = rememberDictation(
        onHeard = { text, done ->
            val merged = if (beforeVoice.isEmpty()) text else "$beforeVoice $text"
            draft = TextFieldValue(merged, TextRange(merged.length))
            if (done) beforeVoice = merged
        },
        onProblem = onProblem,
    )

    fun send() {
        if (dictation.listening) dictation.stop()
        onAdd(draft.text)
        draft = TextFieldValue()
        beforeVoice = ""
    }

    // Появление: без переключения флага animateFloatAsState стартовал бы уже
    // в цели и не анимировал ничего.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "scrim",
    )
    // Карточка вырастает из своей плитки на полке — тем же движением, что и
    // карточка файла в Библиотеке.
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f),
        label = "grow",
    )

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
            // Кремовая, а не белая: это страница, как и раскрытый файл.
            colors = CardDefaults.cardColors(containerColor = Cream),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .cardEdge(RoundedCornerShape(28.dp))
                // Тап по самой карточке её не закрывает: иначе список
                // захлопывался бы от промаха мимо строки.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Head(
                    list = list,
                    left = items.count { !it.done },
                    total = items.size,
                    onClose = onDismiss,
                )

                if (items.isEmpty()) {
                    Blank(modifier = Modifier.weight(1f))
                } else {
                    FadingColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            start = 22.dp,
                            end = 22.dp,
                            top = 12.dp,
                            bottom = 8.dp,
                        ),
                    ) {
                        items(items, key = { it.id }) { item ->
                            CardRow(
                                item = item,
                                list = list,
                                onClick = { onToggle(item) },
                                onLongClick = { onRemoveRow(item) },
                            )
                        }
                    }
                }

                Composer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = ::send,
                    focusRequester = composerFocus,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    placeholder = if (dictation.listening) "Слушаю…" else "Написать в список…",
                    listening = dictation.listening,
                    onMic = {
                        if (dictation.listening) {
                            dictation.stop()
                        } else {
                            beforeVoice = draft.text.trimEnd()
                            dictation.start()
                        }
                    },
                )

                Feet(
                    hasDone = items.any { it.done },
                    onEdit = onEdit,
                    onClearDone = onClearDone,
                    onOpenFull = onOpenFull,
                )
            }
        }
    }
}

/**
 * Шапка карточки: название списка, под ним сколько осталось, справа крестик.
 *
 * Крестик нужен, хотя закрывают и тапом мимо, и «назад»: карточка занимает
 * почти весь экран, и промахнуться мимо неё почти негде.
 */
@Composable
private fun Head(list: YetList, left: Int, total: Int, onClose: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.title.ifBlank { "Без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = countLine(left = left, total = total),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HeaderIcon(
                icon = Icons.Outlined.Close,
                contentDescription = "Закрыть",
                onClick = onClose,
            )
        }

        // Черта под шапкой: список начинается ниже, и без неё название
        // читалось бы первой его строкой.
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(AccentSoft),
        )
    }
}

/** Строка списка в карточке — та же чек-строка, что и на полном экране. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardRow(
    item: YetItem,
    list: YetList,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    MarkdownTask(
        text = item.text,
        done = item.done,
        nested = item.nested,
        mark = list.mark,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
    )
}

/** Пустой список: сказать, что делать, вместо пустоты. */
@Composable
private fun Blank(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Пока пусто. Напиши строкой ниже — можно сразу списком: " +
                "«- хлеб», «1. позвонить»; отступ делает подпунктом.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Что со списком можно сделать, не выходя из карточки.
 *
 * «Убрать сделанное» появляется, только когда есть что убирать: пустая кнопка
 * в ряду учит не смотреть на ряд. Удаление самого списка живёт в правке — там
 * же, где название и знак: чтобы промах пальцем не сносил список по дороге к
 * галочке.
 */
@Composable
private fun Feet(
    hasDone: Boolean,
    onEdit: () -> Unit,
    onClearDone: () -> Unit,
    onOpenFull: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(
            icon = Icons.Outlined.EditNote,
            label = "Править",
            onClick = onEdit,
        )
        ActionButton(
            icon = Icons.Outlined.OpenInFull,
            label = "Во весь экран",
            onClick = onOpenFull,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (hasDone) {
            ActionButton(
                icon = Icons.Outlined.PlaylistRemove,
                label = "Убрать сделанное",
                onClick = onClearDone,
            )
        }
    }
}
