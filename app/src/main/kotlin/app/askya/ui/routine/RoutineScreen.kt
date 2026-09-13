package app.askya.ui.routine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.askya.app.appContainer
import app.askya.data.entity.RoutineItem
import app.askya.ui.components.BlockCard
import app.askya.ui.components.CardContent
import app.askya.ui.components.CardDialog
import app.askya.ui.components.CardGrid
import app.askya.ui.components.DayPart
import app.askya.ui.components.DayPartTitle
import app.askya.domain.model.DeedDays
import app.askya.domain.model.Priority
import app.askya.domain.model.DeedLink
import app.askya.domain.model.LinkKind
import app.askya.ui.components.FadingColumn
import app.askya.ui.components.LinkChoice
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.rememberLinkChoices
import app.askya.ui.components.blockIconOf
import app.askya.ui.components.formatRange
import app.askya.ui.theme.Accent
import app.askya.ui.theme.Ink

/**
 * «Список дел»: базовый список дел на день, из которого собирается расписание.
 *
 * Открывается из AskyaDay во весь экран — это не строчка настройки, а список,
 * с которым работают, и заводят его там же, где смотрят день.
 *
 * Показывается ровно так же, как расписание: те же карточки, та же сетка по
 * трое, те же части дня, та же раскрытая карточка на правку. Это один и тот же
 * день в двух видах — заведённый и прожитый, — и разная вёрстка заставляла бы
 * сверять их глазами.
 *
 * Рассказ о себе и его разбор моделью отсюда убраны вместе с самой моделью:
 * дела пишут руками, у каждого названо время. Правка списка не переписывает
 * уже созданные дни: прошедший день — это запись о том, что было, а не
 * отражение текущих намерений.
 */
