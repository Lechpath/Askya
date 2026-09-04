package app.askya.ui.scroll

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.R
import app.askya.app.appContainer
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.fadingEdges

/**
 * Scroll — оглавление записанного, собранное разговором.
 *
 * ## Почему лента, а не хаб из четырёх окон
 *
 * До этого раздел был хабом: четыре окна во весь рост, одно на экран, выбор
 * прокруткой, и в каждом окне знак, название и три карточки в лицо. Окно
 * отвечало на вопрос «что это за подраздел», но не на тот, с которым в Scroll
 * приходят, — «где то, что я записал». Чтобы увидеть картинки и книги разом,
 * приходилось листать; чтобы дойти до голоса — листать четыре раза.
 *
 * Теперь то же самое читается сверху вниз одной лентой, устроенной как
 * разговор. С одной стороны — названия разделов, как сообщения человека: он
 * спрашивает «Галерея», «Библиотека». С другой — то, что в разделе лежит, как
 * ответ приложения. Ничего не выбирая, человек видит всё записанное разом, а
 * ответ приложения — не подпись «6 записей», а сами записи в лицо.
 *
 * Разговор здесь не украшение и не новый приём: в Askya записывают именно
 * разговором — карточка заметки, строка списка, трата в Ledger добавляются
 * сообщением в окно внизу (см. [app.askya.ui.components.Composer]). Scroll был
 * единственным местом, где записанное потом **читали** иначе, чем писали.
 *
 * ## Что показывает ответ
 *
 * У каждого раздела свой способ показать своё, и это не прихоть раскладки:
 * картинку узнают в лицо, книгу — по цвету корешка, список — по тому, что в
 * нём осталось, голос — по длине.
 *
 * - «Галерея» — миниатюры, повторяющие формат снимка: у лежачего лежачая, у
 *   стоячего стоячая (см. [rememberImageAspects]). Одинаковые квадратики
 *   резали бы ровно то, по чему картинку и узнают.
 * - «Библиотека» — книги цветом корешка и названием, записи — названием и
 *   первыми словами.
 * - «Списки» — карточки разной высоты: в карточку вписаны первые пункты, и
 *   высота идёт от того, сколько их.
 * - «Голос» — плашки воспроизведения разной длины: длина плашки — длина
 *   записи, как у голосового сообщения в переписке.
 *
 * Карточки лежат в два столбца и разной высоты, поэтому ряды не выравниваются
 * (см. [spread]). Это нарочно: ровная сетка читается как таблица, которую
 * просматривают по столбцам, а лента — как то, по чему скользят глазами.
 *
 * ## Строка внизу
 *
 * Та же, в которую в Askya пишут, только пишут в неё поиск. Ищется по словам и
 * по тегам (слово с решёткой — тег), сразу по всему записанному: по названиям,
 * по тексту, по пунктам списков. Найденным лента пересобирается — те же
 * разделы, но в них только то, что нашлось; в чём не нашлось ничего, того в
 * ленте нет вовсе.
 *
 * Теги живут в самой записи ([app.askya.ui.components.TagsLine]): книга — это
 * место, где запись лежит, а тег — слово, по которому её ищут, и таких слов у
 * записи бывает сколько угодно.
 */
