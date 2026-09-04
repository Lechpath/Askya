package app.askya.ui.scroll

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.data.entity.Note
import app.askya.domain.docs.DocFormat
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaNotice
import app.askya.ui.components.HeaderIcon
import app.askya.ui.components.MarkdownDocument
import app.askya.ui.components.NOTE_TITLE
import app.askya.ui.components.highlighted
import app.askya.ui.components.formatRussianDate
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Cream
import app.askya.ui.theme.Muted
import app.askya.ui.theme.cardEdge

/**
 * Файл, раскрытый карточкой почти во весь экран.
 *
 * Не отдельный экран, а карточка поверх «Библиотеки»: файл читают и
 * закрывают, а не уходят в него. Уход экраном убирает из виду полку, с которой
 * файл сняли, — а после чтения человек возвращается к ней же.
 *
 * Почти во весь экран, а не в половину, как карточка дела: у дела внутри
 * строка времени и строка названия, а здесь страница текста, и на половине
 * экрана она превратилась бы в щель для подглядывания.
 *
 * Показывается разметкой, как `.md`: заголовки заголовками, списки списками.
 * И своя заметка, и приложенный `.md` для того и написаны, чтобы читать их
 * набранными, а не исходником со звёздочками и решётками.
 *
 * Картинок здесь не бывает: у них свой раздел. Остальное — pdf, doc, чужие
 * форматы — карточка показать не берётся и отдаёт их просмотру во весь экран:
 * страницы pdf в карточку не помещаются, а врать про формат нельзя.
 *
 * [highlight] — то, что искали, когда запись нашли: искомое слово светится и в
 * имени файла, и в самом тексте. Открыть найденное и заново глазами искать в
 * нём ту самую строчку — работа, которую поиск и должен был избавить делать.
 */
@Composable
fun FileCard(
    note: Note,
    onDismiss: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onOpenFull: (Long) -> Unit,
    highlight: String = "",
) {
    BackHandler(onBack = onDismiss)

    val toolbar = LocalTextToolbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Отправка наружу: у заметки спрашивается, чем именно, у файла — нечего
    // спрашивать, он уходит собой.
    var sharing by remember { mutableStateOf(false) }
    var noShare by remember { mutableStateOf(false) }

    // Появление: без переключения флага animateFloatAsState стартовал бы уже
    // в цели и не анимировал ничего.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(200),
        label = "scrim",
    )
    // Карточка вырастает из строки списка, а не проявляется на её месте — тем
    // же движением, что и карточка дела в AskyaDay.
    val grow by animateFloatAsState(
        targetValue = if (shown) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 340f),
        label = "grow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(scrim)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            // Тап мимо карточки — закрыть. Без indication: рябь во весь экран
            // выглядела бы дико.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            // Кремовая, а не белая: это страница, а не карточка дела. Тот же
            // цвет, каким набрана разметка в полноэкранном просмотре.
            colors = CardDefaults.cardColors(containerColor = Cream),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .graphicsLayer {
                    scaleX = grow
                    scaleY = grow
                }
                .cardEdge(RoundedCornerShape(28.dp))
                // Тап по самой карточке не закрывает её: иначе чтение
                // обрывалось бы от промаха мимо строки. Но вкладку выделения
                // он убирает: системная, она висит поверх текста и сама не
                // уходит, пока не выделишь что-нибудь другое.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { toolbar.hide() },
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Head(note = note, highlight = highlight, onClose = onDismiss)
                Page(note = note, highlight = highlight, modifier = Modifier.weight(1f))
                Feet(
                    note = note,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onOpenFull = onOpenFull,
                    onShare = {
                        val file = note.uri
                        if (file == null) {
                            sharing = true
                        } else {
                            shareAttachment(
                                context = context,
                                scope = scope,
                                uri = file,
                                name = note.title,
                                mime = note.mime,
                                onFailed = { noShare = true },
                            )
                        }
                    },
                )
            }
        }

        if (sharing) {
            ShareNoteDialog(
                title = note.title,
                body = note.body,
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
    }
}

/**
 * Шапка карточки: имя файла, под ним формат и дата, справа крестик.
 *
 * Крестик нужен, хотя закрывают и тапом мимо, и «назад»: карточка занимает
 * почти весь экран, и промахнуться мимо неё почти негде.
 */
