package app.askya.ui.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.data.preferences.SHELF_COLUMNS
import app.askya.data.preferences.SHELF_RANGE
import app.askya.echo.formatDuration
import app.askya.ui.components.FadingGrid
import app.askya.ui.echo.EchoDialog
import app.askya.ui.echo.EchoPill
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.video.Clip

/**
 * Полка: ролики стоят коробками, как диски на полке.
 *
 * ## Почему коробка, а не строчка
 *
 * Строчка списка отвечает на вопрос «что здесь лежит»: имя, длительность,
 * вес. Полка отвечает на другой — «что я хочу посмотреть», — и на него
 * отвечают не чтением, а взглядом. Фильм узнают по обложке: за секунду глаз
 * перебирает два десятка коробок и находит нужную, а тот же список именами
 * приходится читать по строчке.
 *
 * Кадр из самого ролика вместо обложки — единственное, что тут возможно:
 * рисованных обложек у файлов с телефона нет и взяться им неоткуда. Кадр с
 * третьей секунды у снятого телефоном — это ровно то место, где ещё видно, что
 * снимали.
 *
 * ## Почему именно коробка от DVD
 *
 * У неё есть корешок, и в этом всё дело. Коробка без корешка — просто карточка
 * с картинкой, каких в любом приложении по десятку. Корешок же делает вещь
 * вещью: он повёрнут к смотрящему ребром, на нём вдоль написано название, и
 * ряд таких коробок читается как полка, а не как таблица. Поэтому и пропорция
 * взята настоящая — 135 на 191 миллиметр, как у пластиковой коробки, — и угол
 * со стороны открывания скруглён сильнее, чем со стороны шва.
 *
 * Название написано дважды: вдоль корешка и внизу лицевой стороны. Это не
 * повтор ради красоты — на корешке имя обрезано шириной коробки, а внизу лежит
 * в две строки целиком; корешок нужен, чтобы узнать, лицевая — чтобы прочесть.
 *
 * ## Что на лицевой стороне
 *
 * Кадр, тень к низу и на ней две строки: имя и под ним длительность с
 * размером. Тень нужна не для вида: белые буквы на светлом кадре не читаются
 * никак, а перекрасить их по кадру значило бы считать его яркость на каждой
 * прокрутке.
 *
 * Полоска досмотренного идёт по самому низу коробки и есть только у начатых:
 * пустая полоска под каждым непросмотренным превратила бы полку в график.
 *
 * ## Сколько их в ряду
 *
 * [columns] — число коробок в ряду, а не наименьшая ширина каждой. Ширину
 * подбирала система: `Adaptive(150.dp)` давала на обычном телефоне два ряда, а
 * на широком три, и человек, собравший полку под свой телефон, увидел бы её
 * другой на планшете. Число же значит ровно то, что видно: две коробки — это
 * витрина, шесть — полка.
 *
 * Сама коробка при этом не «уменьшенная»: с шириной у неё меняется и толщина
 * корешка, и размер подписей, а у самых узких подписи с лицевой стороны
 * пропадают вовсе — см. [DvdCase]. Ужатая целиком, она превратилась бы в
 * марку с нечитаемым текстом.
 */
@Composable
internal fun ClipShelf(
    clips: List<Clip>,
    spots: Map<String, Long>,
    onPlay: (Clip) -> Unit,
    onMenu: (Clip) -> Unit,
    columns: Int = SHELF_COLUMNS,
) {
    // Тесной полке и промежутки поменьше: те же двенадцать точек между шестью
    // коробками съели бы у каждой пятую часть ширины.
    val gap = if (columns >= 5) 7.dp else 12.dp

    FadingGrid(
        columns = GridCells.Fixed(columns.coerceIn(SHELF_RANGE.first, SHELF_RANGE.last)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap + 2.dp),
    ) {
        items(clips, key = { it.id }) { clip ->
            DvdCase(
                clip = clip,
                watchedMs = spots[clip.uri] ?: 0L,
                onPlay = { onPlay(clip) },
                onMenu = { onMenu(clip) },
            )
        }
    }
}

