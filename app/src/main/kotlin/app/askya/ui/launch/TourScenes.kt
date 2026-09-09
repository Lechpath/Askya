package app.askya.ui.launch

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.R
import app.askya.ui.components.DayPart
import app.askya.ui.components.DayPartIcon
import app.askya.ui.echo.sunsetBackground
import app.askya.ui.navigation.Destination
import app.askya.ui.theme.Accent
import app.askya.ui.theme.AccentInk
import app.askya.ui.theme.AccentSoft
import app.askya.ui.theme.CardWhite
import app.askya.ui.theme.Cream
import app.askya.ui.theme.CoralSoft
import app.askya.ui.theme.PaperMuted
import app.askya.ui.theme.CoralInk
import app.askya.ui.theme.CoralAccent
import app.askya.ui.theme.FlowerInk
import app.askya.ui.theme.Ink
import app.askya.ui.theme.Muted
import app.askya.ui.theme.Night
import app.askya.ui.theme.NightInk
import app.askya.ui.theme.NightMuted
import app.askya.ui.theme.NightPanel
import app.askya.ui.theme.NightPanelSoft
import app.askya.ui.theme.Sunset
import app.askya.ui.theme.cardEdge
import app.askya.ui.theme.cardShade
import kotlinx.coroutines.delay

/**
 * Раздел, показанный изнутри и в движении.
 *
 * ## Почему сцена, а не строка меню
 *
 * Подсвеченная строка меню отвечает на вопрос «где это лежит», но не на тот, с
 * которым знакомство и читают: «а что там». Слово «AskyaEcho» на седьмом
 * экране подряд ничем не отличается от слова «AskyaV» — оба одинаково ничего
 * не показывают.
 *
 * Поэтому раздел на своём шаге **открывается**: на месте меню встаёт его
 * экран, собранный из тех же карточек, тех же красок и той же темноты, что и
 * настоящий, — и сам себя проигрывает.
 *
 * ## Два кадра
 *
 * У плеера, видео и денег экран, которым раздел встречает, и экран, ради
 * которого в него заходят, — разные. Echo спрашивает «что поставить?» и лишь
 * потом играет; AskyaV сперва показывает, что снято, и лишь потом крутит
 * кино; Ledger открывается счетами, а месяц лежит следующей страницей.
 *
 * Показывать один из двух значило бы соврать наполовину, поэтому сцена сама
 * перелистывается: первый кадр — то, что человек увидит, войдя; через пару
 * секунд — то, что будет дальше.
 *
 * ## Почему это макет, а не живой раздел
 *
 * Живой показал бы пустоту: на первом запуске нет ни дел, ни картинок, ни
 * трат, и знакомство началось бы с шести пустых экранов подряд. Макет
 * показывает **обжитой** раздел — тот, каким он станет через неделю, — и
 * ровно этим отвечает на «что можно делать».
 *
 * Ничего настоящего он при этом не трогает: ни базы, ни файлов, ни сети. А то,
 * что можно взять у самого приложения, взято у него: знаки частей дня
 * ([DayPartIcon]), закатный фон Echo ([sunsetBackground]) и его же
 * слово-полоса — те же самые.
 */
@Composable
internal fun SectionScene(destination: Destination, modifier: Modifier = Modifier) {
    // Плеер и видео живут в темноте всегда — и в знакомстве тоже: это их лицо,
    // а не настройка.
    val dark = destination == Destination.ECHO || destination == Destination.VIDEO

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(SCENE)
            .cardShade(SCENE_SHAPE, elevation = 6.dp)
            .clip(SCENE_SHAPE)
            .then(
                when (destination) {
                    // Закат из правого верхнего угла — тот же, что в разделе.
                    Destination.ECHO -> Modifier.sunsetBackground()
                    Destination.VIDEO -> Modifier.background(Night)
                    else -> Modifier.background(Cream)
                },
            )
            .cardEdge(SCENE_SHAPE),
    ) {
        SceneHeader(destination = destination, dark = dark)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            when (destination) {
                Destination.TODAY -> DayScene()
                Destination.NOTES -> ScrollScene()
                Destination.ECHO -> EchoScene()
                Destination.VIDEO -> VideoScene()
                Destination.LEDGER -> LedgerScene()
            }
        }
    }
}

