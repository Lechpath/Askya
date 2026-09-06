package app.askya.ui.scroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.domain.model.MarkColor
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogChoice
import app.askya.ui.components.DialogText
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.EditableLine
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.NewButton
import app.askya.ui.components.SHELF_COLUMNS
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.TileRow
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink

/**
 * «Библиотека» — книги и файлы на одной полке.
 *
 * Раньше это были два подраздела Scroll: «Книги» отдельным экраном и «Файлы»
 * отдельным. Делить их значило спрашивать на входе то, чего человек не знает:
 * «Неемия: копилка» лежит в книге или сам по себе? Ищут не по этому признаку,
 * а по названию, и искать приходилось в двух местах подряд.
 *
 * И книги, и файлы выложены карточками по трое в ряд — той же сеткой, что
 * расписание дня. Файлы карточкой мельче книжной: книга это папка, внутри
 * которой ещё что-то есть, а файл — одна запись, и уравнивать их величиной
 * значило бы стирать разницу между полкой и тем, что на ней лежит.
 *
 * Сверху строка поиска — по всему, что в библиотеке лежит, включая убранное в
 * книги. Человек помнит название записи, а не книгу, в которую её положил.
 *
 * Тап по файлу раскрывает его карточкой поверх полки ([FileCard]), а не уводит
 * экраном: файл прочитали и вернулись к тому же месту.
 *
 * В поиске полка перестаёт быть полкой: найденное выкладывается строками во всю
 * ширину ([SearchResultRow]), с куском текста вокруг совпадения и отмеченным
 * маркером искомым — и в списке, и внутри раскрытой записи. Карточка в треть
 * экрана могла показать только название, а нашлась запись чаще всего по
 * строчке из середины.
 *
 * Кнопка одна на весь экран — «new file», как «new card» в AskyaDay. Три
 * кнопки («новая книга», «добавить файл», «новая заметка») заставляли выбирать
 * кнопку раньше, чем дело; теперь выбор один и он внутри.
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onViewFile: (Long) -> Unit,
) {
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(appContainer()))
    val books by viewModel.topics.collectAsStateWithLifecycle()
    val sizes by remember { viewModel.topicSizes() }
        .collectAsStateWithLifecycle(initialValue = emptyMap())
    val loose by viewModel.loose.collectAsStateWithLifecycle()
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScrollTopic?>(null) }
    var deleting by remember { mutableStateOf<Note?>(null) }
    // Раскрытый файл помнится номером, а не самой записью: список приходит из
    // базы заново, и по номеру карточка показывает переписанное, а не то, что
    // лежало в списке в момент тапа.
    var opened by remember { mutableStateOf<Long?>(null) }

    // Перенос: идёт ли он сейчас и чем кончился. Два состояния, а не одно:
    // между «выбрали файл» и «готово» проходят секунды, и экран, ничего не
    // говорящий всё это время, читается как не заметивший нажатия.
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<ImportOutcome?>(null) }

    val importNotes = rememberNoteImport(
        onStarted = { importing = true },
        onDone = { outcome ->
            importing = false
            imported = outcome
        },
    )

    val searching = query.isNotBlank()
    val shownBooks = if (searching) {
        books.filter { it.title.contains(query.trim(), ignoreCase = true) }
    } else {
        books
    }
    // Пока не ищут — на полке лежат записи без книги: те, что в книгах, видны
    // в самих книгах. Как только ищут, полка раскрывается вся: искать по
    // половине библиотеки значило бы не найти именно то, что убрано аккуратно.
    val shownFiles = if (searching) shelf.filter { matches(it, query) } else loose
    // Раскрытая карточка ищется во всём, что показано: в поиске это может
    // оказаться запись из книги, а не с полки.
    val visible = if (searching) shownFiles else loose

    // Всё, кроме картинок: у изображений свой раздел, и складывать их сюда
    // значило бы показывать одно и то же в двух местах.
    val pickFile = rememberFilePicker(
        mimeTypes = arrayOf("application/pdf", "text/*", "application/*"),
    ) { picked ->
        // Один файл — тот же разговор, что и всегда: «как назовём» и в какую
        // книгу. У пачки этого разговора нет: спрашивать имя десять раз подряд
        // — не разговор, а допрос.
        if (picked.size == 1) {
            viewModel.addFile(picked.first(), topicId = null, onCreated = onOpenNote)
        } else {
            viewModel.addFiles(picked, topicId = null) {}
        }
    }

    ScreenScaffold(
        title = "Библиотека",
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = { NewButton(label = "new file", onClick = { adding = true }) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ShelfSearch(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            val shelf = rememberLazyListState()
            FadingColumn(
                state = shelf,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 4.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (searching && shownBooks.isEmpty() && shownFiles.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Ничего не нашлось",
                            hint = "Поиск смотрит названия книг, имена файлов и текст заметок.",
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        )
                    }
                }

                if (shownBooks.isNotEmpty() || !searching) {
                    item { ShelfTitle("Книги") }
                }

                if (books.isEmpty() && !searching) {
                    item {
                        EmptyState(
                            title = "Книг пока нет",
                            hint = "Книга — это папка: в неё складываются заметки и файлы.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                }

                items(shownBooks.chunked(SHELF_COLUMNS), key = { row -> "book-${row.first().id}" }) { row ->
                    TileRow(row) { book, modifier ->
                        BookTile(
                            book = book,
                            count = sizes[book.id] ?: 0,
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { editing = book },
                            modifier = modifier,
                            highlight = query,
                        )
                    }
                }

                if (shownFiles.isNotEmpty() || !searching) {
                    item {
                        ShelfTitle(
                            text = if (searching) "Найденное" else "Файлы",
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }

                if (loose.isEmpty() && !searching) {
                    item {
                        EmptyState(
                            title = "Пока пусто",
                            hint = "Кнопкой внизу можно написать заметку или принести файл " +
                                "с телефона.",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                }

                if (searching) {
                    items(shownFiles, key = { note -> "found-${note.id}" }) { note ->
                        SearchResultRow(
                            note = note,
                            query = query,
                            onClick = { opened = note.id },
                            onLongClick = { deleting = note },
                        )
                    }
                } else {
                    items(loose.chunked(SHELF_COLUMNS), key = { row -> "file-${row.first().id}" }) { row ->
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
        }
    }

    // Карточка ищется в живом списке: переписали заметку — карточка показывает
    // переписанное, удалили запись — закрывается сама.
    opened?.let { id ->
        val note = visible.firstOrNull { it.id == id }
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
                // Раскрытая из поиска запись светится тем же словом, что и
                // строка, из которой её открыли.
                highlight = if (searching) query else "",
            )
        }
    }

    if (adding) {
        AddDialog(
            onDismiss = { adding = false },
            onNote = {
                adding = false
                viewModel.createNote(null, onOpenNote)
            },
            onFile = {
                adding = false
                pickFile()
            },
            onBook = {
                adding = false
                creating = true
            },
            onImport = {
                adding = false
                importNotes()
            },
        )
    }

    if (importing) {
        // Окно без ответа: закрывать нечего, пока перенос идёт, а «отмена»
        // посреди него оставила бы половину заметок перенесённой и половину
        // нет — состояние, из которого человеку нечем выбраться.
        AskyaDialog(onDismiss = {}, badge = { DialogBadge(Icons.Outlined.MoveToInbox) }) {
            DialogTitle("Переношу")
            DialogText(
                "Читаю выгрузку. Большой архив разбирается с полминуты — заметки " +
                    "появятся на полке сами.",
            )
        }
    }

    imported?.let { outcome ->
        AskyaNotice(
            title = if (outcome.notes > 0) "Перенесено" else "Заметок не нашлось",
            text = if (outcome.notes > 0) {
                "${notesWord(outcome.notes)} из ${filesWord(outcome.files)}. " +
                    "Все легли в Библиотеку — разложить их по книгам можно " +
                    "обычным способом."
            } else {
                "В принесённом не нашлось ни одной заметки. Askya читает выгрузки " +
                    "текстом (txt, md), Evernote (enex), Google Keep (json) и " +
                    "страницами (html) — в том числе внутри архива zip. Картинки и " +
                    "вложения она не переносит."
            },
            icon = Icons.Outlined.MoveToInbox,
            onDismiss = { imported = null },
        )
    }

    if (creating) {
        BookDialog(
            heading = "Новая книга",
            book = null,
            onDismiss = { creating = false },
            onConfirm = { title, color ->
                viewModel.addTopic(title, color)
                creating = false
            },
        )
    }

    editing?.let { book ->
        BookDialog(
            heading = "Книга",
            book = book,
            onDismiss = { editing = null },
            onConfirm = { title, color ->
                viewModel.updateTopic(book, title, color)
                editing = null
            },
            onDelete = {
                viewModel.deleteTopic(book.id)
                editing = null
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

/** Заголовок половины полки. */
/** «12 заметок» — падеж по числу. */
private fun notesWord(count: Int): String {
    val hundred = count % 100
    val ten = count % 10
    return when {
        hundred in 11..14 -> "$count заметок"
        ten == 1 -> "$count заметка"
        ten in 2..4 -> "$count заметки"
        else -> "$count заметок"
    }
}

