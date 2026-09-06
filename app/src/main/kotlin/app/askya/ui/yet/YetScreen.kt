package app.askya.ui.yet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MicNone
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.EmptyState
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.MarkView
import app.askya.ui.components.NewButton
import app.askya.ui.components.SHELF_COLUMNS
import app.askya.data.repository.Trash
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.TileRow
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge

/**
 * «Списки» — подраздел Scroll. Прежнее имя, Yet, осталось в коде: маршрут и
 * таблицы внутренние, а переименование стоило бы миграции ради одного слова.
 *
 * Здесь живёт всё, что предстоит: купить, посмотреть, взять с собой, не забыть.
 * От AskyaDay это отличается тем, что у строки нет ни времени, ни места в дне:
 * она просто ждёт. Как только у неё появляется час, это уже дело, и заводить
 * его надо в расписании.
 *
 * Ведёт себя раздел как «Библиотека»: списки выложены полкой карточек по трое
 * в ряд, тап раскрывает список большой карточкой поверх полки ([YetCard]) —
 * так же, как раскрывается файл, — а долгое нажатие правит название и знак.
 * Кнопка одна, «new list»: назвали, выбрали знак — и список тут же открылся,
 * как открывается только что заведённая заметка.
 */
@Composable
fun YetScreen(onBack: () -> Unit, onOpenList: (Long) -> Unit) {
    val trash = appContainer().trash
    val viewModel: YetViewModel = viewModel(factory = YetViewModel.factory(appContainer()))
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    val sizes by viewModel.sizes.collectAsStateWithLifecycle()

    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<YetList?>(null) }
    var opened by remember { mutableStateOf<Long?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "Списки",
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = { NewButton(label = "new list", onClick = { creating = true }) },
    ) {
        val sheets = rememberLazyListState()
        FadingColumn(
            state = sheets,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (lists.isEmpty()) {
                item {
                    EmptyState(
                        title = "Списков пока нет",
                        hint = "Список — про то, что предстоит: купить, посмотреть, не забыть.",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }

            items(lists.chunked(SHELF_COLUMNS), key = { row -> row.first().id }) { row ->
                TileRow(row) { list, modifier ->
                    ListTile(
                        list = list,
                        left = remaining[list.id] ?: 0,
                        total = sizes[list.id] ?: 0,
                        onClick = { opened = list.id },
                        onLongClick = { editing = list },
                        modifier = modifier,
                    )
                }
            }
        }
    }

    // Список ищется в живом перечне: переименовали — карточка показывает новое
    // имя, удалили — закрывается сама.
    opened?.let { id ->
        val list = lists.firstOrNull { it.id == id }
        if (list == null) {
            opened = null
        } else {
            val items by remember(id) { viewModel.items(id) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

            YetCard(
                list = list,
                items = items,
                onDismiss = { opened = null },
                onToggle = viewModel::toggle,
                onRemoveRow = { row ->
                    viewModel.removeItem(row.id)
                    trash.remembered(Trash.Kind.YET_ROW, row.id)
                },
                onAdd = { viewModel.addLines(id, it) },
                onEdit = { editing = list },
                onClearDone = { viewModel.clearDone(id) },
                onOpenFull = {
                    // Карточка закрывается перед уходом: иначе «назад» с
                    // полного экрана возвращал бы в неё же.
                    opened = null
                    onOpenList(id)
                },
                onProblem = { notice = it },
            )
        }
    }

    if (creating) {
        ListDialog(
            list = null,
            onDismiss = { creating = false },
            onConfirm = { title, mark ->
                creating = false
                // Заведённый список сразу открывается — как новая заметка в
                // Библиотеке открывается на своей странице, а не оставляет
                // человека смотреть на полку и искать, что он только что завёл.
                viewModel.addList(title, mark) { id -> opened = id }
            },
        )
    }

    editing?.let { list ->
        ListDialog(
            list = list,
            onDismiss = { editing = null },
            onConfirm = { title, mark ->
                viewModel.updateList(list, title, mark)
                editing = null
            },
            onDelete = {
                viewModel.deleteList(list.id)
                editing = null
                opened = null
            },
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
}

/**
 * Высота карточки списка.
 *
 * Вытянута вниз, а не квадратная: в ряд их встаёт трое, и при равных стороне и
 * ширине карточка читалась бы плиткой значка. Вытянутая — это карточка, тот же
 * прямоугольник, что дело в дне и книга на полке, только уже.
 */
private val LIST_HEIGHT = 186.dp

/**
 * Список карточкой: знак сверху, название и сколько в нём осталось.
 *
 * Знак вместо цветного корешка книги — то, чем список узнают на полке: у одних
 * квадраты, у других галочки, и глазу есть за что зацепиться раньше, чем он
 * прочитает название. Он же говорит, чем внутри отмечают.
 *
 * Внизу не «сколько всего», а сколько осталось: список нужен ради того, что
 * ещё не сделано. Знак показан нетронутым — в карточке отмечать нечего, она
 * открывает список, а не правит его.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListTile(
    list: YetList,
    left: Int,
    total: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Та же тень, что у книги и у карточки дела: полка, день и списки —
        // один слой над страницей.
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(18.dp))
            .height(LIST_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            MarkView(mark = list.mark, done = false)
            Text(
                text = list.title.ifBlank { "Без названия" },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp).weight(1f),
            )
            Text(
                text = countLine(left = left, total = total),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
            )
        }
    }
}

/**
 * Строка под названием: сколько в списке осталось.
 *
 * «3 из 12», а не «ещё 3»: цифра рядом с общим числом сразу говорит, много ли
 * позади, — а слово «ещё» стояло в разделе на каждой карточке и в каждой шапке
 * и перестало что-либо значить.
 */
internal fun countLine(left: Int, total: Int): String = when {
    total == 0 -> "Пусто"
    left == 0 -> "Всё сделано"
    else -> "$left из $total"
}