/** Шапка раздела — та же, что в приложении: цветок, имя пером, знак справа. */
@Composable
private fun SceneHeader(destination: Destination, dark: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_flower),
            contentDescription = null,
            tint = if (dark) Sunset else FlowerInk,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = destination.label,
            fontFamily = FontFamily.Serif,
            fontSize = 19.sp,
            letterSpacing = (-0.3).sp,
            color = if (dark) NightInk else Ink,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Icon(
            painter = painterResource(destination.icon),
            contentDescription = null,
            tint = if (dark) NightMuted else Muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// AskyaDay
// ---------------------------------------------------------------------------

/**
 * День — тот же, что в разделе: карточки по три в ряд, разложенные по частям
 * дня, и «new card» в углу.
 *
 * Строкой дела не показываются нигде в Askya, и в знакомстве тем более не
 * должны: человек, увидевший здесь список строк, пришёл бы в раздел к другому
 * экрану. Поэтому карточка мельче настоящей, но собрана как настоящая — знак,
 * галочка в углу, время, название, «плюс» и колокольчик снизу.
 */
@Composable
private fun ColumnScope.DayScene() {
    val show = entrance(1300)

    // Первое дело отмечается сделанным само: это главное, что с карточкой
    // делают, и увидеть это лучше, чем прочитать.
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1600)
        done = true
    }

    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
            ) {
                Text(
                    text = "Пятница, 4 сентября",
                    style = MaterialTheme.typography.labelMedium,
                    color = Muted,
                )
                Text(
                    text = "Взять из списка",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Text(
                    text = "Очистить",
                    style = MaterialTheme.typography.labelMedium,
                    color = Accent,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            PartTitle(part = DayPart.MORNING)
            DeedRow(deeds = MORNING, show = show, from = 0, ticked = if (done) 0 else -1)

            Spacer(Modifier.height(10.dp))

            PartTitle(part = DayPart.DAY)
            DeedRow(deeds = AFTERNOON, show = show, from = MORNING.size, ticked = -1)
        }

        NewCardPill(modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** Подпись части дня: живой знак и слово засечными, как в разделе. */
@Composable
private fun PartTitle(part: DayPart) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        DayPartIcon(part = part, tint = Ink, size = 15.dp, modifier = Modifier.padding(end = 6.dp))
        Text(
            text = part.title,
            fontFamily = FontFamily.Serif,
            fontSize = 15.sp,
            letterSpacing = (-0.3).sp,
            color = Ink,
        )
    }
}

/** Ряд дня: три места в ряду, даже когда дел в нём меньше. */
@Composable
private fun DeedRow(deeds: List<Deed>, show: Float, from: Int, ticked: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        deeds.forEachIndexed { index, deed ->
            Appearing(part(show, from + index, TOTAL_DEEDS), modifier = Modifier.weight(1f)) {
                DeedCard(deed = deed, done = index == ticked)
            }
        }
        repeat(DAY_COLUMNS - deeds.size) { Spacer(Modifier.weight(1f)) }
    }
}

private class Deed(val icon: ImageVector, val time: String, val title: String)

private val MORNING = listOf(
    Deed(Icons.Outlined.WbSunny, "06:30", "Подъём"),
    Deed(Icons.Outlined.DirectionsRun, "07:00 – 07:30", "Зарядка"),
    Deed(Icons.Outlined.Restaurant, "08:15", "Завтрак"),
)

private val AFTERNOON = listOf(
    Deed(Icons.Outlined.Work, "10:30 – 15:00", "Работа"),
    Deed(Icons.Outlined.DirectionsWalk, "16:00", "Прогулка"),
)

