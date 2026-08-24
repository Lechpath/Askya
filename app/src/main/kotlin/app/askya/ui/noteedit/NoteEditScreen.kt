package app.askya.ui.noteedit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.Composer
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.MarkdownBlocks
import app.askya.ui.components.NOTE_TITLE
import app.askya.ui.components.ScreenHeader
import app.askya.ui.components.rememberDictation
import app.askya.ui.scroll.ShareNoteDialog
import app.askya.ui.scroll.formatOf
import app.askya.ui.scroll.rememberImageImport
import app.askya.ui.scroll.shareAttachment
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted

/**
 * Заметка — карточкой, какой она станет, когда будет дописана.
 *
 * Раньше заметку записывал разговор: «Как назовём?» — «Что записать?» — «В
 * какую книгу?». Разговор хорош там, где Askya спрашивает, а человек отвечает
 * одним словом. Заметка устроена иначе: её пишут кусками, возвращаются,
 * дописывают через день. Вопрос по экрану этого не выдерживал — чтобы
 * добавить строчку, приходилось заново проходить всю анкету, а увидеть
 * написанное целиком можно было только выйдя из неё.
 *
 * Поэтому теперь на экране сразу карточка. Вверху — название, и оно выделено
 * нарочно: пока карточку не назвали, подсказка и черта под ней коралловые.
 * Название — то, чем запись потом находят на полке, и оставлять его на потом
 * значит не найти её никогда.
 *
 * Внутри — сам текст, набранный так же, как его увидят в «Библиотеке»:
 * заголовки заголовками, списки списками, принесённые картинки картинками.
 *
 * Пишут, надиктовывают и правят в одном месте — в диалоговом окне внизу
 * ([NoteComposer]). Микрофон в нём кладёт сказанное вслух теми же буквами в ту
 * же строку (см. [rememberDictation]): надиктованное почти всегда приходится
 * поправить, и попадать оно должно туда, где его правят.
 * Сам текст в карточке не редактируется: когда он был вторым полем ввода,
 * курсор мигал сразу в двух местах, и было непонятно, какое из них слушает
 * клавиатуру. Тап по абзацу поднимает его в окно — там курсор, там правка,
 * там же выделение с «вырезать». Отправка возвращает абзац на его место.
 *
 * Сохраняется по ходу дела — после каждой отправки, — а не одним разом в
 * конце: карточку дописывают неделями, и «сохранить» в такой работе лишний шаг.
 */
