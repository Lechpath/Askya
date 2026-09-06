package app.askya.ui.echo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.echo.EchoChord
import app.askya.echo.EchoChordScore
import app.askya.echo.EchoChordSpan
import app.askya.echo.EchoInstrument
import app.askya.echo.Track
import app.askya.echo.fingering
import app.askya.echo.formatDuration
import app.askya.echo.keysOf
import app.askya.ui.components.FadingColumn
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightBorder
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import kotlinx.coroutines.delay

/**
 * Разобранная песня: чем её сыграть.
 *
 * ## Три вещи и в этом порядке
 *
 * Сверху — тональность и инструмент, посередине — хваты, внизу — сама песня
 * строчками. Порядок не декоративный: сначала человек решает, на чём играет
 * (от этого зависят все картинки ниже), потом разучивает три-четыре хвата, и
 * только потом идёт по песне. Поставь ленту аккордов первой — и хваты
 * оказались бы под ней, там, куда не долистывают.
 *
 * ## Почему лента, а не сплошной текст
 *
 * Аккорды идут четвёрками, и у каждой строчки слева время. Четвёрка — это то,
 * как песню и записывают в песеннике: такт, ещё такт, строчка. Сплошная лента
 * из ста имён подряд читалась бы как список, а не как песня, и найти в ней
 * место, где ты сейчас, было бы нечем.
 *
 * Время слева не подпись, а кнопка: нажатие ставит песню с этого места.
 * Разбор — догадка, и первое, что с ней делают, — проверяют на слух; ходить
 * ради этого в плеер и перематывать вслепую значило бы проверять вчетверо
 * дольше.
 *
 * ## Пюпитр
 *
 * Наверху карточки — кнопка «сыграть» и то, что под неё нужно: аккорд,
 * который берут **сейчас**, хватом в полную величину, и рядом поменьше тот,
 * который будет следующим.
 *
 * Разучивают песню не по списку, а под запись: включают и играют вместе с
 * ней. Без пюпитра это значило бы гадать, где ты в ленте из ста имён, — а
 * руки в это время заняты, и водить пальцем по экрану ими нельзя. Поэтому
 * место в песне ищет само приложение: играющий аккорд подсвечен в ленте и она
 * доводится до него сама.
 *
 * Следующий аккорд стоит рядом не для полноты: аккорд берут **до** того, как
 * он зазвучал, и подсказка, появляющаяся ровно в момент смены, опаздывает
 * всегда. Тот же принцип, что у нотной строки: смотрят на такт вперёд.
 *
 * Пюпитр появляется, когда песня играет, и уходит, когда её остановили: место
 * на экране он занимает немалое, и держать его пустым, пока человек читает
 * ленту глазами, значило бы отнимать у ленты треть высоты ни за чем.
 *
 * ## Что здесь сказано прямо
 *
 * Что это подбор, а не ноты. Машина слышит всё разом — голос, барабаны,
 * обертоны — и родственные аккорды путает. Строчка об этом стоит внизу, а не
 * всплывает окном: предупреждение, которое закрывают кнопкой «понятно»,
 * читают один раз в жизни.
 */