private const val DAY_COLUMNS = 3

private val TOTAL_DEEDS = MORNING.size + AFTERNOON.size

/** Карточка дела — настоящая, только помельче. */
@Composable
private fun DeedCard(deed: Deed, done: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // requiredHeight, а не height: нижний ряд не влезает в сцену, и
            // сжатая карточка теряла бы название. Пусть лучше уходит за край
            // целой — день и на телефоне продолжается ниже экрана.
            .requiredHeight(DEED_HEIGHT)
            .cardShade(RoundedCornerShape(14.dp), elevation = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(14.dp))
            .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = deed.icon,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = if (done) Accent else Muted.copy(alpha = 0.4f),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = deed.time,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = deed.title,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp).weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(14.dp),
            )
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private val DEED_HEIGHT = 118.dp

/** Тёмная кнопка в углу — та же, что в разделах: «new card», «счёт», «запись». */
@Composable
private fun NewCardPill(modifier: Modifier = Modifier, label: String = "new card") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(bottom = 4.dp)
            .clip(CircleShape)
            .background(Ink)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Cream,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Scroll
// ---------------------------------------------------------------------------

/**
 * Лента-разговор: раздел спрашивают, приложение отвечает тем, что в нём лежит.
 * Миниатюры выкладываются на белую подложку по одной — так они и появляются
 * при первой прокрутке.
 */
@Composable
private fun ColumnScope.ScrollScene() {
    val show = entrance(1500)

    Appearing(part(show, 0, 6)) { Said(icon = R.drawable.ic_scroll_images, text = "Галерея") }
    Heard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            THUMBS.forEachIndexed { index, color ->
                Appearing(part(show, index + 1, 6), modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (index == 1) 1.25f else 1.6f)
                            .cardShade(RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(color),
                    )
                }
            }
        }
        More(text = "ещё 12")
    }

    Spacer(Modifier.height(2.dp))

    Appearing(part(show, 4, 6)) { Said(icon = R.drawable.ic_scroll_library, text = "Библиотека") }
    Heard {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp),
        ) {
            Appearing(part(show, 5, 6), modifier = Modifier.weight(1f)) {
                Spine(title = "Дом", color = Color(0xFF6E8B74))
            }
            Appearing(part(show, 5, 6), modifier = Modifier.weight(1f)) {
                Spine(title = "Работа", color = Color(0xFF8A6E93))
            }
            Appearing(part(show, 5, 6), modifier = Modifier.weight(1f)) {
                Record(title = "Список покупок")
            }
        }
    }

    Spacer(Modifier.weight(1f))

    // Та же строка, что внизу раздела: в неё пишут поиск.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(CircleShape)
            .background(CardWhite)
            .cardEdge(CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Найти — словом или #тегом",
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(15.dp),
        )
    }
}

private val THUMBS = listOf(
    Color(0xFFC7B7A3),
    Color(0xFF9FB0A6),
    Color(0xFFB9A6AE),
)

/** Сообщение человека — плашкой акцента справа, со знаком раздела. */
@Composable
private fun Said(icon: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp))
                .background(AccentSoft)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = AccentInk,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = AccentInk,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Ответ приложения — белая подложка слева, на ней лежит найденное. */
@Composable
private fun Heard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 14.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp))
            .padding(6.dp),
        content = content,
    )
}

/** Подножие ответа: показанное — не всё, и раздел открывается целиком. */
@Composable
private fun More(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 2.dp, end = 2.dp),
    ) {
        Text(
            text = "•••",
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(18.dp).clip(CircleShape).background(AccentSoft),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flower),
                contentDescription = null,
                tint = AccentInk,
                modifier = Modifier.size(9.dp),
            )
        }
    }
}