@Composable
fun NoteEditScreen(noteId: Long, onBack: () -> Unit, onView: (Long) -> Unit) {
    val viewModel: NoteEditViewModel = viewModel(factory = NoteEditViewModel.factory(appContainer()))
    val note by remember(noteId) { viewModel.note(noteId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val topics by viewModel.topics.collectAsStateWithLifecycle()

    // Черновик подхватывается один раз, когда база ответила: перечитывать его
    // значило бы затирать набранное каждым обновлением потока — а поток
    // обновляется теперь после каждой отправки.
    var loaded by remember(noteId) { mutableStateOf(false) }
    var title by remember(noteId) { mutableStateOf("") }
    var body by remember(noteId) { mutableStateOf("") }
    var topicId by remember(noteId) { mutableStateOf<Long?>(null) }
    // Черновик окна с курсором: поднятый на правку абзац должен открываться
    // курсором в конце, а не в начале — иначе набранное лезет перед текстом.
    var draft by remember(noteId) { mutableStateOf(TextFieldValue()) }
    // Какой абзац сейчас поднят в окно на правку. `null` — пишется новый.
    var editing by remember(noteId) { mutableStateOf<Int?>(null) }

    var attaching by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var choosingBook by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    // Отправка наружу: чем именно — спрашивает окно, а «некуда» говорит
    // отдельная записка.
    var sharing by remember { mutableStateOf(false) }
    var noShare by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    // Что было в окне до начала надиктовки: услышанное дописывается к нему, а
    // не затирает набранное руками. Промежуточный текст распознаватель
    // уточняет по ходу фразы, поэтому пишется он всегда поверх этой опоры.
    var beforeVoice by remember(noteId) { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val titleFocus = remember { FocusRequester() }
    val composerFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val toolbar = LocalTextToolbar.current

    val isFile = note?.uri != null
    // Картинку про книгу не спрашивают: «Изображения» отвязаны от «Книг» и
    // раскладываются своими альбомами — их спрашивает карточка картинки.
    val isImage = note?.isImage == true

    LaunchedEffect(note?.id) {
        val current = note ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        loaded = true
        title = current.title
        body = current.body
        topicId = current.topicId
    }

    /**
     * Записать, если есть что записывать.
     *
     * Пока запись не прочитана из базы, поля экрана пусты — и сохранять их
     * значит стереть содержимое карточки, которую ещё не успели показать.
     * Неизменённое не пишется тоже: полка сортируется по дате последней
     * правки, и «просто заглянул» не должен перекладывать карточку в начало.
     */
    fun save() {
        if (!loaded) return
        val current = note ?: return
        val next = current.copy(title = title.trim(), body = body.trim(), topicId = topicId)
        if (next.title == current.title &&
            next.body == current.body &&
            next.topicId == current.topicId
        ) {
            return
        }
        viewModel.save(next)
    }

    /**
     * Выход. Пустая карточка при этом удаляется: её завели в базе ещё до
     * открытия экрана, и без уборки каждый передуманный «плюс» оставлял бы в
     * Scroll строку без названия и текста. У файла так нельзя — он существует
     * и без единой буквы.
     *
     * Пустой считается только та карточка, которую успели прочитать из базы.
     * Без этой оговорки «назад», нажатое в первое мгновение после открытия,
     * снесло бы непустую запись: поля экрана в этот миг ещё пусты.
     */
    fun close() {
        val current = note
        when {
            // Запись ещё не прочитана — трогать её нечем: пустые поля экрана
            // не значат «карточка пуста», они значат «мы её ещё не видели».
            !loaded || current == null -> Unit
            current.uri == null && title.isBlank() && body.isBlank() -> viewModel.delete(current)
            else -> save()
        }
        onBack()
    }

    BackHandler(onBack = ::close)

    /** Дописать в карточку абзац — тем же движением и текст, и картинка. */
    fun append(text: String) {
        if (text.isBlank()) return
        val base = body.trimEnd()
        body = if (base.isEmpty()) text.trim() else "$base\n\n${text.trim()}"
        save()
    }

    /** Вернуть поправленный абзац на его место. Пустой — значит убрать вовсе. */
    fun replace(index: Int, text: String) {
        val parts = paragraphsOf(body).toMutableList()
        if (index !in parts.indices) return
        if (text.isBlank()) parts.removeAt(index) else parts[index] = text.trim()
        body = parts.joinToString("\n\n")
        save()
    }

    val dictation = rememberDictation(
        onHeard = { text, done ->
            val merged = if (beforeVoice.isEmpty()) text else "$beforeVoice $text"
            draft = TextFieldValue(merged, TextRange(merged.length))
            // Окончательное становится новой опорой: следующая фраза ляжет за
            // ним, а не вместо него.
            if (done) beforeVoice = merged
        },
        onProblem = { notice = it },
    )

    fun send() {
        // Отправка гасит микрофон: договорил — значит, надиктовал.
        if (dictation.listening) dictation.stop()
        val index = editing
        if (index == null) append(draft.text) else replace(index, draft.text)
        draft = TextFieldValue()
        editing = null
    }

    // Картинка копируется в папку Askya, а не берётся ссылкой: исходник
    // удаляют из галереи и чистят загрузки, а заметка с дырой на месте
    // картинки — это потерянная заметка.
    val pickImage = rememberImageImport(onFailed = { failed = it }) { picked ->
        append(picked.joinToString("\n\n") { "![${it.name}](${it.uri})" })
    }

    // Новая карточка открывается с названия: с него она и начинается.
    LaunchedEffect(loaded) {
        if (loaded && !isFile && title.isBlank() && body.isBlank()) {
            titleFocus.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            // Тап мимо текста убирает системную вкладку выделения: сама она
            // висит поверх страницы, пока не выделишь что-нибудь другое.
            .pointerInput(noteId) {
                detectTapGestures {
                    toolbar.hide()
                    focusManager.clearFocus()
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(
                // У карточки в шапке одна стрелка назад и ничего больше:
                // ниже стоит её собственное заглавие, и слово «Карточка» над
                // ним объясняло бы то, что и так перед глазами. У файла слово
                // остаётся — оно говорит не «это запись», а «это не запись».
                title = if (isFile) "Файл" else "",
                onNavigationClick = ::close,
                navigationIsBack = true,
                actions = {
                    if (isFile) {
                        HeaderIcon(
                            icon = Icons.Outlined.Visibility,
                            contentDescription = "Смотреть",
                            onClick = { onView(noteId) },
                        )
                    }
                    /*
                     * «Поделиться» появляется, когда есть чем: у пустой
                     * карточки, только что заведённой «плюсом», отправлять
                     * нечего, и кнопка над ней обещала бы несуществующее.
                     *
                     * Записанное перед отправкой сохраняется: наружу должно
                     * уйти то, что человек видит на экране, вместе с
                     * последним дописанным абзацем.
                     */
                    if (isFile || title.isNotBlank() || body.isNotBlank()) {
                        HeaderIcon(
                            icon = Icons.Outlined.Share,
                            contentDescription = "Поделиться",
                            onClick = {
                                save()
                                val file = note?.uri
                                if (file != null) {
                                    shareAttachment(
                                        context = context,
                                        scope = scope,
                                        uri = file,
                                        name = title.ifBlank { "документ" },
                                        mime = note?.mime.orEmpty(),
                                        onFailed = { noShare = true },
                                    )
                                } else {
                                    sharing = true
                                }
                            },
                        )
                    }
                    HeaderIcon(
                        icon = Icons.Outlined.DeleteOutline,
                        contentDescription = "Удалить",
                        onClick = { deleting = true },
                    )
                },
            )

            Card(
                shape = RoundedCornerShape(28.dp),
                // Кремовая, а не белая: это страница, а не карточка дела. Тот
                // же цвет, каким заметка показана в «Библиотеке».
                colors = CardDefaults.cardColors(containerColor = Cream),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
                    TitleField(
                        value = title,
                        onValueChange = { title = it },
                        focusRequester = titleFocus,
                        onNext = { if (!isFile) composerFocus.requestFocus() },
                    )

                    if (!isImage) {
                        BookLine(
                            book = topics.firstOrNull { it.id == topicId },
                            onClick = { choosingBook = true },
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        if (isFile) {
                            FileNote(note, onView = { onView(noteId) })
                        } else {
                            WrittenNote(
                                paragraphs = paragraphsOf(body),
                                editing = editing,
                                onEdit = { index, text ->
                                    editing = index
                                    draft = TextFieldValue(text, TextRange(text.length))
                                    composerFocus.requestFocus()
                                },
                            )
                        }
                    }

                    if (!isFile) {
                        if (editing != null) {
                            EditingLine(
                                onCancel = {
                                    editing = null
                                    draft = TextFieldValue()
                                    focusManager.clearFocus()
                                },
                            )
                        }
                        Composer(
                            draft = draft,
                            onDraftChange = { draft = it },
                            onAttach = { attaching = true },
                            onSend = ::send,
                            focusRequester = composerFocus,
                            modifier = Modifier.padding(top = 8.dp),
                            placeholder = when {
                                dictation.listening -> "Слушаю…"
                                editing != null -> "Правим абзац — пусто уберёт его"
                                else -> "Написать в карточку…"
                            },
                            // Правку абзаца можно довести до пустоты: так из
                            // карточки убирают лишнее, не открывая ничего ещё.
                            canSendEmpty = editing != null,
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
            }
        }
    }

    if (attaching) {
        AttachDialog(
            onDismiss = { attaching = false },
            onImage = {
                attaching = false
                pickImage()
            },
            onLink = {
                attaching = false
                linking = true
            },
        )
    }

    if (linking) {
        LinkDialog(
            onDismiss = { linking = false },
            onAdd = { url, label ->
                append(if (label.isBlank()) url else "[$label]($url)")
                linking = false
            },
        )
    }

    if (choosingBook) {
        BookChooser(
            topics = topics,
            chosen = topicId,
            onDismiss = { choosingBook = false },
            onPick = { picked ->
                topicId = picked
                choosingBook = false
                save()
            },
        )
    }

    if (deleting) {
        AskyaAsk(
            title = "Удалить запись?",
            text = if (isFile) {
                // Важно сказать прямо: в Scroll лежит ссылка, а не копия.
                "Ссылка на файл будет убрана из Scroll. Сам файл на телефоне останется."
            } else {
                "Карточка будет стёрта. Вернуть не получится."
            },
            confirm = "Удалить",
            onConfirm = {
                deleting = false
                note?.let(viewModel::delete)
                onBack()
            },
            onDismiss = { deleting = false },
        )
    }

    if (sharing) {
        ShareNoteDialog(
            title = title.trim(),
            body = body.trim(),
            onDismiss = { sharing = false },
            onFailed = { noShare = true },
        )
    }

    if (noShare) {
        AskyaNotice(
            title = "Некуда отправить",
            text = "На телефоне нет приложения, которое принимает такое.",
            onDismiss = { noShare = false },
            icon = Icons.Outlined.Share,
        )
    }

    notice?.let { text ->
        AskyaNotice(
            title = "Надиктовка",
            text = text,
            onDismiss = { notice = null },
            icon = Icons.Outlined.MicNone,
        )
    }

    if (failed > 0) {
        AskyaNotice(
            title = "Не добавилось",
            text = "Не удалось скопировать $failed " +
                "${plural(failed, "картинку", "картинки", "картинок")}. " +
                "Файл мог быть удалён или на телефоне кончилось место.",
            onDismiss = { failed = 0 },
        )
    }
}

/**
 * Текст карточки абзацами — теми самыми, какими его отправляли.
 *
 * Разделитель — пустая строка: ею же склеены отправленные куски. Внутри абзаца
 * переводы строк остаются, поэтому список или стихи не рассыпаются на строчки.
 */
private fun paragraphsOf(body: String): List<String> = body
    .split(Regex("\\n\\s*\\n"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }

/** Как набран текст карточки. Та же мерка, что у разметки в «Библиотеке». */
private val NOTE_BODY = TextStyle(fontSize = 16.sp, lineHeight = 25.sp)

/**
 * Заглавие карточки — и просьба его дать.
 *
 * Пока названия нет, подсказка и черта под ней коралловые, а под чертой стоит
 * строчка о том, зачем оно нужно. Названо — черта гаснет до бледной, и
 * карточка перестаёт что-либо требовать.
 *
 * Клавиша «дальше» переводит не в текст, а в строку внизу: текст набирают
 * оттуда, отправляя в карточку абзац за абзацем.
 */
@Composable
private fun TitleField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
) {
    val named = value.isNotBlank()

    Column(modifier = Modifier.fillMaxWidth()) {
        Box {
            if (value.isEmpty()) {
                Text(
                    text = "Как назовём карточку?",
                    style = NOTE_TITLE,
                    color = Accent,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = NOTE_TITLE.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { onNext() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(if (named) 1.dp else 2.dp)
                .background(if (named) AccentSoft else Accent),
        )

        if (!named) {
            Text(
                text = "По названию карточку потом найдут на полке.",
                style = MaterialTheme.typography.labelMedium,
                color = AccentInk,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Где карточка лежит: в книге или сама по себе. Тап меняет.
 *
 * Знак меняется вместе со словами: у книги — книга, у отдельной записи — лист.
 * Раскрытая книга рядом со словами «отдельным файлом» говорила ровно
 * обратное тому, что написано.
 *
 * Строкой, а не вопросом на отдельном шаге: книгу выбирают один раз и обычно
 * не думая, а вопрос «в какую книгу?» стоял поперёк дороги каждый раз, когда
 * заметку всего лишь дописывали.
 */
@Composable
private fun BookLine(book: ScrollTopic?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(top = 10.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (book == null) {
                Icons.Outlined.InsertDriveFile
            } else {
                Icons.AutoMirrored.Outlined.MenuBook
            },
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = book?.title?.let { "в книге «$it»" } ?: "отдельным файлом",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Написанное — набранным, а не исходником: так же, как заметку показывает
 * «Библиотека».
 *
 * Абзацами по отдельности, потому что тап по абзацу поднимает его в окно на
 * правку. Правящийся абзац подсвечен: иначе, глядя в окно, не вспомнить, какой
 * именно кусок сейчас в нём лежит.
 *
 * Пустая карточка вместо текста говорит, что делать: строка внизу — это не
 * поиск и не подпись, и без слов её назначение не очевидно.
 */
@Composable
private fun WrittenNote(
    paragraphs: List<String>,
    editing: Int?,
    onEdit: (index: Int, text: String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = 8.dp),
    ) {
        if (paragraphs.isEmpty()) {
            Text(
                text = "Здесь будет текст карточки.\nНапишите его в строке внизу.",
                style = NOTE_BODY,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
            return@Column
        }

        paragraphs.forEachIndexed { index, text ->
            val picked = index == editing
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (index == 0) 0.dp else 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (picked) AccentSoft else Color.Transparent)
                    .pointerInput(index, text) { detectTapGestures { onEdit(index, text) } }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                MarkdownBlocks(text)
            }
        }
    }
}

/** Что сейчас правится и как из этого выйти, ничего не тронув. */
@Composable
private fun EditingLine(onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Text(
            text = "Правим абзац",
            style = MaterialTheme.typography.labelMedium,
            color = AccentInk,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Отмена",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Вместо текста у файла — то, чем он является.
 *
 * Содержимое чужого файла карточка не переписывает: исходник лежит у системы,
 * и правится здесь только имя. Смотреть его — в просмотрщик.
 */
@Composable
private fun FileNote(note: Note?, onView: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = note?.let { formatOf(it) } ?: "ФАЙЛ",
            style = MaterialTheme.typography.titleMedium,
            color = AccentInk,
        )
        Text(
            text = "Askya хранит ссылку на файл. Здесь правится его имя — то, " +
                "под которым он лежит на полке.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp),
        )
        TextButton(onClick = onView, modifier = Modifier.padding(top = 8.dp)) {
            Text("Смотреть")
        }
    }
}

/**
 * В какую книгу положить карточку.
 *
 * Отказ от книги назван действием, а не отсутствием: «Без книги» звучало как
 * пропущенный шаг, а запись без книги — это законное место, полка «Файлы».
 */
@Composable
private fun BookChooser(
    topics: List<ScrollTopic>,
    chosen: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    AskyaDialog(
        onDismiss = onDismiss,
        badge = { DialogBadge(Icons.AutoMirrored.Outlined.MenuBook) },
    ) {
        DialogCaption("В какую книгу?")

        // Полок бывает много, а окно не должно вырастать во весь экран: список
        // книг прокручивается внутри карточки.
        Column(
            modifier = Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            BookRow(
                title = "Оставить отдельным файлом",
                picked = chosen == null,
                onClick = { onPick(null) },
            )
            topics.forEach { topic ->
                BookRow(
                    title = topic.title,
                    picked = topic.id == chosen,
                    onClick = { onPick(topic.id) },
                )
            }
        }
    }
}

@Composable
private fun BookRow(title: String, picked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (picked) AccentInk else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (picked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Русское число словом: «1 картинку», «3 картинки», «5 картинок». */
private fun plural(count: Int, one: String, few: String, many: String): String {
    if (count % 100 in 11..14) return many
    return when (count % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}