@Composable
private fun Head(note: Note, highlight: String, onClose: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlighted(
                        text = note.title.ifBlank {
                            if (note.uri == null) "Без названия" else "Файл"
                        },
                        query = highlight,
                    ),
                    // Той же меркой, что и в окне записи: сохранённая запись
                    // должна выглядеть так же, как её писали, а titleMedium
                    // рядом с текстом в 16sp читался не заглавием, а первой
                    // строкой самого текста.
                    style = NOTE_TITLE,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    formatOf(note)?.let { badge ->
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall
                                .copy(fontWeight = FontWeight.Bold),
                            color = AccentInk,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentSoft)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = formatRussianDate(note.updatedAt.toLocalDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Muted,
                    )
                }
            }
            HeaderIcon(
                icon = Icons.Outlined.Close,
                contentDescription = "Закрыть",
                onClick = onClose,
            )
        }

        // Черта под шапкой: страница начинается ниже, и без неё имя файла
        // читалось бы первой строкой самого текста.
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(AccentSoft),
        )
    }
}

/** Сама страница: разметка своей заметки, разметка `.md` — либо отказ. */
@Composable
private fun Page(note: Note, highlight: String, modifier: Modifier = Modifier) {
    val uri = note.uri
    val padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp)

    when {
        uri == null -> if (note.body.isBlank()) {
            Blank("В заметке пока ничего не написано.", modifier)
        } else {
            MarkdownDocument(
                source = note.body,
                modifier = modifier,
                contentPadding = padding,
                highlight = highlight,
            )
        }

        isTextFile(note) -> when (val text = rememberTextFile(uri)) {
            null -> Blank("Читаю…", modifier)
            "" -> Blank("Прочитать не вышло — файл удалили или отозвали доступ.", modifier)
            else -> MarkdownDocument(
                source = text,
                modifier = modifier,
                contentPadding = padding,
                highlight = highlight,
            )
        }

        // Word и Excel читаются прямо в карточке: внутри у них текст и
        // таблицы — ровно то, что карточка и показывает.
        formatOfFile(note) == DocFormat.WORD || formatOfFile(note) == DocFormat.EXCEL ->
            when (val text = rememberOfficeText(uri, formatOfFile(note))) {
                null -> Blank("Читаю…", modifier)
                "" -> Blank(
                    "Прочитать не вышло. Старые .doc и .xls Askya не читает — " +
                        "это другой формат, не тот, что docx.",
                    modifier,
                )
                else -> MarkdownDocument(
                    source = text,
                    modifier = modifier,
                    contentPadding = padding,
                    highlight = highlight,
                )
            }

        // Книгу карточка не показывает нарочно: у неё главы и оглавление, и
        // читают её не в окошке поверх полки, а в читалке во весь экран.
        formatOfFile(note) == DocFormat.BOOK -> Blank(
            text = "Книга. Открывается читалкой во весь экран: оглавление, кегль, " +
                "ночная страница и место, на котором остановились.",
            modifier = modifier,
        )

        else -> Blank(
            text = "Askya читает здесь разметку и текст. Этот формат " +
                "(${note.mime.ifEmpty { "неизвестный" }}) открывается во весь экран.",
            modifier = modifier,
        )
    }
}

/** Строчка вместо страницы: ждём, не прочли или показывать нечем. */
@Composable
private fun Blank(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Что с записью можно сделать, не выходя из карточки.
 *
 * Правка есть только у своей заметки: у приложенного файла исходник лежит у
 * системы, и переписывать его отсюда нечем. «Во весь экран» — наоборот, только
 * у файла: своей заметке уходить некуда, она и так набрана целиком, а pdf
 * показывает один полноэкранный просмотр.
 *
 * «Поделиться» есть у всего: и заметку, и файл выносят наружу — в переписку,
 * в почту, в чужое приложение. Стоит рядом с правкой, а не у «удалить»: это
 * обычное действие, а не опасное.
 *
 * «Удалить» есть у всего и стоит последним, отдельно от остальных: удаление
 * необратимо, и промах пальцем не должен попадать в него по дороге к правке.
 * Спрашивает подтверждение не карточка, а экран под ней — тот же вопрос, что и
 * при долгом нажатии на строку.
 */
@Composable
private fun Feet(
    note: Note,
    onEdit: (Long) -> Unit,
    onDelete: () -> Unit,
    onOpenFull: (Long) -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (note.uri == null) {
            ActionButton(
                icon = Icons.Outlined.EditNote,
                label = "Редактировать",
                onClick = { onEdit(note.id) },
            )
        } else {
            ActionButton(
                icon = Icons.Outlined.OpenInFull,
                label = if (formatOfFile(note) == DocFormat.BOOK) "Читать" else "Во весь экран",
                // Форматам, которые карточка не показывает, это единственный
                // выход — он и выделен.
                accent = !isTextFile(note),
                onClick = { onOpenFull(note.id) },
            )
        }

        ActionButton(
            icon = Icons.Outlined.Share,
            label = "Поделиться",
            onClick = onShare,
        )

        Spacer(modifier = Modifier.weight(1f))

        ActionButton(
            icon = Icons.Outlined.DeleteOutline,
            label = "Удалить",
            color = MaterialTheme.colorScheme.error,
            onClick = onDelete,
        )
    }
}