/** «3 файлов» — он же для принесённого. */
private fun filesWord(count: Int): String {
    val hundred = count % 100
    val ten = count % 10
    return when {
        hundred in 11..14 -> "$count файлов"
        ten == 1 -> "$count файла"
        ten in 2..4 -> "$count файлов"
        else -> "$count файлов"
    }
}

@Composable
internal fun ShelfTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(bottom = 2.dp),
    )
}

/**
 * Что заводим: заметку, файл с телефона, книгу или перенос из чужого блокнота.
 *
 * Порядок по частоте: пишут чаще, чем приносят, и приносят чаще, чем заводят
 * новую полку. Перенос стоит последним и по той же мерке — его делают один раз
 * в жизни, в первый день. Но стоит он именно здесь, а не в настройках: в
 * первый день человек ищет не настройки, а кнопку «добавить», и не найдя
 * переноса в ней, решает, что переноса нет вовсе.
 */
@Composable
internal fun AddDialog(
    onDismiss: () -> Unit,
    onNote: () -> Unit,
    onFile: () -> Unit,
    onBook: (() -> Unit)? = null,
    onImport: (() -> Unit)? = null,
) {
    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Add) }) {
        DialogCaption("Что заводим?")

        DialogChoice(Icons.Outlined.EditNote, "Заметку", "Написать своими словами", onNote)
        DialogChoice(Icons.Outlined.AttachFile, "Файл", "Принести с телефона", onFile)
        onBook?.let {
            DialogChoice(Icons.Outlined.MenuBook, "Книгу", "Завести новую полку", it)
        }
        onImport?.let {
            DialogChoice(
                Icons.Outlined.MoveToInbox,
                "Перенести заметки",
                "Из выгрузки другого блокнота",
                it,
            )
        }

        // Ответа внизу нет: строка сама и есть выбор, и «ОК» под ней означал бы,
        // что после выбора нужно подтвердить ещё раз. Передумавший закрывает
        // окно тапом мимо него или «назад».
    }
}

