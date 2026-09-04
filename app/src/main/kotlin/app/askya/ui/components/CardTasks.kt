package app.askya.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.askya.domain.model.ListMark
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk

/** Строка списка внутри дела — то, что о ней знает карточка. */
data class CardTask(val id: Long, val text: String, val done: Boolean)

/**
 * Список задач внутри дела — тем же видом, что и список Yet.
 *
 * ## Почему он выглядит как Yet, а не по-своему
 *
 * Отмечаемая строка в Askya одна и та же везде: чек-строка в заметке, строка
 * списка Yet и задача внутри дела. Знак слева, серый до отметки и коралловый
 * после, отмеченное уходит вниз и не зачёркивается — зачёркнутое читается как
 * отменённое, а строку выполнили.
 *
 * Знак только квадратный: форму знака в Yet выбирают списку, как цвет корешка
 * книге, — там списков десяток и их различают в лицо. Внутри дела список один,
 * различать его не с чем, и выбор формы был бы вопросом без последствий.
 *
 * ## Как пишут
 *
 * Тем же окном [Composer], что и в списке Yet: строка, микрофон, отправка. Одна
 * отправка кладёт столько строк, сколько их набрали или вставили, — разбор
 * разметки живёт в репозитории (`DeedTaskRepository.addLines`), и «- хлеб»,
 * «1. позвонить», «- [x] сделано» значат здесь ровно то же, что везде.
 *
 * Длинное нажатие убирает строку — в корзину на сутки, как везде. Отдельного
 * крестика у строки нет: он стоял бы у каждой из семи ради того, чем
 * пользуются раз в неделю.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CardTaskList(
    tasks: List<CardTask>,
    onToggle: (CardTask) -> Unit,
    onRemove: (CardTask) -> Unit,
    onClearDone: () -> Unit,
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
    onProblem: (String) -> Unit = {},
) {
    var draft by remember { mutableStateOf(TextFieldValue()) }

    // Опора для надиктовки: сказанное дописывается к уже набранному, а не
    // затирает его. То же правило, что в списке Yet и в карточке заметки.
    var beforeVoice by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    val dictation = rememberDictation(
        onHeard = { text, done ->
            val merged = if (beforeVoice.isEmpty()) text else "$beforeVoice $text"
            draft = TextFieldValue(merged, TextRange(merged.length))
            if (done) beforeVoice = merged
        },
        onProblem = onProblem,
    )

    fun send() {
        // Отправка гасит микрофон: договорил — значит, надиктовал.
        if (dictation.listening) dictation.stop()
        if (draft.text.isNotBlank()) onAdd(draft.text)
        draft = TextFieldValue()
        beforeVoice = ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // «Убрать сделанное» — там же и теми же словами, что в списке Yet.
        // Показывается, только когда есть что убирать: строка действия,
        // висящая над пустым списком, обещает больше, чем делает.
        if (tasks.any { it.done }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "Убрать сделанное",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClearDone)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (tasks.isEmpty()) {
                item {
                    Text(
                        text = "Что нужно сделать в этом деле? Пиши строкой ниже — " +
                            "по одной или списком сразу.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            items(tasks, key = { it.id }) { task ->
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { onToggle(task) },
                            onLongClick = { onRemove(task) },
                        )
                        .padding(vertical = 6.dp),
                ) {
                    MarkView(
                        mark = ListMark.SQUARE,
                        done = task.done,
                        modifier = Modifier.padding(top = 1.dp, end = 10.dp),
                    )
                    Text(
                        text = task.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (task.done) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            AccentInk
                        },
                    )
                }
            }
        }

        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = ::send,
            focusRequester = focus,
            placeholder = if (dictation.listening) "Слушаю…" else "Добавить строку…",
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
    }
}

/**
 * Строка списка в карточке: сколько сделано и сколько всего.
 *
 * Стоит рядом с заметкой и привязкой и читается так же — «что у этого дела
 * есть». Пустая говорит «Список» серым: списка ещё нет, и нажатие его заводит.
 */
@Composable
internal fun TaskLine(
    tasks: List<CardTask>,
    dimmed: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val faded = MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (tasks.isEmpty()) {
        "Список"
    } else {
        "Список · ${tasks.count { it.done }} из ${tasks.size}"
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                dimmed -> faded.copy(alpha = 0.5f)
                tasks.isEmpty() -> faded
                else -> AccentInk
            },
        )
    }
}
