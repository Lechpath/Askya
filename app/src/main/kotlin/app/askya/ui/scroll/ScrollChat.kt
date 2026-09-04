package app.askya.ui.scroll

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.askya.data.entity.Note
import app.askya.data.entity.ScrollTopic
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.CardWhite
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.cardShade
import app.askya.ui.yet.countLine
import kotlin.math.absoluteValue

/**
 * Из чего собрана лента Scroll: сообщения, ответы, карточки и строка внизу.
 *
 * Вынесено из [ScrollScreen] потому, что экран отвечает за одно — какие
 * разделы, что в них попало и что нашлось, — а здесь лежит то, чем это
 * нарисовано. Держать в одном файле и то и другое значило бы листать полторы
 * тысячи строк, чтобы поправить радиус угла.
 */

// ---------------------------------------------------------------------------
// Сообщения и ответы
// ---------------------------------------------------------------------------

/**
 * Название раздела — сообщением человека: справа, плашкой акцента, с уголком,
 * повёрнутым к своей стороне.
 *
 * Тап открывает раздел целиком. Это и есть смысл сообщения: человек называет
 * то, что хочет увидеть, и ответ ниже — короткий; полный ответ живёт на
 * отдельном экране.
 */
@Composable
internal fun SectionBubble(@DrawableRes icon: Int, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(SAID)
                .background(AccentSoft)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AccentInk,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AccentInk,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * Спрошенное в поиске — тем же сообщением человека, что и название раздела:
 * это и есть его вопрос, а всё ниже — ответ на него.
 *
 * Крестик внутри плашки, а не отдельной кнопкой: сброс поиска — это «забери
 * назад моё сообщение», и стоять он должен на самом сообщении.
 */
@Composable
internal fun AskedBubble(text: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(SAID)
                .background(AccentSoft)
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = AccentInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Сбросить поиск",
                tint = AccentInk,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(4.dp)
                    .size(16.dp),
            )
        }
    }
}

/**
 * Ответ приложения: подложка слева, уголком к своей стороне.
 *
 * Не карточкой: карточки лежат **внутри** ответа, и вторая рамка вокруг них
 * читалась бы как карточка в карточке. Подложка — это лист, на котором
 * приложение своё разложило.
 *
 * Лист белый, а не кремовый: кремовым он сливался с бумагой экрана, и ответ
 * терял края. Лежащее на нём отделено тенью ([cardShade]) — тем же приёмом,
 * которым в Askya приподняты все карточки.
 */
@Composable
internal fun AnswerPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 18.dp, bottom = 6.dp)
            .clip(HEARD)
            .background(CardWhite)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        content()
    }
}

/**
 * Название раздела над найденным — подписью, а не сообщением человека.
 *
 * В поиске человек спросил слово, а не раздел, и ставить «Галерея» его
 * сообщением значило бы приписывать ему то, чего он не говорил. Поэтому
 * название стоит на стороне отвечающего и приглушённым голосом: это не вопрос,
 * а пометка, откуда взялось найденное.
 */
@Composable
internal fun AnswerLabel(
    @DrawableRes icon: Int,
    title: String,
    count: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = Muted,
            modifier = Modifier.padding(start = 6.dp),
        )
        Text(
            text = "· $count",
            style = MaterialTheme.typography.labelLarge,
            color = Accent,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** В разделе пока ничего. Ответ всё равно есть — и говорит, чего ждать. */
@Composable
internal fun AnswerEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Muted,
        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
    )
}

/**
 * Подножие ответа: недосказанное слева, вход в раздел справа.
 *
 * Три точки — то, чем в переписке обозначают «это не всё», и читаются они
 * раньше, чем прочитано число рядом. Стрелка стоит всегда, даже когда
 * показано всё: раздел открывается не только затем, чтобы увидеть остаток, —
 * там его правят, раскладывают и убирают.
 */
@Composable
internal fun AnswerFoot(more: Int, onOpen: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        if (more > 0) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .padding(start = if (index == 0) 4.dp else 3.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Muted),
                )
            }
            Text(
                text = "ещё $more",
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Box(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = "Открыть раздел",
            tint = AccentInk,
            modifier = Modifier
                .clip(CircleShape)
                .background(AccentSoft)
                .clickable(onClick = onOpen)
                .padding(5.dp)
                .size(18.dp),
        )
    }
}