@Composable
private fun Spine(title: String, color: Color) {
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = Modifier
            .fillMaxSize()
            .cardShade(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .padding(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun Record(title: String) {
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = Modifier
            .fillMaxSize()
            .cardShade(RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// AskyaEcho
// ---------------------------------------------------------------------------

/**
 * Плеер двумя кадрами: вопрос «что поставить?», которым Echo встречает, и сам
 * плеер, в который он приводит.
 */
@Composable
private fun ColumnScope.EchoScene() {
    val playing = secondBeat()

    Crossfade(targetState = playing, animationSpec = tween(500), label = "echo") { now ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (now) EchoPlayer() else EchoChooser()
        }
    }
}

/** Первый кадр: ответы на вопрос, с которым в плеер и приходят. */
@Composable
private fun ColumnScope.EchoChooser() {
    val show = entrance(900)

    Text(
        text = "Что поставить?",
        fontFamily = FontFamily.Serif,
        fontSize = 18.sp,
        color = NightInk,
        modifier = Modifier.padding(top = 2.dp),
    )
    Text(
        text = "500 песен на телефоне",
        style = MaterialTheme.typography.labelSmall,
        color = NightMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    WAYS.chunked(2).forEachIndexed { row, pair ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        ) {
            pair.forEachIndexed { column, way ->
                Appearing(part(show, row * 2 + column, WAYS.size), modifier = Modifier.weight(1f)) {
                    WayCard(way)
                }
            }
        }
    }
}

private class Way(
    val icon: ImageVector,
    val title: String,
    val hint: String,
    val count: String,
)

private val WAYS = listOf(
    Way(Icons.Outlined.PlayCircleOutline, "Продолжить", "На чём остановились", "Тихий вечер"),
    Way(Icons.Outlined.LibraryMusic, "Вся музыка", "Всё, что на телефоне", "500 песен"),
    Way(Icons.Outlined.QueueMusic, "Плейлист", "Свой порядок песен", "2 плейлиста"),
    Way(Icons.Outlined.FolderOpen, "Папка", "Как лежит на телефоне", "6 на телефоне"),
)

@Composable
private fun WayCard(way: Way) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightPanel)
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NightPanelSoft),
        ) {
            Icon(
                imageVector = way.icon,
                contentDescription = null,
                tint = Sunset,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = way.title,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = NightInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = way.hint,
            style = MaterialTheme.typography.labelSmall,
            color = NightMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = way.count,
            style = MaterialTheme.typography.labelSmall,
            color = Sunset,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Второй кадр: обложка дышит в такт, слово-полоса отсчитывает песню. */
@Composable
private fun ColumnScope.EchoPlayer() {
    val forever = rememberInfiniteTransition(label = "echo")

    val beat by forever.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(540), RepeatMode.Reverse),
        label = "beat",
    )
    val played by forever.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "played",
    )

    Spacer(Modifier.weight(0.5f))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .size(104.dp)
            .graphicsLayer {
                scaleX = beat
                scaleY = beat
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Sunset.copy(alpha = 0.85f)),
    ) {
        Icon(
            painter = painterResource(Destination.ECHO.icon),
            contentDescription = null,
            tint = Night.copy(alpha = 0.75f),
            modifier = Modifier.size(38.dp),
        )
    }

    Text(
        text = "Тихий вечер",
        fontFamily = FontFamily.Serif,
        fontSize = 17.sp,
        color = NightInk,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    Text(
        text = "Ваша музыка",
        style = MaterialTheme.typography.labelSmall,
        color = NightMuted,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth(),
    )

    // Полоса времени — то самое слово «AskyaEcho», закрашиваемое закатом.
    Word(progress = played, modifier = Modifier.align(Alignment.CenterHorizontally))

    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.SkipPrevious,
            contentDescription = null,
            tint = NightInk,
            modifier = Modifier.size(22.dp),
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Sunset),
        ) {
            Icon(
                imageVector = Icons.Filled.Pause,
                contentDescription = null,
                tint = Night,
                modifier = Modifier.size(20.dp),
            )
        }
        Icon(
            imageVector = Icons.Outlined.SkipNext,
            contentDescription = null,
            tint = NightInk,
            modifier = Modifier.size(22.dp),
        )
    }

    Spacer(Modifier.weight(1f))

    // Эквалайзер — то, что в Echo правят на месте, а не в настройках.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 8.dp)
            .clip(CircleShape)
            .background(NightPanel)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            tint = Sunset,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "EQ",
            style = MaterialTheme.typography.labelSmall,
            color = NightInk,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * Слово-полоса: имя раздела, написанное пером, закрашивается по мере того, как
 * идёт песня. Тот же приём и тот же знак, что в самом плеере.
 */