@Composable
fun ScrollScreen(
    onOpenMenu: () -> Unit,
    onOpenImages: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLists: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onViewFile: (Long) -> Unit,
    onViewImage: (Long) -> Unit,
    onOpenList: (Long) -> Unit,
) {
    val container = appContainer()
    val viewModel: ScrollViewModel = viewModel(factory = ScrollViewModel.factory(container))

    val images by viewModel.images.collectAsStateWithLifecycle()
    val loose by viewModel.loose.collectAsStateWithLifecycle()
    val shelf by viewModel.shelf.collectAsStateWithLifecycle()
    val books by viewModel.topics.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val listItems by viewModel.listItems.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()

    // Что звучит сейчас: плашка играющей заметки показывает пуск паузой.
    val aside by container.echoAside.state.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf(TextFieldValue()) }
    val ask = remember(draft.text) { askOf(draft.text) }
    val searching = !ask.empty

    val feed = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalFocusManager.current

    // Найденное показывается сверху: лента пересобралась, и смотреть на её
    // старую середину незачем. Прокрутка мгновенная, а не плавная: под
    // набираемым словом лента меняется на каждой букве, и поехавший экран
    // читался бы как дрожь.
    LaunchedEffect(searching) { feed.scrollToItem(0) }

    /** «Галерея» — только найденное; вне поиска первые несколько снимков. */
    val shownImages = if (searching) images.filter { it.matches(ask) } else images
    // В поиске ищется и то, что убрано в книги: человек помнит название
    // записи, а не книгу, в которую он её положил. Вне поиска показываются
    // лежащие отдельно — книги стоят рядом своими карточками.
    val shownRecords = if (searching) {
        shelf.filterNot { it.voice }.filter { it.matches(ask) }
    } else {
        loose
    }
    val shownBooks = if (searching) books.filter { it.matches(ask) } else books
    val shownLists = if (searching) {
        lists.filter { list -> matches(list, listItems[list.id].orEmpty(), ask) }
    } else {
        lists
    }
    val shownVoices = if (searching) voices.filter { it.matches(ask) } else voices

    val sections = listOf(
        Section(
            icon = R.drawable.ic_scroll_images,
            title = "Галерея",
            empty = "Пока пусто. Здесь появятся картинки, которые вы сюда положите.",
            total = shownImages.size,
            shown = if (searching) FOUND else GALLERY_SHOWN,
            onOpen = onOpenImages,
            answer = { limit ->
                GalleryAnswer(
                    notes = shownImages.take(limit),
                    onOpen = onViewImage,
                )
            },
        ),
        Section(
            icon = R.drawable.ic_scroll_library,
            title = "Библиотека",
            empty = "Пока пусто. Здесь встанут книги и записи, которые в них лежат.",
            total = shownBooks.size + shownRecords.size,
            shown = if (searching) FOUND else SHELF_SHOWN,
            onOpen = onOpenLibrary,
            answer = { limit ->
                LibraryAnswer(
                    items = shelfOf(shownBooks, shownRecords, limit),
                    onOpenBook = onOpenBook,
                    onOpenNote = onOpenNote,
                    onViewFile = onViewFile,
                )
            },
        ),
        Section(
            icon = R.drawable.ic_scroll_lists,
            title = "Списки",
            empty = "Пока пусто. Здесь будет то, что ещё предстоит.",
            total = shownLists.size,
            shown = if (searching) FOUND else LISTS_SHOWN,
            onOpen = onOpenLists,
            answer = { limit ->
                ListsAnswer(
                    lists = shownLists.take(limit),
                    items = listItems,
                    words = ask.words,
                    onOpen = onOpenList,
                )
            },
        ),
        Section(
            icon = R.drawable.ic_scroll_voice,
            title = "Голос",
            empty = "Пока пусто. Здесь лягут заметки, которые проще сказать, чем набрать.",
            total = shownVoices.size,
            shown = if (searching) FOUND else VOICE_SHOWN,
            onOpen = onOpenVoice,
            answer = { limit ->
                VoiceAnswer(
                    notes = shownVoices.take(limit),
                    sounding = aside?.noteId,
                    playing = aside?.playing == true,
                    onPlay = { note -> container.echoAside.play(note) },
                )
            },
        ),
    )

    // В поиске пустые разделы из ленты уходят: «Галерея — ничего» четырьмя
    // строками подряд не ответ, а список того, чего не спрашивали.
    val visible = if (searching) sections.filter { it.total > 0 } else sections

    ScreenScaffold(title = "Scroll", onNavigationClick = onOpenMenu) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                state = feed,
                modifier = Modifier.fillMaxWidth().weight(1f).fadingEdges(feed),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (searching) {
                    // Спрошенное — сообщением человека: оно и есть тот вопрос,
                    // на который лента ниже отвечает.
                    item(key = "asked") {
                        AskedBubble(
                            text = draft.text.trim(),
                            onClear = { draft = TextFieldValue() },
                        )
                    }
                }

                if (searching && visible.isEmpty()) {
                    item(key = "nothing") { NothingFound() }
                }

                visible.forEach { section ->
                    if (!searching) {
                        item(key = "ask-${section.title}") {
                            SectionBubble(
                                icon = section.icon,
                                title = section.title,
                                onClick = section.onOpen,
                            )
                        }
                    }
                    item(key = "answer-${section.title}") {
                        SectionAnswer(
                            section = section,
                            // В поиске раздел не спрашивали — его название
                            // стоит подписью над найденным, на стороне
                            // отвечающего, а не сообщением человека.
                            labelled = searching,
                        )
                    }
                }

                // Место под строкой: последний ответ не должен упираться в неё.
                item(key = "tail") { Box(modifier = Modifier.padding(bottom = 6.dp)) }
            }

            SearchLine(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = { keyboard.clearFocus() },
                onClear = { draft = TextFieldValue() },
                focusRequester = focus,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Раздел в ленте: чем он подписан и чем отвечает.
 *
 * [shown] — сколько записей влезает в ответ. Ответ не должен быть самим
 * разделом: он показывает, что там лежит, а не всё, что там лежит, — иначе
 * лента из четырёх разделов становится четырьмя разделами подряд, и до голоса
 * не докрутить.
 *
 * [answer] получает этот потолок, а не готовый срез, потому что «Библиотека»
 * делит его между книгами и записями сама.
 */
private class Section(
    @DrawableRes val icon: Int,
    val title: String,
    val empty: String,
    val total: Int,
    val shown: Int,
    val onOpen: () -> Unit,
    val answer: @Composable (limit: Int) -> Unit,
)

/** Сколько записей показывает ответ раздела. */
private const val GALLERY_SHOWN = 6
private const val SHELF_SHOWN = 6
private const val LISTS_SHOWN = 4
private const val VOICE_SHOWN = 4

/**
 * Сколько показывает найденное.
 *
 * Больше, чем обычный ответ: найденное — это и есть то, за чем пришли, и
 * прятать его за «ещё 8» значило бы искать дважды.
 */
private const val FOUND = 12

/**
 * Ответ раздела: подпись (в поиске), содержимое и подножие.
 *
 * Подножие говорит две вещи: что показанное не всё («ещё 12») и что раздел
 * открывается целиком (стрелка). Многоточием, а не одним числом: три точки на
 * краю ответа — это то, чем в переписке обозначают недосказанное, и читаются
 * они раньше, чем прочитано число.
 */
@Composable
private fun SectionAnswer(section: Section, labelled: Boolean) {
    AnswerPanel {
        if (labelled) {
            AnswerLabel(
                icon = section.icon,
                title = section.title,
                count = section.total,
                onClick = section.onOpen,
            )
        }

        if (section.total == 0) {
            AnswerEmpty(section.empty)
        } else {
            section.answer(section.shown)
            AnswerFoot(
                more = section.total - section.shown,
                onOpen = section.onOpen,
            )
        }
    }
}

/**
 * Что спросили и что нашлось — вопрос человека, разобранный на слова и теги.
 *
 * Слово с решёткой — тег: так их пишут везде, и объяснять это отдельной
 * кнопкой «искать по тегу» не нужно. Остальные слова ищутся по всему, что у
 * записи есть буквами, теги в том числе: набравший «дача» без решётки имел в
 * виду и запись про дачу, и запись, помеченную «дача».
 *
 * Слова требуются все: два слова в строке — это уточнение, а не «или».
 */
private class Ask(val words: List<String>, val tags: List<String>) {
    val empty: Boolean get() = words.isEmpty() && tags.isEmpty()
}

private fun askOf(query: String): Ask {
    val parts = query.trim().split(WHITESPACE).filter { it.isNotBlank() }
    return Ask(
        words = parts.filterNot { it.startsWith("#") }.map { it.lowercase() },
        tags = parts.filter { it.startsWith("#") && it.length > 1 }
            .map { it.drop(1).lowercase() },
    )
}

private val WHITESPACE = Regex("\\s+")

/** Запись подходит, если в ней нашлось каждое слово и каждый тег. */
private fun Note.matches(ask: Ask): Boolean {
    val hay = buildString {
        append(title.lowercase())
        append('\n')
        append(body.lowercase())
        append('\n')
        append(tags.joinToString(" ").lowercase())
    }
    if (!ask.words.all { hay.contains(it) }) return false
    return ask.tags.all { needle -> tags.any { it.lowercase().contains(needle) } }
}

/**
 * Книга подходит по названию. Тегов у книги нет и не будет: тег — свойство
 * записи, а книга — место, куда её положили, и помечать словами саму полку
 * значило бы завести второй способ делать то же самое.
 */
private fun ScrollTopic.matches(ask: Ask): Boolean =
    ask.tags.isEmpty() && ask.words.all { title.lowercase().contains(it) }

/** Список подходит по названию или по любому своему пункту. */
private fun matches(list: YetList, items: List<YetItem>, ask: Ask): Boolean {
    if (ask.tags.isNotEmpty()) return false
    val hay = (list.title + "\n" + items.joinToString("\n") { it.text }).lowercase()
    return ask.words.all { hay.contains(it) }
}

/** Русское число словом: «1 книга», «3 книги», «5 книг». */
internal fun plural(count: Int, one: String, few: String, many: String): String {
    if (count % 100 in 11..14) return many
    return when (count % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}