@Composable
fun EchoChordsCard(
    track: Track,
    score: EchoChordScore,
    onPlayAt: (Long) -> Unit,
    onDismiss: () -> Unit,
    playing: Boolean = false,
    position: () -> Long = { 0L },
    onToggle: () -> Unit = {},
) {
    var instrument by remember { mutableStateOf(EchoInstrument.GUITAR) }

    // Место в песне спрашивается у плеера, а не приходит потоком: поток на
    // каждую восьмую секунды будил бы экран и тогда, когда карточка закрыта.
    // Спрашивают, только пока играет, — остановили, и опрос кончился сам.
    var at by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (playing) {
            at = position()
            // Восьмая секунды: смена аккорда, опоздавшая на четверть, слышна
            // рукой, а опоздавшая на восьмую — нет.
            delay(120)
        }
    }

    val now = remember(score, at) { score.spans.indexOfLast { at >= it.fromMs } }
    val here = score.spans.getOrNull(now)
    val ahead = score.spans.getOrNull(now + 1)

    val list = rememberLazyListState()
    // Лента доводится до играющей строчки сама: руки заняты струнами.
    // Отдельным `LaunchedEffect` по номеру строчки, а не по месту в песне,
    // — иначе прокрутка дёргалась бы восемь раз в секунду вместо одного раза
    // на строчку.
    val line = if (now < 0) -1 else now / IN_LINE
    LaunchedEffect(playing, line) {
        if (playing && line >= 0) list.animateScrollToItem(HEADS + line)
    }

    EchoCard(
        title = "Аккорды",
        subtitle = track.title,
        onDismiss = onDismiss,
        width = 0.94f,
        height = 0.92f,
    ) {
        Stand(
            playing = playing,
            here = here?.chord,
            ahead = ahead?.chord,
            instrument = instrument,
            onToggle = onToggle,
        )

        FadingColumn(
            state = list,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item(key = "key") {
                Text(
                    text = "Тональность ${score.key} · ${chordsWord(score.chords.size)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NightMuted,
                )
            }

            item(key = "instrument") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EchoInstrument.entries.forEach { option ->
                        EchoPill(
                            label = option.title,
                            chosen = instrument == option,
                            onClick = { instrument = option },
                        )
                    }
                }
            }

            item(key = "shapes") {
                // Строкой вбок, а не сеткой: хватов в песне обычно четыре, и
                // сетка из четырёх картинок съела бы половину экрана, оставив
                // саму песню за нижним краем.
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(score.chords, key = { it.name }) { chord ->
                        ChordShape(chord = chord, instrument = instrument)
                    }
                }
            }

            item(key = "line") {
                Text(
                    text = "Как идёт",
                    fontFamily = FontFamily.Serif,
                    fontSize = 19.sp,
                    color = NightInk,
                    modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                )
            }

            val bars = score.spans.chunked(IN_LINE)
            itemsIndexed(bars, key = { _, bar -> bar.first().fromMs }) { index, bar ->
                ChordLine(
                    bar = bar,
                    playing = if (line == index) now - index * IN_LINE else -1,
                    onPlayAt = onPlayAt,
                )
            }

            item(key = "word") {
                Text(
                    text = if (score.whole) {
                        "Это подбор на слух, а не ноты: машина слышит песню целиком — " +
                            "с голосом и барабанами — и родственные аккорды иногда путает. " +
                            "Время слева ставит песню с этого места: проверить быстрее, " +
                            "чем спорить."
                    } else {
                        "Разобраны первые двенадцать минут — дальше запись не слушалась. " +
                            "Это подбор на слух, а не ноты: родственные аккорды машина " +
                            "иногда путает, и время слева ставит песню с этого места."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NightMuted,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }
        }
    }
}

/**
 * Пюпитр: кнопка «сыграть» и аккорд, который берут сейчас.
 *
 * ## Пока не играет — только кнопка
 *
 * Одна строка во всю ширину вместо значка в углу шапки. Разбор открывают
 * затем, чтобы сыграть, и «включить вместе с записью» — главное, что здесь
 * делают; значок среди прочих значков спрятал бы главное действие туда, где
 * его ищут последним.
 *
 * ## Пока играет — аккорд в полную величину
 *
 * Имя кеглем в сорок точек и хват под ним: их читают от гитары, с расстояния
 * вытянутой руки и краем глаза. Тот же кегль, каким имена набраны в ленте,
 * с этого расстояния не читается вовсе.
 *
 * Хват тот же, что и в ряду выше, и это не повтор: там их разучивают все
 * подряд до игры, здесь показан ровно один — тот, который нужен сию секунду.
 * Отправлять человека со струнами в руках искать нужную картинку в ряду
 * значило бы отправлять его туда каждые две секунды.
 *
 * Следующий аккорд — именем, без хвата: до него ещё есть время, и хват он
 * покажет сам, когда придёт его черёд. Два хвата рядом спорили бы за то,
 * какой из них брать.
 */
