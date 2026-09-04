package app.askya.ui.scroll

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.ui.components.formatRussianDate
import app.askya.ui.components.highlighted
import app.askya.ui.components.snippet
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge

/** Высота карточки книги. Ниже карточки дела: на полке их куда больше. */
private val BOOK_HEIGHT = 132.dp

/**
 * Высота карточки файла — заметно меньше книжной.
 *
 * Меньше намеренно: книга это папка, внутри которой ещё что-то есть, а файл —
 * одна запись. Равные по величине карточки уравнивали бы полку и то, что на
 * ней лежит, и глазу не за что было бы зацепиться.
 */
private val FILE_HEIGHT = 92.dp

/**
 * Книга карточкой: цветной корешок слева, название и сколько внутри.
 *
 * Полоса — то, чем книгу узнают на полке: одинаковые белые карточки с
 * названиями читаются как список. Цвет выбирают в карточке книги, а пока не
 * выбрали — он выводится из названия (см. [spineColor]).
 *
 * Тап открывает книгу, долгое нажатие правит.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookTile(
    book: ScrollTopic,
    count: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlight: String = "",
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Та же тень, что у карточки дела: полка и день — один слой над страницей.
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(18.dp))
            .height(BOOK_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(7.dp)
                    .fillMaxHeight()
                    .background(spineColor(book)),
            )
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Text(
                    text = highlighted(book.title.ifBlank { "Без названия" }, highlight),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (count == 0) {
                        "Пусто"
                    } else {
                        "$count ${plural(count, "запись", "записи", "записей")}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                )
            }
        }
    }
}

/**
 * Запись карточкой: метка формата у файла, название и дата.
 *
 * Общая у полки и у книги: в книге лежат те же записи, что и на полке, и
 * различаться видом они не должны.
 *
 * Тап раскрывает запись ([FileCard]), долгое нажатие удаляет: удаление редкое,
 * и отдельная кнопка на каждой карточке занимала бы место ради того, чем
 * пользуются раз в месяц.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTile(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Тень мельче книжной: карточка меньше, и шесть точек под ней
        // выглядели бы подложенной книгой.
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(14.dp))
            .height(FILE_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            formatOf(note)?.let { badge ->
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = AccentInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(AccentSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                text = note.title.ifBlank { if (note.uri == null) "Без названия" else "Файл" },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp).weight(1f),
            )
            Text(
                text = formatRussianDate(note.updatedAt.toLocalDate()),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Голосовая заметка карточкой — той же, что и запись рядом с ней.
 *
 * Голос лежал строкой во всю ширину, когда всё остальное в Scroll лежало
 * карточками, и читался из-за этого как чужой список: полоса от края до края —
 * форма списка дел, а не полки. Теперь он выложен той же сеткой ([TileRow]),
 * той же высоты ([FILE_HEIGHT]) и с теми же полями, что файл: заметка голосом —
 * такая же запись, и отличать её видом карточки не за что.
 *
 * Отличается она одним — кружком «играть» на месте метки формата: у файла там
 * стоит слово «PDF», а здесь главное действие карточки. Звучащая заметка
 * держит кружок закрашенным и меняет знак на паузу — по нему и видно, которая
 * из них сейчас играет, даже если карточку плеера отвели глазами.
 *
 * Тап играет, долгое нажатие открывает карточку заметки — там её называют и
 * оттуда убирают. Ровно так же разложены действия у файла на полке: частое —
 * на тап, редкое — на долгое нажатие, и отдельной кнопки на карточке нет.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VoiceTile(
    note: Note,
    sounding: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(14.dp))
            .height(FILE_HEIGHT)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (sounding) Accent else AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Пауза" else "Слушать",
                        tint = if (sounding) MaterialTheme.colorScheme.surface else AccentInk,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = formatClock(note.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
            }
            Text(
                text = note.title.ifBlank { "Голос" },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp).weight(1f),
            )
            Text(
                text = formatRussianDate(note.createdAt.toLocalDate()),
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
            )
        }
    }
}

/**
 * Найденная запись — строкой во всю ширину, а не карточкой в ряду из трёх.
 *
 * Полка выложена карточками, потому что на ней узнают своё в лицо; найденное
 * читают. В карточку шириной в треть экрана не помещается ни целое название,
 * ни строчка, из-за которой запись нашлась, — а именно она и отвечает на
 * вопрос «почему это здесь».
 *
 * Искомое отмечено маркером и в названии, и в куске текста вокруг совпадения.
 * Раньше поиск честно находил запись по слову из середины и показывал её
 * названием, в котором этого слова нет; открыв её, человек искал глазами
 * заново — то же самое, от чего поиск и должен избавлять.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchResultRow(
    note: Note,
    query: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Кусок текста показывается, только если совпало именно в тексте: у файла
    // текста нет вовсе, а у заметки совпасть могло и одно название.
    val inBody = note.body.contains(query.trim(), ignoreCase = true)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .cardEdge(RoundedCornerShape(14.dp))
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                formatOf(note)?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(AccentSoft)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Text(
                    text = highlighted(
                        text = note.title.ifBlank { if (note.uri == null) "Без названия" else "Файл" },
                        query = query,
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (formatOf(note) != null) 8.dp else 0.dp),
                )
                Text(
                    text = formatRussianDate(note.updatedAt.toLocalDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Muted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (inBody) {
                Text(
                    text = highlighted(snippet(note.body, query), query),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Строка поиска по полке.
 *
 * Ищется по всему, что в библиотеке лежит, — и по отдельным файлам, и по тем,
 * что убраны в книги. Человек помнит название записи, а не то, в какую книгу
 * он её положил; поиск, который не заглядывает внутрь книг, заставлял бы
 * открывать их по очереди.
 *
 * Своим полем, а не `OutlinedTextField`: у того рамка и плавающая подпись
 * чужого оформления, а здесь нужна та же белая таблетка, что и карточки
 * раздела.
 */
@Composable
internal fun ShelfSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(20.dp),
        )
        Box(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            if (query.isEmpty()) {
                Text(
                    text = "Поиск по библиотеке",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Крестик появляется только при наборе: пустое поле стирать нечем.
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Очистить",
                    tint = Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Подходит ли запись под то, что ищут.
 *
 * Ищется и по названию, и по тексту заметки: половину записей человек помнит
 * не заголовком, а строчкой из середины. У файла текста нет — там остаётся имя.
 */
internal fun matches(note: Note, query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return note.title.contains(needle, ignoreCase = true) ||
        note.body.contains(needle, ignoreCase = true)
}
