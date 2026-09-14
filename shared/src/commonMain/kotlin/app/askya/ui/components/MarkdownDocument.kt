package app.askya.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.domain.markdown.Links
import app.askya.domain.markdown.MdAlign
import app.askya.domain.markdown.MdBlock
import app.askya.domain.markdown.Markdown
import app.askya.domain.model.ListMark
import app.askya.ui.scroll.rememberThumbnail
import app.askya.ui.theme.Accent
import app.askya.ui.theme.CoralAccent
import app.askya.ui.theme.CoralInk
import app.askya.ui.theme.CoralSoft
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Ink
import app.askya.ui.theme.ModeGreenSoft
import app.askya.ui.theme.Muted

/**
 * Заметка, показанная как разметка: заголовки заголовками, списки списками,
 * код в тёмной плашке, таблица таблицей.
 *
 * Читают заметку чаще, чем пишут, поэтому открывается она набранной, а не
 * исходником со звёздочками и решётками. Исходник остаётся тем, что человек
 * правит, — «Переписать» показывает его как есть.
 *
 * Разбор — `domain/markdown/Markdown.kt`, здесь только вид. Палитра общая с
 * приложением: кремовая страница, коралловый акцент, плашки цвета `AccentSoft`.
 *
 * Текст выделяется и копируется: долгое нажатие берёт слово, ручки растягивают
 * выделение на кусок, дальше системное «Копировать». Записанное часто нужно
 * унести наружу — в сообщение, в письмо, — и до сих пор из показанной заметки
 * нельзя было взять ни строчки. Выделение живёт внутри страницы: то, что
 * успело сложиться в ленивом списке, выделяется целиком, а не по абзацу.
 */
@Composable
fun MarkdownDocument(
    source: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    highlight: String = "",
) {
    val blocks = remember(source) { Markdown.parse(source) }

    // Размер держит внешняя коробка, а не SelectionContainer: обёртка выделения
    // не пропускает наружу `weight` — родитель перестаёт видеть, что страницу
    // просили занять остаток места, и в карточке она либо съёживается до высоты
    // текста, либо вытесняет кнопки за нижний край.
    CompositionLocalProvider(LocalHighlight provides highlight) {
        Box(modifier = modifier) {
            SelectionContainer {
                FadingColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
                    items(blocks.size) { index ->
                        BlockView(blocks[index], previous = blocks.getOrNull(index - 1))
                    }
                }
            }
        }
    }
}

/**
 * Та же разметка, но обычной колонкой — без ленивого списка и без выделения.
 *
 * Нужна там, где страница лежит внутри чужой прокрутки и должна отдавать тап
 * наружу: в карточке заметки текст показан набранным, а тап по нему включает
 * правку. Ленивый список внутри прокрутки не помещается вовсе, а обёртка
 * выделения перехватывала бы этот тап себе.
 */
@Composable
fun MarkdownBlocks(source: String, modifier: Modifier = Modifier) {
    val blocks = remember(source) { Markdown.parse(source) }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            BlockView(block, previous = blocks.getOrNull(index - 1))
        }
    }
}

@Composable
private fun BlockView(block: MdBlock, previous: MdBlock?) {
    // Отступ сверху зависит от того, что было до: заголовок после текста
    // должен отделяться заметно, а подпись под ним — нет.
    val top = when {
        previous == null -> 0.dp
        block is MdBlock.Heading && block.level == 1 -> 28.dp
        block is MdBlock.Heading -> 24.dp
        previous is MdBlock.Heading -> 8.dp
        else -> 14.dp
    }

    Column(modifier = Modifier.padding(top = top)) {
        when (block) {
            is MdBlock.Heading -> HeadingView(block)
            is MdBlock.Paragraph -> Text(
                text = marked(block.text),
                style = BODY,
                color = Ink,
            )

            is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.items.forEach { item ->
                    MarkedRow(marker = "•", markerColor = Accent, nested = item.nested) {
                        Text(marked(item.text), style = BODY, color = Ink)
                    }
                }
            }

            is MdBlock.Numbers -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.items.forEachIndexed { index, item ->
                    MarkedRow(marker = "${index + 1}.", markerColor = Muted, nested = item.nested) {
                        Text(marked(item.text), style = BODY, color = Ink)
                    }
                }
            }

            is MdBlock.Tasks -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                block.items.forEach { task ->
                    MarkdownTask(task.text, task.done, nested = task.nested)
                }
            }

            is MdBlock.Quote -> QuoteView(block.text)
            is MdBlock.Code -> CodeView(block.text)
            is MdBlock.Table -> TableView(block)
            is MdBlock.Picture -> PictureView(block.alt, block.src)
            is MdBlock.Footnote -> FootnoteView(block.mark, block.text)
            MdBlock.Rule -> Spacer(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AccentSoft),
            )
        }
    }
}