/**
 * Одна коробка.
 *
 * Короткое нажатие включает, долгое раскрывает карточкой — то же правило, что
 * у строчки списка и у песни в Echo.
 *
 * ## Коробка меряет себя сама
 *
 * Ширина приходит от полки, и от неё же зависит всё остальное: толщина
 * корешка, кегль подписей и то, есть ли подписи вообще. Числа заданы долями
 * ширины, а не по числу коробок в ряду: одна и та же полка на телефоне и на
 * планшете даёт разную ширину, и коробка должна отвечать на то, сколько места
 * ей досталось, а не на то, сколько у неё соседей.
 *
 * У самых узких — тех, что выходят при шести в ряду, — надписи с лицевой
 * стороны сняты совсем. Две строки имени кеглем в шесть точек не читаются
 * ниоткуда, а серая каша поверх кадра мешает узнать сам кадр; узкая полка —
 * это полка, где узнают по картинке и по корешку, и подпись там лишняя. Тень
 * к низу вместе с ними тоже уходит: держать её не для чего.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DvdCase(
    clip: Clip,
    watchedMs: Long,
    onPlay: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val frame = rememberFrame(clip)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CASE)
            .clip(CASE_SHAPE)
            .background(NightPanel)
            .border(1.dp, NightBorder, CASE_SHAPE)
            .combinedClickable(onClick = onPlay, onLongClick = onMenu),
    ) {
        val tall = maxHeight
        val wide = maxWidth

        // Корешок долей ширины, а не в шестнадцать точек всегда: на коробке в
        // сорок пять точек шириной он занимал бы её треть.
        val spine = (wide * 0.11f).coerceIn(6.dp, 16.dp)
        // Имя на лицевой стороне — только там, где его прочтут.
        val titled = wide >= 78.dp
        val roomy = wide >= 118.dp
        val nameSize = if (roomy) 14.sp else 12.sp

        Row(modifier = Modifier.fillMaxSize()) {
            // Корешок. Шов между ним и лицевой стороной — не обводка, а тень:
            // у настоящей коробки там сгиб, а не край.
            Box(
                modifier = Modifier
                    .width(spine)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(listOf(NightPanelSoft, NightPanel)),
                    )
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = clip.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = NightMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // По середине строки, а не по её началу: надпись лежит в
                    // поле шириной во всю коробку, и короткое имя, прижатое к
                    // началу строки, после поворота оказывается у нижнего края
                    // корешка — как будто съехало.
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .requiredWidth(tall - 12.dp)
                        .rotate(-90f),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (frame != null) {
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Кадра нет — вместо него ровный тёмный лист со знаком:
                    // пустая белая коробка читалась бы как незагрузившаяся
                    // картинка, а не как «кадра не нашлось».
                    Box(
                        modifier = Modifier.fillMaxSize().background(NightPanelSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = NightMuted,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                if (titled) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.42f to Color.Transparent,
                                    1f to Night.copy(alpha = 0.92f),
                                ),
                            ),
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp, bottom = 7.dp),
                    ) {
                        Text(
                            text = clip.title,
                            fontFamily = FontFamily.Serif,
                            fontSize = nameSize,
                            lineHeight = nameSize * 1.22f,
                            color = NightInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Длительность с разрешением — только на широкой
                        // коробке: на средней вторая строка отняла бы место у
                        // имени, а имя важнее.
                        if (roomy) {
                            Text(
                                text = listOfNotNull(
                                    formatDuration(clip.durationMs)
                                        .takeIf { clip.durationMs > 0 },
                                    clip.resolution.takeIf { it.isNotEmpty() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = NightMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (watchedMs > 0 && clip.durationMs > 0) {
                    val done = (watchedMs.toFloat() / clip.durationMs).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(NightBorder),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(done)
                                .height(3.dp)
                                .background(Sunset),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Пропорция коробки — настоящая: 135 на 191 миллиметр.
 *
 * Числом, а не «примерно вытянутая»: полка из коробок узнаётся именно по этой
 * пропорции, и вытянутая на глаз читалась бы как книга или как афиша.
 */
private const val CASE = 135f / 191f

/**
 * Скругления: со стороны открывания больше, со стороны шва меньше.
 *
 * У пластиковой коробки петля с одного края, и этот край почти прямой, а
 * противоположный скруглён как следует. Одинаковые углы сделали бы из коробки
 * плитку.
 */
private val CASE_SHAPE = RoundedCornerShape(
    topStart = 4.dp,
    bottomStart = 4.dp,
    topEnd = 12.dp,
    bottomEnd = 12.dp,
)

/**
 * Калибровка полки: сколько коробок ставить в ряд.
 *
 * ## Зачем это вообще спрашивать
 *
 * У полки нет одного правильного размера, и это не вкусовщина. Тот, у кого
 * два десятка фильмов, ищет глазами обложку — ему нужна витрина. Тот, у кого
 * их две сотни, ищет знакомый корешок в ряду и листает; ему витрина — это
 * десять экранов прокрутки. Приложение не знает, какой из двух человек его
 * открыл, а человек знает сразу.
 *
 * ## Почему рядом с числом стоит полка, а не число
 *
 * Выбирают не «четыре», а то, как это выглядит. Поэтому под пилюлями стоит
 * сама полка из пустых коробок в выбранном числе: видно и размер коробки, и
 * то, останется ли на ней подпись. Список «2 · 3 · 4 · 5 · 6» без картинки
 * заставлял бы закрывать окно и смотреть, потом открывать снова.
 *
 * Меньше двух не бывает: одна коробка в ряду — это уже не полка, а список
 * картинками. Больше шести — коробка уже́ ногтя, и попасть в неё пальцем
 * труднее, чем прочесть строчку.
 */
@Composable
internal fun ShelfCalibration(
    columns: Int,
    onColumns: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    EchoDialog(title = "Полка", onDismiss = onDismiss) {
        Text(
            text = "Сколько коробок ставить в ряд.",
            style = MaterialTheme.typography.bodyMedium,
            color = NightMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SHELF_RANGE.forEach { count ->
                EchoPill(
                    label = "$count",
                    chosen = count == columns,
                    onClick = { onColumns(count) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Образец полки: пустые коробки того размера, какой выйдет. Без кадров
        // намеренно — здесь меряют размер, а не разглядывают фильмы, и чужие
        // обложки в образце отвлекали бы от единственного вопроса.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (columns >= 5) 7.dp else 12.dp),
        ) {
            repeat(columns) {
                SampleCase(modifier = Modifier.weight(1f))
            }
        }

        Text(
            text = when {
                columns <= 2 -> "Витрина: обложка видна целиком, имя и длительность под ней."
                columns <= 4 -> "Полка: обложку ещё узнаёшь, имя читается."
                else -> "Тесная полка: подписи с лицевой стороны сняты, имя остаётся на корешке."
            },
            style = MaterialTheme.typography.bodySmall,
            color = NightMuted,
            modifier = Modifier.padding(top = 14.dp),
        )

        EchoPill(
            label = "Готово",
            chosen = true,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
    }
}

/** Пустая коробка для образца: та же пропорция, тот же корешок, тот же угол. */
@Composable
private fun SampleCase(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(CASE)
            .clip(CASE_SHAPE)
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, CASE_SHAPE),
    ) {
        Box(
            modifier = Modifier
                .width((maxWidth * 0.11f).coerceIn(6.dp, 16.dp))
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(NightPanel, NightPanelSoft))),
        )
    }
}