/**
 * Карточка книги: название и цвет корешка.
 *
 * Название и цвет в одном месте, потому что заводят их одним движением: книгу
 * называют и тут же красят, чтобы потом узнавать её на полке не читая.
 *
 * [book] `null` означает новую книгу: тогда цвет не выбран, и корешок покажет
 * тот, что выведен из названия, — выбирать краску никто не обязан.
 */
@Composable
private fun BookDialog(
    heading: String,
    book: ScrollTopic?,
    onDismiss: () -> Unit,
    onConfirm: (String, MarkColor?) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var draft by remember(book) { mutableStateOf(book?.title.orEmpty()) }
    var color by remember(book) { mutableStateOf(book?.color) }

    val ready = draft.isNotBlank()

    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.MenuBook) }) {
        DialogCaption(heading)

        // Поле сразу открытое: имя — первое, что здесь заполняют, и ждать тапа
        // по строке незачем. Enter сохраняет, пустое имя не принимается.
        EditableLine(
            value = draft,
            onValueChange = { draft = it },
            active = true,
            dimmed = false,
            hint = "Как назовём?",
            fontSize = 26.sp,
            weight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            onDone = { if (ready) onConfirm(draft, color) },
        )

        Text(
            text = "Корешок",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        SpinePalette(
            chosen = color,
            // Повторный тап по выбранной краске снимает выбор: цвет тогда снова
            // выводится из названия, и вернуться к этому иначе было бы нечем.
            onPick = { picked -> color = if (picked == color) null else picked },
        )

        if (onDelete != null) {
            // Прямо в тексте: папку убирают, содержимое остаётся.
            Text(
                text = "При удалении книги записи не пропадут — переедут в «Файлы».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        DialogButtons {
            // «Удалить» уходит к левому краю, подальше от галочки: рядом их
            // легко перепутать, а промах здесь необратим.
            if (onDelete != null) {
                ActionButton(
                    icon = Icons.Outlined.DeleteOutline,
                    label = "Удалить",
                    color = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
                Spacer(modifier = Modifier.weight(1f))
            }
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Сохранить",
                accent = ready,
                enabled = ready,
                onClick = { onConfirm(draft, color) },
            )
        }
    }
}

/**
 * Палитра корешков: краски кружками, выбранная — с галочкой.
 *
 * Без подписей: цвет выбирают глазами, и слово «сливовый» не помогает его
 * узнать. Галочка внутри кружка, а не обводка вокруг: на тёмных красках
 * обводка почти не видна, а светлый знак виден на всех восьми.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpinePalette(chosen: MarkColor?, onPick: (MarkColor) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 4,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MarkColor.entries.forEach { option ->
            val picked = option == chosen
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(spineColor(option))
                    .border(
                        width = if (picked) 2.dp else 0.dp,
                        color = if (picked) Ink else Color.Transparent,
                        shape = CircleShape,
                    )
                    .clickable { onPick(option) },
            ) {
                if (picked) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Выбрано",
                        tint = Cream,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
