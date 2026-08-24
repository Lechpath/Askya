package app.askya.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.EmptyState
import app.askya.ui.components.NewButton
import app.askya.ui.components.SHELF_COLUMNS
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.TileRow

/**
 * Содержимое одной книги — записей, которым эту книгу назначили.
 *
 * Раньше этот же экран показывал и раздел «Файлы» (тема `null`). Файлы уехали
 * в «Библиотеку», к книгам, и разделять их экраном больше нечем: у книги
 * всегда есть номер.
 *
 * Кнопка та же, что на полке, — «new file»: и написанная заметка, и
 * принесённый файл попадают в эту книгу. Человек стоит в книге, и добавленное
 * должно оказаться в ней же. Новую книгу отсюда не заводят: полку заводят на
 * полке.
 *
 * Тап по записи раскрывает её карточкой ([FileCard]) — тем же движением, что и
 * в «Библиотеке»: одна и та же запись не должна открываться по-разному в
 * зависимости от того, откуда её открыли.
 *
 * Записи выложены карточками по трое в ряд — теми же, что на полке. Список в
 * одну колонку показывал в книге на десяток записей меньше, чем помещается, а
 * заметка и файл в книге — то же самое, что и в «Библиотеке».
 */
@Composable
fun BookScreen(
    topicId: Long,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onViewFile: (Long) -> Unit,
) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(appContainer()))

    val items by remember(topicId) { viewModel.inTopic(topicId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val book by remember(topicId) { viewModel.topic(topicId) }
        .collectAsStateWithLifecycle(initialValue = null)

    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    // Раскрытая запись помнится номером, а не самой записью: список приходит
    // из базы заново, и по номеру карточка показывает переписанное.
    var opened by remember { mutableStateOf<Long?>(null) }

    // Всё, кроме картинок: у изображений свой раздел, и складывать их сюда
    // значило бы показывать одно и то же в двух местах.
    val pickFile = rememberFilePicker(
        mimeTypes = arrayOf("application/pdf", "text/*", "application/*"),
    ) { picked ->
        // Один файл — тот же разговор, что и всегда: «как назовём». У пачки
        // этого разговора нет: спрашивать имя десять раз подряд — не разговор,
        // а допрос. Имена остаются те, что у файлов; переименовать можно потом.
        if (picked.size == 1) {
            viewModel.addFile(picked.first(), topicId, onCreated = onOpenNote)
        } else {
            viewModel.addFiles(picked, topicId) {}
        }
    }

    ScreenScaffold(
        title = book?.title.orEmpty().ifEmpty { "Книга" },
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = { NewButton(label = "new file", onClick = { adding = true }) },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        title = "Книга пуста",
                        hint = "Кнопкой внизу можно написать заметку или принести файл с телефона.",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }

            items(items.chunked(SHELF_COLUMNS), key = { row -> row.first().id }) { row ->
                TileRow(row) { note, modifier ->
                    FileTile(
                        note = note,
                        onClick = { opened = note.id },
                        onLongClick = { deleting = note },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    opened?.let { id ->
        val note = items.firstOrNull { it.id == id }
        if (note == null) {
            opened = null
        } else {
            FileCard(
                note = note,
                onDismiss = { opened = null },
                onEdit = { noteId ->
                    opened = null
                    onOpenNote(noteId)
                },
                onDelete = {
                    opened = null
                    deleting = note
                },
                onOpenFull = { noteId ->
                    // Карточка закрывается перед уходом: иначе «назад» из
                    // полноэкранного просмотра возвращал бы в неё же.
                    opened = null
                    onViewFile(noteId)
                },
            )
        }
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onNote = {
                adding = false
                viewModel.createNote(topicId, onOpenNote)
            },
            onFile = {
                adding = false
                pickFile()
            },
        )
    }

    deleting?.let { note ->
        AskyaAsk(
            title = "Удалить запись?",
            text = if (note.uri == null) {
                "Заметка будет стёрта. Вернуть не получится."
            } else {
                // Важно сказать прямо: в Scroll лежит ссылка, а не копия.
                "Ссылка на файл будет убрана из Scroll. Сам файл на телефоне останется."
            },
            confirm = "Удалить",
            onConfirm = {
                viewModel.delete(note)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}