@Composable
private fun Word(progress: Float, modifier: Modifier = Modifier) {
    val word = painterResource(R.drawable.ic_wordmark_echo)
    val height = 24.dp
    val width = height * (word.intrinsicSize.width / word.intrinsicSize.height) * 1.6f

    Box(modifier = modifier.padding(top = 12.dp).width(width).height(height)) {
        Image(
            painter = word,
            contentDescription = null,
            colorFilter = ColorFilter.tint(NightMuted.copy(alpha = 0.32f)),
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = word,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Sunset),
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(right = size.width * progress) {
                        this@drawWithContent.drawContent()
                    }
                },
        )
    }
}

// ---------------------------------------------------------------------------
// AskyaV
// ---------------------------------------------------------------------------

/** Видео двумя кадрами: что снято — и как это смотрят. */
@Composable
private fun ColumnScope.VideoScene() {
    val watching = secondBeat()

    Crossfade(targetState = watching, animationSpec = tween(500), label = "video") { now ->
        Column(modifier = Modifier.fillMaxSize()) {
            if (now) VideoPlayer() else VideoShelf()
        }
    }
}

/** Первый кадр: полки раздела и то, что на них лежит. */
@Composable
private fun ColumnScope.VideoShelf() {
    val show = entrance(900)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
    ) {
        VideoTab(text = "Всё", lit = true)
        VideoTab(text = "Плейлисты", lit = false)
        VideoTab(text = "Папки", lit = false)
    }

    CLIPS.chunked(3).forEachIndexed { row, shelf ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        ) {
            shelf.forEachIndexed { column, clip ->
                Appearing(part(show, row * 3 + column, CLIPS.size), modifier = Modifier.weight(1f)) {
                    VideoTile(clip)
                }
            }
        }
    }
}

@Composable
private fun VideoTab(text: String, lit: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (lit) Night else NightInk,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (lit) Sunset else NightPanel)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private class Clip(val title: String, val meta: String, val tint: Color)

private val CLIPS = listOf(
    Clip("Река", "0:21 · 1920×1080", Color(0xFF3E5266)),
    Clip("Горы", "0:13 · 1920×1080", Color(0xFF4A4A44)),
    Clip("Вечер", "2:45 · 1280×720", Color(0xFF5A4550)),
    Clip("Дорога", "1:04 · 1920×1080", Color(0xFF44515A)),
    Clip("Двор", "0:38 · 1280×720", Color(0xFF4F5744)),
    Clip("Снег", "0:12 · 1920×1080", Color(0xFF56505E)),
)

@Composable
private fun VideoTile(clip: Clip) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(132.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NightPanel),
    ) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(clip.tint))
        Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text(
                text = clip.title,
                fontFamily = FontFamily.Serif,
                fontSize = 12.sp,
                color = NightInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = clip.meta,
                style = MaterialTheme.typography.labelSmall,
                color = NightMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Второй кадр: кадр, бегунок, жесты по краям и лаборатория внизу. */
@Composable
private fun ColumnScope.VideoPlayer() {
    val forever = rememberInfiniteTransition(label = "player")

    val played by forever.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "played",
    )
    val light by forever.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "light",
    )
    val loud by forever.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "loud",
    )

    Spacer(Modifier.weight(0.4f))

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Gesture(icon = Icons.Outlined.WbSunny, fill = light)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(12.dp))
                .background(NightPanel),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = NightInk,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = "— а дальше что?",
                style = MaterialTheme.typography.labelSmall,
                color = NightInk,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }

        Gesture(icon = Icons.Outlined.GraphicEq, fill = loud)
    }

    Spacer(Modifier.height(10.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(NightPanelSoft),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(played)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Sunset),
        )
    }

    Spacer(Modifier.weight(1f))

    // Лаборатория: то, что делают с кадром, не выходя из плеера.
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Tool(icon = Icons.Outlined.ContentCut, label = "вырезать")
        Tool(icon = Icons.Outlined.ScreenRotation, label = "повернуть")
        Tool(icon = Icons.Outlined.PhotoCamera, label = "кадр")
    }
}

