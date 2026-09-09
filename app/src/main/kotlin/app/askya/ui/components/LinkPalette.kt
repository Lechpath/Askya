package app.askya.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk

/**
 * Одна строка выбора привязки: чем делается дело.
 *
 * [value] — то, что ляжет в колонку (`book:12`); группа — раздел, из которого
 * эта строка взята. Карточка о базе ничего не знает и знать не должна: список
 * ей приносит экран, который её открыл.
 */
data class LinkChoice(val value: String, val title: String, val group: String)

/**
 * Выбор привязки внутри раскрытой карточки — отступление в сторону, как выбор
 * знака и выбор мелодии.
 *
 * Списком, а не рядом слов: книг и заметок бывает под сотню, и в ряд они не
 * лягут. Группами по разделам, потому что человек помнит не «как называется»,
 * а «где лежит»: сперва ищет раздел глазами, потом строку в нём.
 *
 * Первой строкой — «Без привязки». Убрать привязку должно быть так же легко,
 * как поставить: если снять её можно только повторным тапом по выбранному,
 * человек этого не находит.
 */
@Composable
fun LinkPalette(
    chosen: String?,
    choices: List<LinkChoice>,
    onPick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Порядок групп задан порядком в списке, а не алфавитом: разделы стоят в
    // том порядке, в каком они стоят в меню, и переставлять их здесь значило
    // бы завести второй порядок для тех же пяти слов.
    val groups = choices.groupBy { it.group }

    FadingColumn(modifier = modifier.fillMaxWidth()) {
        item(key = "none") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPick(null) }
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Без привязки",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        groups.forEach { (group, rows) ->
            item(key = "group-$group") {
                Text(
                    text = group,
                    fontFamily = FontFamily.Serif,
                    fontSize = 17.sp,
                    color = AccentInk,
                    modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 4.dp),
                )
            }
            items(rows, key = { it.value }) { row ->
                val picked = row.value == chosen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (picked) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface,
                        )
                        .clickable { onPick(row.value) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(end = 10.dp),
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
        }

        if (choices.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Привязывать пока не к чему: заведите книгу, список или " +
                        "запись, и они появятся здесь.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/**
 * Строка привязки в карточке: чем делается дело и куда по ней уходят.
 *
 * Показывается всегда, когда карточка её умеет, а не только когда привязка
 * есть: пустая строка «Чем делается» — это приглашение, а без неё привязку
 * никто не найдёт. Тап по строке открывает выбор, тап по слову справа —
 * уходит туда, куда она ведёт.
 */
@Composable
fun LinkLine(
    label: String,
    dimmed: Boolean,
    onPick: () -> Unit,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val faded = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label.ifBlank { "Чем делается" },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    dimmed -> faded.copy(alpha = 0.5f)
                    label.isBlank() -> faded
                    else -> AccentInk
                },
            )
        }
        if (onOpen != null && !dimmed) {
            Text(
                text = "Перейти",
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Список того, к чему вообще можно привязать дело.
 *
 * Собирается из уже написанных разделов и в том же порядке, в каком они стоят
 * в меню: книги и записи Scroll, списки Yet, два раздела целиком. Своей
 * таблицы у выбора нет — он и есть содержимое приложения, а вторая его копия
 * разошлась бы с первой в тот же день.
 *
 * У AskyaEcho и AskyaV адреса нет, и это не упрощение: оба сами помнят, где
 * человек остановился, — плеер продолжает последнюю песню, AskyaV встаёт на
 * месте остановки. Привязка к разделу и означает «продолжить там, где бросил».
 * Формат пары «вид + адрес» при этом оставляет место для `echo:5`, когда у
 * плеера появится вход снаружи.
 *
 * Картинки в список не идут: к ним привязывать дело незачем, а их в разделе
 * бывают сотни, и они утопили бы всё остальное.
 */
@Composable
fun rememberLinkChoices(): List<LinkChoice> {
    val container = appContainer()

    val topics by remember(container) { container.noteRepository.topics() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val notes by remember(container) { container.noteRepository.notes() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val lists by remember(container) { container.yetRepository.lists() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    return remember(topics, notes, lists) {
        buildList {
            topics.forEach { topic ->
                add(
                    LinkChoice(
                        value = DeedLink(LinkKind.BOOK, topic.id).store(),
                        title = topic.title.ifBlank { "Без названия" },
                        group = "Книги",
                    ),
                )
            }
            notes.filterNot { it.isImage }.forEach { note ->
                add(
                    LinkChoice(
                        value = DeedLink(LinkKind.NOTE, note.id).store(),
                        title = note.title.ifBlank { "Без названия" },
                        group = "Записи",
                    ),
                )
            }
            lists.forEach { list ->
                add(
                    LinkChoice(
                        value = DeedLink(LinkKind.YET, list.id).store(),
                        title = list.title.ifBlank { "Без названия" },
                        group = "Списки Yet",
                    ),
                )
            }
            listOf(LinkKind.ECHO, LinkKind.VIDEO).forEach { kind ->
                add(
                    LinkChoice(
                        value = DeedLink(kind).store(),
                        title = kind.title,
                        group = "Разделы",
                    ),
                )
            }
        }
    }
}