@Composable
private fun Stand(
    playing: Boolean,
    here: EchoChord?,
    ahead: EchoChord?,
    instrument: EchoInstrument,
    onToggle: () -> Unit,
) {
    if (!playing) {
        EchoPill(
            label = "Сыграть вместе",
            chosen = true,
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NightPanelSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Sunset)
                .clickable(onClick = onToggle),
        ) {
            Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = "Остановить",
                tint = Night,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = here?.name ?: "…",
                fontFamily = FontFamily.Serif,
                fontSize = 40.sp,
                color = Sunset,
                maxLines = 1,
            )
            Text(
                text = ahead?.let { "дальше · ${it.name}" } ?: "дальше ничего",
                style = MaterialTheme.typography.labelMedium,
                color = NightMuted,
                maxLines = 1,
            )
        }

        if (here != null) {
            ChordShape(chord = here, instrument = instrument)
        }
    }
}

/**
 * Строчка песни: время слева и четыре аккорда за ним.
 *
 * [playing] — место играющего аккорда в этой строчке; −1, если играет не
 * здесь. Подсвечен один аккорд, а не вся строчка: строчка — это четыре такта,
 * и подсветить её целиком значило бы сказать «где-то тут», то есть ничего.
 */
@Composable
private fun ChordLine(bar: List<EchoChordSpan>, playing: Int, onPlayAt: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration(bar.first().fromMs),
            style = MaterialTheme.typography.labelMedium,
            color = NightMuted,
            modifier = Modifier
                .width(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onPlayAt(bar.first().fromMs) }
                .padding(vertical = 6.dp),
        )
        bar.forEachIndexed { at, span ->
            val lit = at == playing
            Text(
                text = span.chord.name,
                fontFamily = FontFamily.Serif,
                fontSize = 18.sp,
                color = if (lit) Sunset else NightInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (lit) NightPanelSoft else Color.Transparent)
                    .clickable { onPlayAt(span.fromMs) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            )
        }
        // Неполная последняя строчка не растягивается: четвёртый аккорд
        // должен стоять на месте четвёртого и в ней тоже.
        repeat(IN_LINE - bar.size) { Box(modifier = Modifier.weight(1f)) }
    }
}