@Composable
private fun HeadingView(block: MdBlock.Heading) {
    val size = when (block.level) {
        1 -> 30.sp
        2 -> 24.sp
        3 -> 19.sp
        else -> 17.sp
    }
    Text(
        text = marked(block.text),
        style = BODY.copy(
            fontFamily = FontFamily.Serif,
            fontSize = size,
            lineHeight = size * 1.25f,
            fontWeight = if (block.level >= 3) FontWeight.SemiBold else FontWeight.Normal,
        ),
        color = Ink,
    )
}

/** Строка с меткой слева и висячим отступом: перенос не заезжает под метку. */
@Composable
private fun MarkedRow(
    marker: String,
    markerColor: androidx.compose.ui.graphics.Color,
    nested: Boolean,
    content: @Composable () -> Unit,
) {
    Row(modifier = Modifier.padding(start = if (nested) 22.dp else 0.dp)) {
        Text(
            text = marker,
            style = BODY,
            color = markerColor,
            modifier = Modifier.width(24.dp),
        )
        content()
    }
}

/**
 * Строка чек-листа. Сделанное отмечено коралловым знаком — тем же цветом, что
 * у выполненного дела в AskyaDay.
 *
 * Живёт здесь, а не в чек-листе заметки: строку списка в Scroll рисует она же.
 * Отмеченное человек видит в приложении одним и тем же знаком, где бы он это
 * ни отмечал, — иначе два вида одного и того же приходится узнавать заново.
 *
 * Форма знака ([mark]) своя у каждого списка Scroll; в заметке она всегда
 * квадратная — там чек-лист один на все заметки, и выбирать в нём нечего.
 */
@Composable
internal fun MarkdownTask(
    text: String,
    done: Boolean,
    nested: Boolean = false,
    mark: ListMark = ListMark.SQUARE,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier.padding(start = if (nested) 22.dp else 0.dp),
    ) {
        MarkView(mark = mark, done = done, modifier = Modifier.padding(top = 2.dp, end = 10.dp))
        Text(
            text = marked(text),
            style = BODY,
            color = if (done) Muted else Ink,
        )
    }
}

/**
 * Знак строки: квадрат, кружок, точка или галочка.
 *
 * Все четыре занимают одно и то же место в 20 точек, даже точка: строки в
 * списке должны стоять в одну колонку, а не съезжать по величине знака.
 *
 * Цвет один на все виды — серый, пока не отмечено, и коралловый, когда
 * отмечено. Форму человек выбирает под список, но «сделано» во всём приложении
 * значит один и тот же цвет.
 */