/** Не нашлось ничего — одним ответом, а не четырьмя пустыми разделами. */
@Composable
internal fun NothingFound() {
    AnswerPanel {
        Text(
            text = "Ничего не нашлось. Попробуйте другое слово — или тег с решёткой, " +
                "если помечали запись.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            modifier = Modifier.padding(2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// «Галерея»
// ---------------------------------------------------------------------------

/**
 * Миниатюры, повторяющие формат снимка: у лежачего лежачая, у стоячего
 * стоячая. Формат читается из заголовка файла ([rememberImageAspects]) — то
 * есть до раскладки, иначе столбцы переезжали бы по мере чтения снимков.
 *
 * Углы скруглены у всех, и это не украшение: миниатюры стоят вплотную и
 * разного роста, и прямые углы слипались бы в одно пятно.
 */
@Composable
internal fun GalleryAnswer(notes: List<Note>, onOpen: (Long) -> Unit) {
    val aspects = rememberImageAspects(notes.mapNotNull { it.uri })
    // Высота — величина, обратная формату: чем шире снимок, тем ниже плитка.
    val piles = spread(notes, COLUMNS) { note -> 1f / aspectOf(note, aspects) }

    Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.fillMaxWidth()) {
        piles.forEach { pile ->
            Column(
                verticalArrangement = Arrangement.spacedBy(GAP),
                modifier = Modifier.weight(1f),
            ) {
                pile.forEach { note ->
                    Thumb(
                        note = note,
                        aspect = aspectOf(note, aspects),
                        onClick = { onOpen(note.id) },
                    )
                }
            }
        }
    }
}

/**
 * Формат снимка, обрезанный по краям разумного.
 *
 * Панорама один к шести дала бы полоску в три миллиметра высотой, а снимок
 * экрана телефона — плитку выше самого ответа. Обрезанное показывается
 * заполнением: середина панорамы честнее её же полоски.
 *
 * Пока формат не прочитан — квадрат: он не обещает ни лежачего снимка, ни
 * стоячего, и переезд из квадрата в настоящий формат самый короткий.
 */
private fun aspectOf(note: Note, aspects: Map<String, Float>): Float {
    val known = note.uri?.let { aspects[it] } ?: return 1f
    return known.coerceIn(FLAT, TALL)
}

private const val FLAT = 0.58f
private const val TALL = 1.75f

/** Одна миниатюра. Не прочиталась — вместо неё имя файла: доступ могли отозвать. */
@Composable
private fun Thumb(note: Note, aspect: Float, onClick: () -> Unit) {
    val bitmap = note.uri?.let { rememberThumbnail(it, targetPx = 320) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .cardShade(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = note.title.ifBlank { "Картинка" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = note.title.ifBlank { "Файл" },
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// «Библиотека»
// ---------------------------------------------------------------------------

/** Что стоит на полке: книга или отдельная запись. */
internal sealed interface Shelved {
    class Book(val book: ScrollTopic) : Shelved
    class Record(val note: Note) : Shelved
}

/**
 * Полка вперемешку: книга, запись, книга, запись.
 *
 * Через одну, а не сперва все книги: ответ должен показывать обе половины
 * раздела, а две книги подряд с записями под ними читались бы как два разных
 * ответа, слипшихся в один. Чего меньше — того и меньше; остаток добирает
 * второе.
 */
internal fun shelfOf(
    books: List<ScrollTopic>,
    records: List<Note>,
    limit: Int,
): List<Shelved> {
    val forBooks = books.take(maxOf(limit / 2, limit - records.size))
    val forRecords = records.take(limit - forBooks.size)

    val out = mutableListOf<Shelved>()
    var book = 0
    var record = 0
    while (book < forBooks.size || record < forRecords.size) {
        if (book < forBooks.size) out += Shelved.Book(forBooks[book++])
        if (record < forRecords.size) out += Shelved.Record(forRecords[record++])
    }
    return out
}

/**
 * Книги и записи двумя столбцами разной высоты.
 *
 * Книга выше записи: книга — это папка, внутри которой ещё что-то есть, и
 * уравнивать её с одной записью значило бы стирать разницу между полкой и тем,
 * что на ней лежит. Запись тем выше, чем больше в ней написано, — в карточку
 * влезают первые слова, и высота идёт от них.
 */
@Composable
internal fun LibraryAnswer(
    items: List<Shelved>,
    onOpenBook: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onViewFile: (Long) -> Unit,
) {
    val piles = spread(items, COLUMNS) { shelvedHeight(it).value }

    Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.fillMaxWidth()) {
        piles.forEach { pile ->
            Column(
                verticalArrangement = Arrangement.spacedBy(GAP),
                modifier = Modifier.weight(1f),
            ) {
                pile.forEach { item ->
                    when (item) {
                        is Shelved.Book -> BookCard(
                            book = item.book,
                            height = shelvedHeight(item),
                            onClick = { onOpenBook(item.book.id) },
                        )

                        is Shelved.Record -> RecordCard(
                            note = item.note,
                            height = shelvedHeight(item),
                            onClick = {
                                // У файла открывают содержимое, у заметки —
                                // саму заметку: файл читают, заметку правят.
                                if (item.note.uri == null) {
                                    onOpenNote(item.note.id)
                                } else {
                                    onViewFile(item.note.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun shelvedHeight(item: Shelved): Dp = when (item) {
    is Shelved.Book -> if (item.book.title.length > 16) 168.dp else 140.dp
    is Shelved.Record -> when {
        item.note.body.length > 140 -> 152.dp
        item.note.body.isNotBlank() -> 124.dp
        else -> 96.dp
    }
}

/**
 * Книга — цветом корешка и названием.
 *
 * Цветом во всю карточку, а не полоской сбоку: на полке книгу узнают по цвету
 * раньше, чем прочитают название (см. [spineColor]), и в ленте, где карточка
 * величиной с ноготь, это единственное, что успевает сработать. Название
 * поверх краски белым — восемь красок палитры выбраны так, что белое читается
 * на каждой.
 */
@Composable
private fun BookCard(book: ScrollTopic, height: Dp, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .cardShade(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(spineColor(book))
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(
            text = book.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Запись — названием и первыми словами.
 *
 * У файла вместо первых слов метка формата: `MD` и `PDF` человек различает
 * мгновенно, а текста у файла нет вовсе. У ссылки показывается сама ссылка:
 * заголовок «Статья» без адреса не помогает вспомнить, что за статья.
 */
@Composable
private fun RecordCard(note: Note, height: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .cardShade(RoundedCornerShape(16.dp))
            .cardEdge(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        formatOf(note)?.let { badge ->
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = AccentInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AccentSoft)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            text = note.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        firstWords(note)?.let { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Первые слова записи: адрес у ссылки, начало текста у заметки. */
private fun firstWords(note: Note): String? {
    val body = note.body.trim()
    if (body.isEmpty()) return null
    LINK.find(body)?.let { return it.value }

    val flat = body.replace(WHITE, " ").trim()
    val words = flat.split(' ')
    return if (words.size <= WORDS) flat else words.take(WORDS).joinToString(" ") + "…"
}

/** Сколько слов из записи влезает в карточку. */
private const val WORDS = 14

private val LINK = Regex("""https?://\S+""")
private val WHITE = Regex("""\s+""")

// ---------------------------------------------------------------------------
// «Списки»
// ---------------------------------------------------------------------------

/**
 * Списки карточками разной высоты — только стоячими.
 *
 * Высота идёт от того, сколько пунктов в карточку влезло: список из двух строк
 * и список из двадцати одинаковой карточкой выглядели бы одинаково, а разница
 * между ними — ровно то, что человек хочет увидеть, не открывая список.
 *
 * Пункты — не сделанные вперёд: в списке «ещё» смотрят на то, что ещё
 * предстоит. В поиске вперёд выходят те, в которых нашлось искомое: список
 * нашёлся по строчке из середины, и показывать вместо неё первые две значило бы
 * заставить искать глазами заново.
 */
@Composable
internal fun ListsAnswer(
    lists: List<YetList>,
    items: Map<Long, List<YetItem>>,
    words: List<String>,
    onOpen: (Long) -> Unit,
) {
    val shown = lists.map { list ->
        val all = items[list.id].orEmpty()
        Listed(
            list = list,
            lines = linesOf(all, words),
            left = all.count { !it.done },
            total = all.size,
        )
    }
    val piles = spread(shown, COLUMNS) { listedHeight(it).value }

    Row(horizontalArrangement = Arrangement.spacedBy(GAP), modifier = Modifier.fillMaxWidth()) {
        piles.forEach { pile ->
            Column(
                verticalArrangement = Arrangement.spacedBy(GAP),
                modifier = Modifier.weight(1f),
            ) {
                pile.forEach { listed ->
                    ListCard(
                        listed = listed,
                        height = listedHeight(listed),
                        onClick = { onOpen(listed.list.id) },
                    )
                }
            }
        }
    }
}

/** Список, готовый к показу: сам он, его первые пункты и сколько осталось. */
private class Listed(
    val list: YetList,
    val lines: List<YetItem>,
    val left: Int,
    val total: Int,
)

/** Какие пункты попадут в карточку: найденные вперёд, затем ещё не сделанные. */
private fun linesOf(items: List<YetItem>, words: List<String>): List<YetItem> {
    if (words.isEmpty()) return items.take(LINES)
    val hit = items.filter { item ->
        val text = item.text.lowercase()
        words.any { text.contains(it) }
    }
    return (hit + items.filterNot { it in hit }).take(LINES)
}

/** Сколько пунктов влезает в карточку списка. */
private const val LINES = 4

private fun listedHeight(listed: Listed): Dp = (86 + 20 * listed.lines.size).dp

@Composable
private fun ListCard(listed: Listed, height: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .cardShade(RoundedCornerShape(16.dp))
            .cardEdge(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Text(
            text = listed.list.title.ifBlank { "Без названия" },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.weight(1f).padding(top = 6.dp),
        ) {
            listed.lines.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (item.done) Muted else Accent),
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.done) Muted else Ink,
                        // Сделанное вычеркнуто, а не убрано: вычеркнутая
                        // строка — это ещё и память о том, что сделано.
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }

        Text(
            // Та же строка, что под названием списка на его полке: «3 из 12».
            text = countLine(left = listed.left, total = listed.total),
            style = MaterialTheme.typography.labelSmall,
            color = if (listed.left == 0) Muted else Accent,
        )
    }
}

// ---------------------------------------------------------------------------
// «Голос»
// ---------------------------------------------------------------------------

/**
 * Голосовые заметки — плашками воспроизведения разной длины.
 *
 * Не карточками: у заметки, которую слушают, нет содержимого, которое можно
 * показать глазами, — есть только длина. Поэтому плашка тем длиннее, чем
 * длиннее запись: так голосовое сообщение выглядит в любой переписке, и
 * человек читает «это на полминуты» не считая цифр.
 *
 * Слушается не уходя из ленты: заметка звучит поверх того, что открыто, и
 * карточка плеера уходит сама, когда она кончилась (см.
 * [app.askya.echo.EchoAside]). Нажатие на играющую — пауза, а не «начни
 * сначала».
 */
@Composable
internal fun VoiceAnswer(
    notes: List<Note>,
    sounding: Long?,
    playing: Boolean,
    onPlay: (Note) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        notes.forEach { note ->
            VoicePlaque(
                note = note,
                sounding = sounding == note.id,
                playing = sounding == note.id && playing,
                onClick = { onPlay(note) },
            )
        }
    }
}

@Composable
private fun VoicePlaque(
    note: Note,
    sounding: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .cardShade(RoundedCornerShape(22.dp))
                .cardEdge(RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 7.dp, vertical = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (sounding) Accent else AccentSoft),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Пауза" else "Слушать",
                    tint = if (sounding) MaterialTheme.colorScheme.surface else AccentInk,
                    modifier = Modifier.size(18.dp),
                )
            }

            Wave(
                seed = note.id,
                bars = barsFor(note.durationMs),
                lit = sounding,
                modifier = Modifier.padding(horizontal = 8.dp),
            )

            Text(
                text = formatClock(note.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = Muted,
                maxLines = 1,
                modifier = Modifier.padding(end = 4.dp),
            )
        }

        // Имя под плашкой, а не в ней: плашка тем и говорит, что она про длину,
        // и вписанное внутрь имя ломало бы эту мерку. А имя нужно — по нему
        // заметку и находят, когда их становится тридцать.
        Text(
            text = note.title.ifBlank { "Голос" },
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )
    }
}

/**
 * Сколько полосок в дорожке — по длине записи.
 *
 * Два часа в плашку не влезут ни при какой мерке, поэтому шкала кончается на
 * двух минутах: заметки длиннее наговаривают редко, а до неё разница между
 * семью секундами и минутой видна с одного взгляда.
 */
private fun barsFor(durationMs: Long): Int {
    val part = (durationMs / VOICE_FULL).coerceIn(0f, 1f)
    return (BARS_MIN + (BARS_MAX - BARS_MIN) * part).toInt()
}

private const val VOICE_FULL = 120_000f
private const val BARS_MIN = 6
private const val BARS_MAX = 26

/**
 * Дорожка звука полосками.
 *
 * Полоски не настоящие: громкость по всей записи пришлось бы прочитать целиком,
 * а плашек в ленте четыре. Но и не случайные — они выведены из номера заметки,
 * поэтому от запуска к запуску одинаковы: плашка, меняющая рисунок при каждой
 * перерисовке, читалась бы как помеха, а не как звук.
 */
@Composable
private fun Wave(seed: Long, bars: Int, lit: Boolean, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.height(22.dp),
    ) {
        repeat(bars) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight(seed, index))
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (lit) Accent else AccentSoft),
            )
        }
    }
}

private fun barHeight(seed: Long, index: Int): Dp {
    val mixed = ((seed % 997) * 37 + index * 61) % 89
    return (6 + mixed.absoluteValue % 15).toInt().dp
}

// ---------------------------------------------------------------------------
// Строка внизу
// ---------------------------------------------------------------------------

/**
 * Строка поиска — то же окно, в которое в Askya пишут.
 *
 * Не «строка поиска» с лупой в шапке, а именно окно сообщения внизу: в Askya
 * везде пишут снизу, и раздел, где то же самое делается сверху и по-другому,
 * пришлось бы объяснять.
 *
 * Ищется по ходу набора, а не по нажатию: лента пересобирается на каждой букве,
 * и это и есть ответ. Стрелка поэтому не «искать», а «договорил» — она убирает
 * клавиатуру, чтобы ответ было видно целиком. Крестик рядом забирает вопрос
 * назад.
 */
@Composable
internal fun SearchLine(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val typed = draft.text.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .cardEdge(RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Box(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            if (draft.text.isEmpty()) {
                Text(
                    text = "Найти — словом или #тегом",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Muted,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                cursorBrush = SolidColor(Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp)
                    .focusRequester(focusRequester),
            )
        }

        if (typed) {
            RoundButton(
                icon = Icons.Outlined.Close,
                description = "Сбросить поиск",
                background = Cream,
                tint = Ink,
                onClick = onClear,
            )
        }

        RoundButton(
            icon = if (typed) Icons.Outlined.ArrowUpward else Icons.Outlined.Search,
            description = if (typed) "Договорил" else "Искать",
            background = if (typed) Ink else Cream,
            tint = if (typed) Accent else Muted,
            modifier = Modifier.padding(start = 6.dp),
            onClick = { if (typed) onSend() else focusRequester.requestFocus() },
        )
    }
}

/** Круглая кнопка в строке — та же, что в окне записи. */
@Composable
private fun RoundButton(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Раскладка
// ---------------------------------------------------------------------------

/**
 * Раскладывает карточки по столбцам так, чтобы ряды не выравнивались.
 *
 * Каждая следующая ложится в тот столбец, который на эту минуту короче. Так
 * получается лента без швов: карточки разной высоты, положенные рядами,
 * оставляли бы под низкой пустоту до конца ряда — и лента читалась бы как
 * таблица с дырками.
 *
 * Порядок внутри столбца сохраняется — свежее сверху, как и во всех разделах
 * Scroll. Своей раскладки вместо `LazyVerticalStaggeredGrid` не от упрямства:
 * сетка внутри прокручиваемой ленты требует заданной высоты, а вся мысль
 * здесь в том, что высота у карточек разная.
 */
private fun <T> spread(items: List<T>, columns: Int, height: (T) -> Float): List<List<T>> {
    val piles = List(columns) { mutableListOf<T>() }
    val filled = FloatArray(columns)

    items.forEach { item ->
        var pick = 0
        for (index in 1 until columns) {
            if (filled[index] < filled[pick] - 0.01f) pick = index
        }
        piles[pick] += item
        filled[pick] += height(item)
    }

    return piles.map { it.toList() }
}

/** Сколько столбцов в ответе и какая щель между карточками. */
private const val COLUMNS = 2
private val GAP = 8.dp

/** Сказанное человеком: уголок повёрнут к его стороне — вправо и вниз. */
private val SAID = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 6.dp,
)

/** Ответ приложения: уголок к его стороне — влево и вверх. */
private val HEARD = RoundedCornerShape(
    topStart = 6.dp,
    topEnd = 20.dp,
    bottomStart = 20.dp,
    bottomEnd = 20.dp,
)