/** Один хват: имя сверху, картинка под ним. */
@Composable
private fun ChordShape(chord: EchoChord, instrument: EchoInstrument) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanelSoft)
            .border(1.dp, NightBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = chord.name,
            fontFamily = FontFamily.Serif,
            fontSize = 20.sp,
            color = Sunset,
        )

        if (instrument == EchoInstrument.KEYS) {
            Keyboard(chord = chord, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = keysOf(chord).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = NightMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            val frets = fingering(chord, instrument)
            FretBoard(frets = frets, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = fretWord(frets),
                style = MaterialTheme.typography.labelMedium,
                color = NightMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Гриф: струны вниз, лады поперёк.
 *
 * Рисуется, а не собирается из кружков и палок разметкой: сетка ладов — это
 * десяток линий и шесть точек, и разметкой это было бы полсотни элементов на
 * каждый хват, пересчитываемых при каждой перерисовке.
 *
 * Показываются четыре лада — столько, сколько накрывает рука. Хват, стоящий
 * выше первого лада, показан со своего: сетка с четырьмя пустыми ладами
 * сверху и точками у нижнего края врала бы о том, где эту руку держать.
 */
@Composable
private fun FretBoard(frets: List<Int>, modifier: Modifier = Modifier) {
    val played = frets.filter { it > 0 }
    val base = if (played.isEmpty() || played.max() <= SHOWN) 1 else played.min()

    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        if (base > 1) {
            Text(
                text = "$base",
                style = MaterialTheme.typography.labelSmall,
                color = NightMuted,
                modifier = Modifier.padding(end = 3.dp, top = 16.dp),
            )
        }

        Canvas(
            modifier = Modifier
                .width((frets.size * 13).dp)
                .height(78.dp),
        ) {
            val step = size.width / (frets.size - 1)
            val top = size.height * 0.16f
            val board = size.height - top
            val row = board / SHOWN
            val dot = step * 0.32f

            // Верхняя черта толще прочих, когда хват стоит у самого порожка:
            // это и есть порожек, и по нему видно, что лад первый.
            drawLine(
                color = if (base == 1) NightInk else NightBorder,
                start = Offset(0f, top),
                end = Offset(size.width, top),
                strokeWidth = if (base == 1) 4f else 2f,
            )
            for (fret in 1..SHOWN) {
                drawLine(
                    color = NightBorder,
                    start = Offset(0f, top + row * fret),
                    end = Offset(size.width, top + row * fret),
                    strokeWidth = 2f,
                )
            }
            for (string in frets.indices) {
                drawLine(
                    color = NightBorder,
                    start = Offset(step * string, top),
                    end = Offset(step * string, size.height),
                    strokeWidth = 2f,
                )
            }

            frets.forEachIndexed { string, fret ->
                val at = step * string
                when {
                    fret < 0 -> {
                        // Крестик над струной: её не трогают.
                        val arm = dot * 0.7f
                        drawLine(
                            color = NightMuted,
                            start = Offset(at - arm, top * 0.25f),
                            end = Offset(at + arm, top * 0.25f + arm * 2),
                            strokeWidth = 3f,
                        )
                        drawLine(
                            color = NightMuted,
                            start = Offset(at + arm, top * 0.25f),
                            end = Offset(at - arm, top * 0.25f + arm * 2),
                            strokeWidth = 3f,
                        )
                    }

                    fret == 0 -> drawCircle(
                        color = NightMuted,
                        radius = dot * 0.6f,
                        center = Offset(at, top * 0.45f),
                        style = Stroke(width = 3f),
                    )

                    else -> {
                        val place = fret - base
                        if (place in 0 until SHOWN) {
                            drawCircle(
                                color = Sunset,
                                radius = dot,
                                center = Offset(at, top + row * (place + 0.5f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Клавиши: октава, и в ней горят три ноты аккорда.
 *
 * Одна октава, а не две: аккорд — это три названия нот, и какими октавами их
 * брать, решает рука. Вторая октава добавила бы к картинке ровно повтор.
 */
@Composable
private fun Keyboard(chord: EchoChord, modifier: Modifier = Modifier) {
    val notes = chord.notes.toSet()

    Canvas(modifier = modifier.width(112.dp).height(62.dp)) {
        val white = size.width / WHITE.size
        val black = white * 0.62f

        WHITE.forEachIndexed { at, note ->
            drawRect(
                color = if (note in notes) Sunset else Color.White.copy(alpha = 0.86f),
                topLeft = Offset(white * at, 0f),
                size = Size(white - 1.5f, size.height),
            )
        }
        BLACK_AFTER.forEachIndexed { at, after ->
            val note = BLACK[at]
            drawRect(
                color = if (note in notes) Sunset else NightPanel,
                topLeft = Offset(white * (after + 1) - black / 2, 0f),
                size = Size(black, size.height * 0.62f),
            )
        }
    }
}

/** Хват словами — тем же, чем его пишут в песеннике: `x32010`. */
private fun fretWord(frets: List<Int>): String = frets.joinToString("") { fret ->
    when {
        fret < 0 -> "x"
        fret < 10 -> "$fret"
        else -> "(${fret})"
    }
}

private fun chordsWord(count: Int): String {
    val hundred = count % 100
    val ten = count % 10
    return when {
        hundred in 11..14 -> "$count аккордов"
        ten == 1 -> "$count аккорд"
        ten in 2..4 -> "$count аккорда"
        else -> "$count аккордов"
    }
}

/** Сколько аккордов стоит в строчке — столько же, сколько тактов в песеннике. */
private const val IN_LINE = 4

/**
 * Сколько в ленте стоит перед строчками песни: тональность, инструменты,
 * хваты и подпись «Как идёт».
 *
 * Числом, а не поиском по ключу: прокрутка до играющей строчки считается
 * восемь раз в секунду, и обходить ради неё список было бы расточительством.
 * Меняется оно ровно тогда, когда что-то добавляют выше, — и тогда его нужно
 * поправить здесь.
 */
private const val HEADS = 4

/** Сколько ладов показывает гриф: столько накрывает рука. */
private const val SHOWN = 4

/** Белые клавиши октавы полутонами. */
private val WHITE = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/** Чёрные — и после какой белой каждая стоит. */
private val BLACK = intArrayOf(1, 3, 6, 8, 10)
private val BLACK_AFTER = intArrayOf(0, 1, 3, 4, 5)