/** Край экрана, по которому ведут пальцем: яркость слева, громкость справа. */
@Composable
private fun Gesture(icon: ImageVector, fill: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NightMuted,
            modifier = Modifier.size(14.dp),
        )
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .padding(top = 5.dp)
                .width(5.dp)
                .height(74.dp)
                .clip(CircleShape)
                .background(NightPanelSoft),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fill)
                    .clip(CircleShape)
                    .background(Sunset),
            )
        }
    }
}

@Composable
private fun Tool(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NightPanel)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Sunset,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NightInk,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Ledger
// ---------------------------------------------------------------------------

/**
 * Деньги двумя кадрами — теми же двумя страницами, что листаются в разделе:
 * счета, на которых лежит, и месяц, в котором это тратится.
 */
@Composable
private fun ColumnScope.LedgerScene() {
    val month = secondBeat()

    Crossfade(targetState = month, animationSpec = tween(500), label = "ledger") { now ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (now) LedgerMonth() else LedgerAccounts()
            }
            NewCardPill(
                modifier = Modifier.align(Alignment.BottomEnd),
                label = if (now) "запись" else "счёт",
            )
        }
    }
}

/** Первый кадр: страница счетов, которой раздел и открывается. */
@Composable
private fun ColumnScope.LedgerAccounts() {
    val show = entrance(900)

    PageTitle(title = "Счета", at = 0)

    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Total(sum = "23 700 ₽", what = "всего", modifier = Modifier.weight(1f))
        Total(sum = "0 ₽", what = "долг", modifier = Modifier.weight(1f))
    }

    PURSES.chunked(3).forEachIndexed { row, shelf ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
        ) {
            shelf.forEachIndexed { column, purse ->
                Appearing(
                    progress = part(show, row * 3 + column, PURSES.size),
                    modifier = Modifier.weight(1f),
                ) {
                    PurseCard(purse)
                }
            }
            repeat(3 - shelf.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Total(sum: String, what: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .cardShade(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = sum,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Ink,
            maxLines = 1,
        )
        Text(
            text = what,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            maxLines = 1,
        )
    }
}

private class Purse(
    val icon: ImageVector,
    val title: String,
    val kind: String,
    val sum: String,
    val stripe: Color,
)

private val PURSES = listOf(
    Purse(Icons.Outlined.Payments, "Кошелёк", "Наличные", "3 200 ₽", Color(0xFF6B4A2F)),
    Purse(Icons.Outlined.CreditCard, "Карта", "Карта", "18 500 ₽", Sunset),
    Purse(Icons.Outlined.Savings, "Копилка", "Накопления", "2 000 ₽", Color(0xFF2F6B45)),
    Purse(Icons.Outlined.CreditCard, "Кредитка", "Кредитная карта", "0 ₽", Color(0xFF6B4A93)),
    Purse(Icons.Outlined.Payments, "Отпуск", "Накопления", "5 000 ₽", Color(0xFF3A5E8C)),
)

/** Карточка счёта: полоска краски сверху, знак, имя и остаток снизу. */
@Composable
private fun PurseCard(purse: Purse) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(104.dp)
            .cardShade(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(12.dp)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(purse.stripe))
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Icon(
                imageVector = purse.icon,
                contentDescription = null,
                tint = purse.stripe,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = purse.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = purse.kind,
                style = MaterialTheme.typography.labelSmall,
                color = Muted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = purse.sum,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Второй кадр: месяц одной страницей — пришло, ушло, осталось и статьи. */
@Composable
private fun ColumnScope.LedgerMonth() {
    val show = entrance(1100)

    PageTitle(title = "Доход/Расход", at = 1)

    // Переключатель месяца — той же белой полосой со стрелками по краям.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .cardShade(RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text = "‹", style = MaterialTheme.typography.bodyMedium, color = Muted)
        Text(
            text = "Сентябрь 2026",
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(text = "›", style = MaterialTheme.typography.bodyMedium, color = Muted)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Total(sum = "42 000 ₽", what = "Пришло", modifier = Modifier.weight(1f))
        Total(sum = "18 300 ₽", what = "Ушло", modifier = Modifier.weight(1f))
        Total(sum = "23 700 ₽", what = "Осталось", modifier = Modifier.weight(1f))
    }

    Text(
        text = "Расходы по статьям",
        fontFamily = FontFamily.Serif,
        fontSize = 15.sp,
        color = Ink,
        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
    )

    SPENDINGS.forEachIndexed { index, spending ->
        Appearing(part(show, index, SPENDINGS.size)) {
            SpendingRow(title = spending.first, sum = spending.second, tint = SPEND_COLORS[index])
        }
        Spacer(Modifier.height(5.dp))
    }
}

private val SPENDINGS = listOf(
    "Еда" to "6 400 ₽",
    "Дом" to "4 900 ₽",
    "Дорога" to "2 100 ₽",
)

private val SPEND_COLORS = listOf(
    Color(0xFFB0603F),
    Color(0xFF4A6B8A),
    Color(0xFF8A6E93),
)

@Composable
private fun SpendingRow(title: String, sum: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .cardShade(RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(CardWhite)
            .cardEdge(RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(tint))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        Text(
            text = sum,
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            maxLines = 1,
        )
    }
}

/** Название страницы и точки: сколько их у раздела и где мы. */
@Composable
private fun PageTitle(title: String, at: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp),
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.Serif,
            fontSize = 17.sp,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(LEDGER_PAGES) { page ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (page == at) Accent else Muted.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

/** Страниц у Ledger три: счета, месяц и статистика. */
private const val LEDGER_PAGES = 3

// ---------------------------------------------------------------------------
// Общее
// ---------------------------------------------------------------------------

/**
 * Второй кадр сцены: раздел сперва показывает, чем встречает, потом — то, ради
 * чего в него заходят. Пауза не короче двух секунд: кадр, сменившийся раньше,
 * читается как подмена, а не как переход.
 */
@Composable
private fun secondBeat(after: Long = 2400): Boolean {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(after)
        on = true
    }
    return on
}

/**
 * Въезд одной вещи: прозрачность и небольшой подъём снизу.
 *
 * Подъём маленький: карточка, приезжающая через пол-экрана, рассказывает о
 * себе, а не о разделе.
 */
@Composable
private fun Appearing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .alpha(progress)
            .graphicsLayer { translationY = (1f - progress) * 22f },
        content = { content() },
    )
}

/**
 * Ход сцены от нуля к единице — один раз за показ.
 *
 * Возвращается числом, а не состоянием: части сцены берут из него свою долю
 * ([part]), и очередь появления получается сама, без таймеров на каждую
 * карточку.
 */
@Composable
private fun entrance(millis: Int = 1100): Float {
    val run = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        run.animateTo(1f, animationSpec = tween(millis, easing = LinearEasing))
    }
    return run.value
}

/** Доля общего хода, приходящаяся на [index]-ю вещь из [count]. */
private fun part(progress: Float, index: Int, count: Int): Float =
    (progress * count - index).coerceIn(0f, 1f)

/** Ростом со средний экран: сцена должна быть похожа на раздел, а не на значок. */
private val SCENE = 360.dp

private val SCENE_SHAPE = RoundedCornerShape(28.dp)
