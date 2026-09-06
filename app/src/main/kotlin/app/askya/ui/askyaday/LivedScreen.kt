package app.askya.ui.askyaday

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.domain.lived.DeedName
import app.askya.domain.lived.LivedRow
import app.askya.domain.lived.MonthTally
import app.askya.domain.lived.WatchedDeed
import app.askya.domain.lived.deedNames
import app.askya.domain.lived.streakWord
import app.askya.domain.lived.tally
import app.askya.domain.lived.watched
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogChoice
import app.askya.ui.components.DialogText
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import java.time.LocalDate

/**
 * «Прожитое» — счёт по делам, которые человек выбрал сам.
 *
 * Раздел начинался с общего счёта: полоса всего года, самый занятый месяц,
 * «записано 222 дела, отмечено 135». Всё это правда и всё это ни о чём: сумма
 * по «Завтраку», «Отвезти Алёну» и «Чтению Библии» не отвечает ни на один
 * вопрос, который человек себе задаёт. Осталось только выбранное — и страница
 * молчит, пока не выбрано ничего.
 *
 * **Помесячно, а не по дням.** Год по дням для одного дела не читается: у дела,
 * случившегося тридцать раз, триста тридцать дней пустые, и тридцать штрихов в
 * волос толщиной не складываются ни во что. Месяц — та мера, в которой человек
 * и думает о повторяющемся: «в мае бегал четыре раза, в июне ни разу».
 */
