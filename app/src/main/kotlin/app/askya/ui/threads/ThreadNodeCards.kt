package app.askya.ui.threads

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.askya.data.entity.ThreadNode
import app.askya.data.repository.ThreadWeb
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import app.askya.domain.model.ThreadNodeKind
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogCaption
import app.askya.ui.components.DialogField
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.LinkChoice
import app.askya.ui.components.LinkPalette
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.components.formatRussianDate
import app.askya.ui.components.rememberLinkChoices
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted

/**
 * Окна узла: выбор типа, правка, сам узел и его привязка.
 *
 * Все четыре — обычные карточки Askya, те же, что у дела и у записи книги.
 * Карта под ними остаётся на месте: узел открывают, чтобы дочитать и решить,
 * что вырастить дальше, а не чтобы уйти в другой экран.
 */

/**
 * Что заводим — тип нового узла.
 *
 * ## Порядок не алфавитный и не постоянный
 *
 * Когда узел растят из другого, первыми стоят те типы, которыми в этом месте
 * продолжают чаще ([ThreadNodeKind.grows]): из подводного камня — вопрос, из
 * шага — открытие. Это не ограничение: ниже стоят все остальные, и выбрать
 * можно любой. Это подсказка о том, как замысел обычно движется, — и вся
 * «помощь в размышлении», какая бывает без сети.
 *
 * У первого узла нити подсказки нет: там сверху искра, потому что с неё всё и
 * начинается.
 */