@Composable
fun RoutineScreen(onBack: () -> Unit, onOpenLink: (String) -> Unit = {}) {
    val viewModel: RoutineViewModel = viewModel(factory = RoutineViewModel.factory(appContainer()))
    // Context нужен не экрану, а будильникам: сохранённое дело доходит до
    // сегодняшнего дня, и вместе с убранным из него снимаются напоминания.
    val context = LocalContext.current
    val items by viewModel.items.collectAsStateWithLifecycle()
    val bridges by appContainer().bridgeRepository.bridges()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    // Мосты — такие же строки выбора, как книги и списки: у строки списка дел
    // «чем делается» тот же вопрос, что у дела в дне.
    val linkChoices = rememberLinkChoices() + bridges.map { bridge ->
        LinkChoice(
            value = DeedLink(LinkKind.BRIDGE, bridge.id).store(),
            title = bridge.name.ifBlank { "Без названия" },
            group = "Мосты",
        )
    }

    // null — диалога нет; Editing(null) — новое дело.
    var editing by remember { mutableStateOf<Editing?>(null) }

    ScreenScaffold(
        title = "Список дел",
        onNavigationClick = onBack,
        navigationIsBack = true,
        floatingActionButton = {
            // Та же чёрная «таблетка» с коралловым плюсом, что в AskyaDay: жест
            // один и тот же — «завести новое дело», — и выглядеть он должен
            // одинаково в обоих местах. Слово короче на «card»: здесь заводят
            // дело обычного дня, а не карточку на дату.
            ExtendedFloatingActionButton(
                onClick = { editing = Editing(null) },
                shape = RoundedCornerShape(percent = 50),
                containerColor = Ink,
                contentColor = Accent,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "new",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) {
        FadingColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
            // Свой отступ строки задаёт ритм сетки; общий интервал только
            // отделяет части дня друг от друга — как в расписании.
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    Text(
                        text = "Пока пусто. Добавь дело — из этого списка и собирается день.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                DayPart.entries.forEach { part ->
                    val partItems = items.filter { DayPart.of(it.startTime) == part }
                    if (partItems.isEmpty()) return@forEach

                    item(key = part.name) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            DayPartTitle(part.title, part = part)
                            CardGrid(partItems) { item, cardModifier ->
                                RoutineCard(
                                    item = item,
                                    onClick = { editing = Editing(item) },
                                    onEnabledChange = { viewModel.setEnabled(context, item, it) },
                                    modifier = cardModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { current ->
        // Та же раскрытая карточка, что в дне: время, потом название. Заметки
        // у дела в списке нет — она пишется к конкретному дню, а не к правилу,
        // по которому он собирается. «Выполнено» нет по той же причине.
        // Важность, наоборот, есть только здесь: она про то, как собирать день,
        // а в самом дне решать уже нечего.
        CardDialog(
            card = current.item?.let {
                CardContent(
                    title = it.title,
                    time = formatRange(it.startTime, it.endTime),
                    icon = it.icon,
                    priority = it.priority,
                    link = it.link,
                    days = it.repeatDays,
                )
            },
            withNote = false,
            withPriority = true,
            // Дни недели есть только здесь: повторение — свойство правила, по
            // которому собирается день, а не самого дня. Делу, уже стоящему в
            // среде, спрашивать «по каким дням» не о чем.
            withDays = true,
            // Привязка у строки списка — та же, что у дела дня, и стоит она
            // здесь ради повторяющегося: «Чтение Библии» делается одной и той
            // же книгой каждый день, и выбирать её заново в каждом дне
            // человек не станет. День берёт её отсюда по названию.
            linkChoices = linkChoices,
            onOpenLink = onOpenLink,
            onDismiss = { editing = null },
            onDelete = current.item?.let { item ->
                {
                    viewModel.delete(item.id)
                    editing = null
                }
            },
            onSave = { draft ->
                val base = current.item ?: RoutineItem(title = draft.title, startTime = draft.start)
                viewModel.save(
                    context,
                    base.copy(
                        title = draft.title,
                        startTime = draft.start,
                        endTime = draft.end,
                        icon = draft.icon,
                        priority = draft.priority,
                        link = draft.link,
                        days = DeedDays.store(draft.days),
                    ),
                )
            },
        )
    }
}

/** Открытый диалог. [item] = null — дело ещё не создано. */
private data class Editing(val item: RoutineItem?)

/**
 * Карточка дела в списке: общая карточка плюс переключатель снизу.
 *
 * Выключенное дело не пропадает и не уезжает вниз — оно остаётся на своём месте
 * в дне и только гаснет: важно видеть, что в это время дело есть, просто сейчас
 * оно не разворачивается. Переключатель стоит там же, где в расписании стоят
 * заметка и колокольчик, — по низу карточки.
 */
@Composable
private fun RoutineCard(
    item: RoutineItem,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BlockCard(
        icon = blockIconOf(item.title, item.icon),
        time = formatRange(item.startTime, item.endTime),
        title = item.title,
        titleColor = if (item.enabled) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        metaColor = MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = onClick,
        modifier = modifier,
    ) {
        // Дни недели подписаны только у того дела, которое случается не каждый
        // день: «Каждый день» под каждой карточкой — это слово, которое
        // перестают читать на второй карточке, а «Пн Ср Пт» и есть новость.
        //
        // «Важно» стоит рядом с ними и по той же мерке: это уже не пометка о
        // настроении, а поведение — важное дело само встаёт в день (см.
        // [app.askya.data.repository.DayRepository.ensureStanding]), и не
        // видеть этого в списке значило бы гадать, откуда в дне взялось дело.
        val days = item.repeatDays
        val under = listOfNotNull(
            "Важно".takeIf { item.priority == Priority.HIGH },
            DeedDays.title(days).takeIf { days.isNotEmpty() },
        ).joinToString(" · ")
        if (under.isNotEmpty()) {
            Text(
                text = under,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.priority == Priority.HIGH) {
                    Accent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        Switch(
            checked = item.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
}