@Composable
internal fun MarkView(mark: ListMark, done: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(MARK_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        when (mark) {
            ListMark.SQUARE -> Box(
                modifier = Modifier
                    .size(MARK_SIZE)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (done) Accent else AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Сделано",
                        tint = Cream,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            // Кружок заливается целиком, а не обводится: пустой кружок с
            // тонкой линией теряется на кремовой странице.
            ListMark.CIRCLE -> Box(
                modifier = Modifier
                    .size(MARK_SIZE)
                    .clip(CircleShape)
                    .background(if (done) Accent else AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Сделано",
                        tint = Cream,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            // Точка мельче прочих знаков: это перечень, в котором отмечают
            // редко, и колонка крупных знаков спорила бы с текстом.
            ListMark.DOT -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (done) Accent else AccentSoft),
            )

            ListMark.TICK -> Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = if (done) "Сделано" else null,
                tint = if (done) Accent else AccentSoft,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Место под знак. Одинаковое у всех четырёх — иначе строки идут лесенкой. */
private val MARK_SIZE = 20.dp

/** Цитата — зелёная плашка: то, что процитировано, а не сказано автором. */
@Composable
private fun QuoteView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ModeGreenSoft)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(text = marked(text), style = BODY, color = Ink)
    }
}

/**
 * Блок кода — тёмная плашка со своей прокруткой вбок: код переносить нельзя,
 * перенос меняет смысл строки.
 */
@Composable
private fun CodeView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = text,
            style = MONO.copy(color = Cream),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * Таблица: шапка капителью и коралловая черта под ней, строки без линеек.
 * Линейки в каждой клетке превращают таблицу в решётку, а читают в ней всё
 * равно строки.
 */
@Composable
private fun TableView(table: MdBlock.Table) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            table.head.forEachIndexed { index, cell ->
                Text(
                    text = cell.uppercase(),
                    style = BODY.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = AccentInk,
                    textAlign = alignOf(table.aligns.getOrNull(index)),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Accent),
        )
        table.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                row.forEachIndexed { index, cell ->
                    Text(
                        text = marked(cell),
                        style = BODY,
                        color = Ink,
                        textAlign = alignOf(table.aligns.getOrNull(index)),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun alignOf(align: MdAlign?): TextAlign = when (align) {
    MdAlign.END -> TextAlign.End
    MdAlign.CENTER -> TextAlign.Center
    else -> TextAlign.Start
}

/**
 * Картинка.
 *
 * Своя — та, что человек принёс в заметку кнопкой «+», — показывается
 * рисунком: она лежит в папке Askya, ссылка на неё полная, и читается тем же
 * кодом, что превью в разделе.
 *
 * Чужая — из принесённого `.md` — остаётся подписью: путь там относительный и
 * указывает в папку, куда у приложения доступа нет. Подпись хотя бы говорит,
 * что здесь должно быть; пустое место не сказало бы ничего.
 */
@Composable
private fun PictureView(alt: String, src: String) {
    val own = src.startsWith("content://") || src.startsWith("file://")
    val bitmap = if (own) rememberThumbnail(src, targetPx = 1024) else null

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = alt.ifBlank { "Изображение" },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        return
    }

    Text(
        // Своя картинка, которая ещё читается или уже не прочиталась, тоже
        // подписью: пустая полоса на её месте выглядела бы поломкой страницы.
        text = alt.ifBlank { "Изображение" },
        style = BODY.copy(fontStyle = FontStyle.Italic),
        color = Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun FootnoteView(mark: String, text: String) {
    Row {
        Text(
            text = mark,
            style = BODY.copy(fontSize = 11.sp, baselineShift = BaselineShift.Superscript),
            color = Accent,
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(text = marked(text), style = BODY.copy(fontSize = 14.sp), color = Muted)
    }
}

/**
 * Строка текста, готовая к показу: разобранная разметка плюс подсветка того,
 * что сейчас ищут ([LocalHighlight]).
 *
 * Вся набранная разметка идёт через неё, а не через [inline] напрямую: искомое
 * слово должно светиться и в заголовке, и в пункте списка, и в ячейке
 * таблицы — человек ищет по всей записи, а не по её абзацам.
 */
@Composable
private fun marked(source: String): AnnotatedString {
    val needle = LocalHighlight.current
    val parsed = inline(source, rememberLinkOpener())
    return if (needle.isBlank()) parsed else parsed.highlighted(needle)
}

/**
 * Разбор внутристрочной разметки: жирное, курсив, зачёркнутое, код, ссылки и
 * ссылки на сноски.
 *
 * Разбор посимвольный, одним проходом: набор регулярных выражений на вложенных
 * парах звёздочек разъезжается, а проход по символам ошибается предсказуемо.
 *
 * [onLink] — что делать с нажатой ссылкой. Без него ссылка остаётся видом:
 * подчёркнутой и коралловой, но неживой. Так она и выглядит в разборе без
 * экрана — в тестах.
 *
 * Краски здесь сами по себе, а не роли темы: разметка размечается вне
 * композиции — и в тестах, где темы нет вовсе, — и спросить о выбранной гамме
 * отсюда некого. Коралловая ссылка и коралловая плашка кода остаются
 * коралловыми при любой гамме; это три места на всё приложение.
 */
internal fun inline(
    source: String,
    onLink: LinkInteractionListener? = null,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    val text = source

    fun take(marker: String): String? {
        if (!text.startsWith(marker, i)) return null
        val end = text.indexOf(marker, i + marker.length)
        if (end < 0) return null
        val inner = text.substring(i + marker.length, end)
        if (inner.isEmpty()) return null
        i = end + marker.length
        return inner
    }

    // Ссылка — не только краска: нажатая, она уводит на страницу. Адрес
    // приписывается к тексту меткой, и по ней Compose зовёт [onLink].
    fun link(label: String, url: String) {
        if (onLink == null || url.isBlank()) {
            withStyle(LINK) { append(label) }
            return
        }
        withLink(
            LinkAnnotation.Url(
                url = Links.web(url),
                styles = TextLinkStyles(style = LINK),
                linkInteractionListener = onLink,
            ),
        ) {
            append(label)
        }
    }

    while (i < text.length) {
        // Голый адрес: скопированная в браузере строка, вставленная в запись
        // как есть. Она — самый частый способ положить ссылку в заметку, и
        // подписывать её ради того, чтобы она заработала, никто не обязан.
        val bare = Links.at(text, i)
        if (bare != null) {
            link(bare, bare)
            i += bare.length
            continue
        }

        // Ссылка: [текст](адрес). Показывается текст — адрес читателю не нужен.
        if (text[i] == '[' && !text.startsWith("[^", i)) {
            val close = text.indexOf(']', i)
            if (close > 0 && close + 1 < text.length && text[close + 1] == '(') {
                val paren = text.indexOf(')', close)
                if (paren > 0) {
                    link(text.substring(i + 1, close), text.substring(close + 2, paren))
                    i = paren + 1
                    continue
                }
            }
        }

        // Ссылка на сноску: [^1] — номером над строкой.
        if (text.startsWith("[^", i)) {
            val close = text.indexOf(']', i)
            if (close > 0) {
                withStyle(
                    SpanStyle(
                        color = CoralAccent,
                        baselineShift = BaselineShift.Superscript,
                        fontSize = 11.sp,
                    ),
                ) {
                    append(text.substring(i + 2, close))
                }
                i = close + 1
                continue
            }
        }

        val bold = take("**")
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            continue
        }
        val struck = take("~~")
        if (struck != null) {
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(struck) }
            continue
        }
        val code = take("`")
        if (code != null) {
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = CoralSoft,
                    color = CoralInk,
                    fontSize = 14.sp,
                ),
            ) {
                append(code)
            }
            continue
        }
        val italic = take("*") ?: take("_")
        if (italic != null) {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
            continue
        }

        append(text[i])
        i++
    }
}

/**
 * Вид ссылки: коралловым и с чертой — единственная краска, зовущая нажать.
 *
 * Сама краска, а не роль темы: разметка размечается вне композиции, и
 * выбранную гамму читать отсюда нечем.
 */
private val LINK = SpanStyle(color = CoralAccent, textDecoration = TextDecoration.Underline)

internal val BODY = androidx.compose.ui.text.TextStyle(
    fontSize = 16.sp,
    lineHeight = 25.sp,
)

private val MONO = androidx.compose.ui.text.TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 22.sp,
)
