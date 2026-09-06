package app.askya.ui.yet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.YetItem
import app.askya.domain.model.ListMark
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.Composer
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.MarkdownTask
import app.askya.data.repository.Trash
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.rememberDictation

/**
 * Один список Yet.
 *
 * Пишут сюда тем же окном, что и в карточку заметки, — [Composer] внизу
 * страницы: плюса нет (в список нечего приносить, кроме букв), но строка,
 * микрофон и отправка те же. Список часто заводят на ходу и вслух, и заставлять
 * человека набирать «молоко, хлеб, батарейки» руками там, где в заметке он это
 * говорит, значило бы держать два разных приложения в одном.
 *
 * Записывают разметкой, как везде: «- хлеб», «1. позвонить», «- [x] уже
 * сделано», отступ в начале — подпункт. Одна отправка кладёт столько строк,
 * сколько их набрали или вставили: список, пришедший в сообщении, ложится
 * строками, а не одним комком (разбор — `domain/markdown/ListInput.kt`).
 *
 * Показывается строка тоже как в заметке — чек-строкой разметки [MarkdownTask]:
 * знак слева, подпункт с отступом. Форма знака своя у каждого списка (её
 * выбирают вместе с названием), а цвет один на всё приложение: серый, пока не
 * отмечено, коралловый — когда отмечено.
 *
 * Отмеченное не исчезает, а уходит вниз. Зачёркивания нет: зачёркнутое читается
 * как отменённое, а строку выполнили. Убирается отдельным действием, когда
 * надоест.
 */
@Composable
fun YetListScreen(listId: Long, onBack: () -> Unit) {
    val trash = appContainer().trash
    val viewModel: YetViewModel = viewModel(factory = YetViewModel.factory(appContainer()))
    val list by remember(listId) { viewModel.list(listId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val items by remember(listId) { viewModel.items(listId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var draft by remember(listId) { mutableStateOf(TextFieldValue()) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Опора для надиктовки: сказанное дописывается к уже набранному, а не
    // затирает его. То же правило, что в карточке заметки.
    var beforeVoice by remember(listId) { mutableStateOf("") }
    val composerFocus = remember { FocusRequester() }

    val dictation = rememberDictation(
        onHeard = { text, done ->
            val merged = if (beforeVoice.isEmpty()) text else "$beforeVoice $text"
            draft = TextFieldValue(merged, TextRange(merged.length))
            if (done) beforeVoice = merged
        },
        onProblem = { notice = it },
    )

    fun send() {
        // Отправка гасит микрофон: договорил — значит, надиктовал.
        if (dictation.listening) dictation.stop()
        viewModel.addLines(listId, draft.text)
        draft = TextFieldValue()
        beforeVoice = ""
    }

    ScreenScaffold(
        title = list?.title?.ifBlank { "Без названия" } ?: "Yet",
        onNavigationClick = onBack,
        navigationIsBack = true,
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // Одно действие в строке над списком — как «Очистить» в AskyaDay.
            // Счётчика рядом с ним больше нет: сколько осталось, видно по
            // самому списку, а слово «ещё» стояло в разделе на каждом экране и
            // перестало что-либо значить.
            if (items.any { it.done }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Убрать сделанное",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { viewModel.clearDone(listId) },
                    )
                }
            }

            FadingColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            ) {
                if (items.isEmpty()) {
                    item {
                        Text(
                            text = "Пока пусто. Напиши, что нужно, — строкой ниже. " +
                                "Можно списком: «- хлеб», «1. позвонить»; отступ делает подпунктом.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 32.dp),
                        )
                    }
                }

                items(items, key = { it.id }) { item ->
                    YetItemRow(
                        item = item,
                        mark = list?.mark ?: ListMark.SQUARE,
                        onClick = { viewModel.toggle(item) },
                        onLongClick = {
                            viewModel.removeItem(item.id)
                            trash.remembered(Trash.Kind.YET_ROW, item.id)
                        },
                    )
                }
            }

            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = ::send,
                focusRequester = composerFocus,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
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
        }
    }


    notice?.let { text ->
        AskyaNotice(
            title = "Надиктовка",
            text = text,
            onDismiss = { notice = null },
            icon = Icons.Outlined.MicNone,
        )
    }
}

/**
 * Строка списка — та же чек-строка, что в разметке заметки, но тап по всей
 * ширине: отмечают на ходу, и целиться пальцем в квадрат размером с букву
 * неудобно. Длинное нажатие убирает строку.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YetItemRow(
    item: YetItem,
    mark: ListMark,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    MarkdownTask(
        text = item.text,
        done = item.done,
        nested = item.nested,
        mark = mark,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
    )
}