@Composable
fun LivedScreen(onBack: () -> Unit) {
    val container = appContainer()
    val today = remember { LocalDate.now() }
    val from = remember { today.minusYears(1).plusDays(1) }

    var rows by remember { mutableStateOf<List<LivedRow>?>(null) }
    LaunchedEffect(from, today) {
        rows = container.scheduleRepository.lived(from, today)
    }

    val settings by container.settings.settings
        .collectAsStateWithLifecycle(initialValue = container.settings.state.value)
    var picking by remember { mutableStateOf(false) }

    val deeds = remember(rows, settings.watchedDeeds) {
        rows?.let { watched(it, settings.watchedDeeds, today) }.orEmpty()
    }

    ScreenScaffold(
        title = "Прожитое",
        onNavigationClick = onBack,
        actions = {
            // Знак стоит в шапке, а не кнопкой внизу страницы: выбор делают раз
            // в полгода, и место внизу дороже отдать самой странице.
            if (rows != null) {
                IconButton(onClick = { picking = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "За чем следить",
                        tint = Accent,
                    )
                }
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fadingVerticalScroll()
                .padding(horizontal = 16.dp),
        ) {
            if (rows == null) {
                Text(
                    text = "Считаю год…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
                return@Column
            }

            Text(
                text = "За чем слежу",
                fontFamily = FontFamily.Serif,
                fontSize = 20.sp,
                color = AccentInk,
                modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
            )

            if (deeds.isEmpty()) {
                Text(
                    text = "Здесь считается только то, что вы отметили сами. Выберите дело — " +
                        "«Чтение Библии», «Бег», — и под ним встанет счёт: сколько раз оно " +
                        "стояло в дне и сколько раз было сделано, месяц за месяцем.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Слева, по краю письма: это не ответ в окне, а строка страницы,
                // и прижатая к правому краю кнопка висела бы в пустоте.
                Row(modifier = Modifier.padding(top = 6.dp)) {
                    ActionButton(
                        icon = Icons.Outlined.Visibility,
                        label = "Выбрать",
                        accent = true,
                        onClick = { picking = true },
                    )
                }
            } else {
                deeds.forEach { deed -> DeedCard(deed) }

                Text(
                    text = "Столбик — месяц. Во весь рост — сколько раз дело стояло в дне, " +
                        "краской снизу — сколько раз сделано; число над столбиком — " +
                        "сделанные разы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = "Считается обычным запросом к своей базе. Ничто из этого никуда не " +
                    "уходит и никем, кроме вас, не читается.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 40.dp),
            )
        }
    }

    val known = rows
    if (picking && known != null) {
        WatchPicker(
            names = remember(known) { deedNames(known) },
            chosen = settings.watchedDeeds,
            onToggle = { container.settings.toggleWatchedDeed(it) },
            onDismiss = { picking = false },
        )
    }
}

/** Одно выбранное дело: название, счёт словами и год помесячно. */
@Composable
private fun DeedCard(deed: WatchedDeed) {
    Text(
        text = deed.title,
        fontFamily = FontFamily.Serif,
        fontSize = 21.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
    Text(
        text = listOfNotNull(deed.tally(), deed.streakWord()).joinToString(", "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    )
    MonthBars(months = deed.months)
    Spacer(Modifier.height(18.dp))
}

/**
 * Год дела по месяцам.
 *
 * Столбик показывает поставленное, залитая снизу часть — сделанное: расстояние
 * между «собирался» и «сделал» видно ростом светлого над краской, и это ровно
 * тот вопрос, ради которого дело сюда и выбрали.
 *
 * Числом подписаны только сделанные разы: «сколько раз я всё-таки читал» — то,
 * за чем приходят, а второе число рядом превратило бы ряд в таблицу, которую
 * надо разбирать.
 *
 * Столбики набраны плашками, а не одним холстом: число, столбик и подпись
 * месяца стоят в одной колонке, и раскладке проще держать их вместе, чем
 * вычислять в холсте координаты текста.
 */
@Composable
private fun MonthBars(months: List<MonthTally>) {
    if (months.isEmpty()) return
    // Потолок — самый занятый месяц: у дела, стоящего раз в неделю, свой
    // масштаб, и подгонять его под ежедневное значило бы придавить к полу.
    val ceiling = maxOf(months.maxOf { it.planned }, 1)

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { month ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (month.done > 0) month.done.toString() else "",
                    fontSize = 11.sp,
                    color = Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Box(
                    modifier = Modifier.fillMaxWidth().height(BARS_HEIGHT),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Черта в основании стоит под каждым месяцем, и под пустым
                    // тоже: без неё ряд с двумя столбиками у правого края
                    // выглядит обрывком, а не годом, в котором десять месяцев
                    // прошли мимо. Пустой месяц должен читаться нулём, а не
                    // пропажей.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(BASE_LINE)
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    )

                    if (month.planned > 0) {
                        val full = BARS_HEIGHT * (month.planned.toFloat() / ceiling)
                        val filled = BARS_HEIGHT * (month.done.toFloat() / ceiling)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(BAR_WIDTH)
                                .height(maxOf(full, BAR_LEAST))
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        if (month.done > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(BAR_WIDTH)
                                    .height(maxOf(filled, BAR_LEAST))
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Accent),
                            )
                        }
                    }
                }

                Text(
                    text = MONTH_LETTERS[month.month.monthValue - 1],
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Окно выбора: за чем следить.
 *
 * Списком того, что уже записано в дне, а не строкой для ввода: набранное с
 * опечаткой дело не совпало бы ни с одним днём и молчало бы вечно, а винили бы
 * счёт, а не опечатку.
 *
 * Тап по строке и есть ответ — отмеченное встаёт на страницу тут же, без
 * «Сохранить»: сохранять здесь нечего, кроме самого выбора.
 */
@Composable
private fun WatchPicker(
    names: List<DeedName>,
    chosen: List<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.Visibility) }) {
        DialogTitle("За чем следить")
        DialogText(
            "Дела за последний год, частые — выше. Отмеченные встанут в «Прожитом» " +
                "своим счётом.",
        )

        if (names.isEmpty()) {
            DialogText("За год в дне ещё ничего не стояло: выбирать не из чего.")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    // Обрезка обязательна: без неё уезжающие строки наползают
                    // на объяснение над списком и на «Готово» под ним.
                    .clip(RoundedCornerShape(14.dp))
                    .fadingVerticalScroll()
                    .padding(top = 12.dp),
            ) {
                names.forEach { name ->
                    val picked = chosen.any { it.equals(name.title, ignoreCase = true) }
                    DialogChoice(
                        icon = if (picked) Icons.Outlined.TaskAlt else Icons.Outlined.RadioButtonUnchecked,
                        title = name.title,
                        about = "стояло " + name.planned + ", отмечено " + name.done,
                        picked = picked,
                        onClick = { onToggle(name.title) },
                    )
                }
            }
        }

        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Готово",
                accent = true,
                onClick = onDismiss,
            )
        }
    }
}

/** Во что вырастает самый занятый месяц. */
private val BARS_HEIGHT = 76.dp

/** Месяц, в котором было хоть что-то, виден всегда — хотя бы полоской. */
private val BAR_LEAST = 3.dp

/** Черта под месяцем: ряд читается шкалой, а не двумя столбиками в пустоте. */
private val BASE_LINE = 2.dp

/**
 * Какую часть своей клетки занимает столбик.
 *
 * Не всю: столбики впритык слипаются в сплошную заливку, и год перестаёт
 * делиться на месяцы раньше, чем человек дочитает подписи.
 */
private const val BAR_WIDTH = 0.62f

/**
 * Месяцы под столбиками. Три буквы, а не одна: «м» стояло бы и над мартом, и
 * над маем, а «и» — над июнем и июлем.
 */
private val MONTH_LETTERS = listOf(
    "янв", "фев", "мар", "апр", "май", "июн",
    "июл", "авг", "сен", "окт", "ноя", "дек",
)