@Composable
fun ThreadKindCard(
    from: ThreadNode?,
    onPick: (ThreadNodeKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = from?.kind?.grows ?: listOf(ThreadNodeKind.SPARK)
    val rest = ThreadNodeKind.entries.filterNot { it in first }

    AskyaDialog(onDismiss = onDismiss) {
        DialogTitle(if (from == null) "Новый узел" else "Что вырастет отсюда")

        if (from != null) {
            Text(
                text = "Из «${from.title.ifBlank { from.kind.title }}»",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        DialogCaption(if (from == null) "С чего начать" else "Обычно дальше идёт")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            first.forEach { kind -> KindLine(kind = kind, lit = true, onPick = { onPick(kind) }) }
        }

        DialogCaption("Или что-то другое")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rest.forEach { kind -> KindLine(kind = kind, lit = false, onPick = { onPick(kind) }) }
        }
    }
}

/** Строка выбора типа: значок в краске типа, имя и то, что в него пишут. */
@Composable
private fun KindLine(kind: ThreadNodeKind, lit: Boolean, onPick: () -> Unit) {
    val colour = nodeColor(kind)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (lit) AccentSoft else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onPick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(nodeShape(kind))
                .background(colour.copy(alpha = 0.18f))
                .border(1.dp, colour.copy(alpha = 0.6f), nodeShape(kind)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = nodeIcon(kind),
                contentDescription = null,
                tint = colour,
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = kind.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (lit) AccentInk else Ink,
            )
            Text(
                text = kind.hint,
                style = MaterialTheme.typography.bodySmall,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Правка узла: одна строка и развёрнутое под ней.
 *
 * Строка — то, что видно на карте, поэтому её и спрашивают первой и коротко.
 * Развёрнутое не обязательно: половина узлов замысла — это четыре слова, и поле,
 * требующее абзаца, заставляло бы их выдумывать.
 */
@Composable
fun ThreadNodeEditCard(
    node: ThreadNode,
    onSave: (ThreadNode) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(node.id) { mutableStateOf(node.title) }
    var note by remember(node.id) { mutableStateOf(node.note) }

    AskyaDialog(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = nodeIcon(node.kind),
                contentDescription = null,
                tint = nodeColor(node.kind),
                modifier = Modifier.size(20.dp),
            )
            DialogTitle(
                text = if (node.id == 0L) node.kind.title else "Правим: ${node.kind.title}",
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        DialogCaption("Одной строкой")
        DialogField(
            value = title,
            onValueChange = { title = it },
            hint = node.kind.hint,
            autoFocus = node.id == 0L,
            singleLine = false,
        )

        DialogCaption("Подробнее")
        DialogField(
            value = note,
            onValueChange = { note = it },
            hint = "Не обязательно",
            singleLine = false,
        )
        Text(
            text = node.kind.nudge,
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        DialogButtons {
            ActionButton(icon = Icons.Outlined.Close, label = "Отмена", onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                enabled = title.isNotBlank(),
                onClick = { onSave(node.copy(title = title, note = note)) },
            )
        }
    }
}

/**
 * Сам узел: что в нём написано, с чем он связан и что с ним можно сделать.
 *
 * ## Встречный вопрос стоит выше действий
 *
 * Первое, что видно под текстом узла, — вопрос его типа: «а если сделать так,
 * чтобы этот камень перестал мешать?». Он не совет и ничего не предлагает
 * сделать; он возвращает человека к его же мысли. Ради этого раздел и заведён:
 * усиливать размышление, а не подменять его списком задач.
 *
 * ## «Вырастить» — главное действие карточки
 *
 * Оно стоит первым и с краской, потому что им карта и растёт: новый узел
 * появляется уже связанным, и человеку не приходится ни выбирать место, ни
 * тянуть линию.
 */
@Composable
fun ThreadNodeCard(
    node: ThreadNode,
    web: ThreadWeb,
    done: Boolean,
    onEdit: () -> Unit,
    onGrow: () -> Unit,
    onTie: () -> Unit,
    onUntie: (Long) -> Unit,
    onOpenNode: (Long) -> Unit,
    onMark: (Boolean) -> Unit,
    onDeed: () -> Unit,
    onLine: () -> Unit,
    onLink: () -> Unit,
    onOpenLink: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colour = nodeColor(node.kind)
    val deed = web.deedOf(node)
    val tied = web.tiedTo(node.id).mapNotNull { web.node(it) }

    AskyaDialog(onDismiss = onDismiss, width = 0.92f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(nodeShape(node.kind))
                    .background(colour.copy(alpha = 0.18f))
                    .border(1.dp, colour.copy(alpha = 0.6f), nodeShape(node.kind)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = nodeIcon(node.kind),
                    contentDescription = null,
                    tint = colour,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = node.kind.title,
                style = MaterialTheme.typography.labelLarge,
                color = Muted,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        DialogTitle(
            text = node.title.ifBlank { "Без слов" },
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(max = 380.dp)
                .fadingVerticalScroll(),
        ) {
            if (node.note.isNotBlank()) {
                Text(
                    text = node.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Встречный вопрос. Тихой плашкой и без кнопки «ответить»: на него
            // отвечают не в поле, а следующим узлом.
            Text(
                text = node.kind.nudge,
                style = MaterialTheme.typography.bodyMedium,
                color = AccentInk,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AccentSoft)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )

            if (done) {
                val when_ = web.doneOn(node)
                Text(
                    text = when_?.let { "Сделано ${formatRussianDate(it)}" } ?: "Сделано",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colour,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (deed != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Делом на ${formatRussianDate(deed.date)}" +
                            if (deed.done) " · отмечено" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // Привязка: то, чем этот узел уже лежит в Askya, — книга
            // Библиотеки, запись, список, плейлист.
            DialogCaption("Чем подкреплён")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = if (node.link == null) Muted else Accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = node.link?.let { linkWord(it) } ?: "Ничем — нажми, чтобы привязать",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (node.link == null) Muted else AccentInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onLink)
                        .padding(vertical = 6.dp),
                )
                node.link?.let { link ->
                    Text(
                        text = "Перейти",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenLink(link) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (tied.isNotEmpty()) {
                DialogCaption("Связан с")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tied.forEach { other ->
                        TiedLine(
                            node = other,
                            onOpen = { onOpenNode(other.id) },
                            onUntie = { onUntie(other.id) },
                        )
                    }
                }
            }
        }

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.DeleteOutline,
                label = "Стереть",
                color = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
            ActionButton(icon = Icons.Outlined.Tune, label = "Правка", onClick = onEdit)
            Spacer(Modifier.weight(1f))
            ActionButton(icon = Icons.Outlined.Hub, label = "Связать", onClick = onTie)
            ActionButton(
                icon = Icons.Outlined.Add,
                label = "Вырастить",
                accent = true,
                onClick = onGrow,
            )
        }

        // Второй ряд — только у шага: ему одному есть куда уйти из карты.
        if (node.kind == ThreadNodeKind.STEP) {
            DialogButtons {
                ActionButton(
                    icon = if (done) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check,
                    label = if (done) "Снять отметку" else "Сделан",
                    accent = !done,
                    onClick = { onMark(!done) },
                )
                Spacer(Modifier.weight(1f))
                ActionButton(
                    icon = Icons.Outlined.Checklist,
                    label = "В список",
                    enabled = node.title.isNotBlank(),
                    onClick = onLine,
                )
                ActionButton(
                    icon = Icons.Outlined.CalendarMonth,
                    label = if (deed == null) "В день" else "Уже в дне",
                    enabled = deed == null && node.title.isNotBlank(),
                    onClick = onDeed,
                )
            }
        } else if (node.kind.markable) {
            DialogButtons {
                Spacer(Modifier.weight(1f))
                ActionButton(
                    icon = if (done) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.Check,
                    label = if (done) "Ещё не достигнут" else "Достигнут",
                    accent = !done,
                    onClick = { onMark(!done) },
                )
            }
        }
    }
}

/** Строка соседа: куда ведёт связь и как её снять. */
@Composable
private fun TiedLine(node: ThreadNode, onOpen: () -> Unit, onUntie: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 12.dp),
    ) {
        Icon(
            imageVector = nodeIcon(node.kind),
            contentDescription = null,
            tint = nodeColor(node.kind),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = node.title.ifBlank { node.kind.title },
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
        )
        Text(
            text = "Развязать",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onUntie)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

/**
 * Привязка узла — тем же списком, каким привязывают дело.
 *
 * Ни своего списка, ни своих видов: узел привязывается к тем же книгам,
 * записям, спискам и разделам, что и дело дня ([rememberLinkChoices]). Нити из
 * выбора убраны — узел одной нити, показывающий на другую нить целиком, читался
 * бы как ошибка; связывают узлы связями, а нити между собой — общим замыслом.
 */
@Composable
fun ThreadNodeLinkCard(
    node: ThreadNode,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices: List<LinkChoice> = rememberLinkChoices().filterNot { it.group == "Нити" }

    AskyaDialog(onDismiss = onDismiss) {
        DialogTitle("Чем подкрепить")
        Text(
            text = "Книга, запись, список или раздел — то, что по этому узлу уже есть в " +
                "Askya. Материал остаётся на своём месте: узел только показывает на него.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Box(modifier = Modifier.padding(top = 12.dp).heightIn(max = 420.dp)) {
            LinkPalette(
                chosen = node.link,
                choices = choices,
                onPick = { picked -> onPick(picked) },
            )
        }
    }
}

/** Как привязка называется человеку, когда самой вещи под рукой нет. */
private fun linkWord(raw: String): String = when (DeedLink.of(raw)?.kind) {
    LinkKind.BOOK -> "Книга Библиотеки"
    LinkKind.NOTE -> "Запись Scroll"
    LinkKind.YET -> "Список Yet"
    LinkKind.ECHO -> LinkKind.ECHO.title
    LinkKind.VIDEO -> LinkKind.VIDEO.title
    LinkKind.BRIDGE -> "Мост"
    LinkKind.THREAD -> "Другая нить"
    null -> "Привязано"
}
