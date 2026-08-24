package app.askya.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DirectionsBike
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.BlockIcons
import java.time.LocalTime

/**
 * Карточка дела: знак, время, название и ряд действий снизу.
 *
 * Одна на два экрана — расписание дня и список дел, из которого день
 * собирается. Это одна и та же вещь в двух видах: то, что человек завёл в
 * списке, назавтра стоит в дне. Выглядеть по-разному они не должны, иначе
 * связь между экранами приходится держать в голове.
 *
 * Различается только низ карточки — [actions]: в дне это заметка и
 * напоминание, в списке дел переключатель «участвует ли дело в сборке».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockCard(
    icon: ImageVector,
    time: String,
    title: String,
    titleColor: Color,
    metaColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Та же тень, что у кнопки «+ new card»: шесть точек — стандартный
        // подъём плавающей кнопки в Material 3, и карточки с ней читаются как
        // один слой, лежащий над страницей.
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        // Ширину задаёт ряд — все карточки в нём одинаковые. Высота своя и
        // фиксированная: карточка вытянута вниз, и от длины названия её рост
        // меняться не должен, иначе строка идёт лесенкой.
        modifier = modifier.height(CARD_HEIGHT),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 2.dp, top = 12.dp, bottom = 2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = metaColor,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = time,
                style = MaterialTheme.typography.bodyLarge,
                color = metaColor,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
                color = titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp).weight(1f),
            )

            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

private val CARD_HEIGHT = 212.dp

/** Сколько карточек помещается в строку. Три — как на эскизе. */
private const val COLUMNS = 3

/**
 * Сетка карточек: рядами по трое, поровну по ширине экрана.
 *
 * Не поместившиеся переносятся на следующую строку и сдвигают вниз всё, что
 * ниже. Прокрутка только вертикальная — горизонтальная лента прятала бы дела
 * за краем экрана, а эти списки смотрят, чтобы увидеть всё сразу.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> CardGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    card: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = COLUMNS,
        modifier = modifier.fillMaxWidth(),
    ) {
        items.forEach { item -> card(item, Modifier.weight(1f)) }
        // Хвост последней строки добирается пустотой: без этого две карточки
        // растянулись бы на всю ширину и оказались бы вдвое шире соседних
        // сверху.
        val tail = items.size % COLUMNS
        if (tail != 0) {
            repeat(COLUMNS - tail) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Подпись части дня над сеткой.
 *
 * Тем же засечным шрифтом, что «AskyaDay» в шапке: части дня — это подзаголовки
 * страницы, а не подписи к строчкам списка.
 */
@Composable
fun DayPartTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = FontFamily.Serif,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(bottom = 10.dp),
    )
}

/**
 * Часть дня: утро, день, вечер.
 *
 * Границы взяты по человеческой мерке, а не по трети суток: утро кончается в
 * десять, когда начинается работа, вечер начинается в шесть, когда она
 * кончается. Ночные дела попадают в вечер — заводить четвёртую часть ради
 * двух дел в году незачем.
 */
enum class DayPart(val title: String) {
    MORNING("Утро"),
    DAY("День"),
    EVENING("Вечер"),
    ;

    companion object {
        fun of(time: LocalTime): DayPart = when {
            time < LocalTime.of(10, 0) -> MORNING
            time < LocalTime.of(18, 0) -> DAY
            else -> EVENING
        }
    }
}

/**
 * Знак дела: выбранный руками, а если не выбирали — угаданный по названию.
 */
fun blockIconOf(title: String, chosen: BlockIcon? = null): ImageVector =
    blockIcon(chosen ?: BlockIcons.of(title))

/** Общее соответствие: им пользуются и маленькая карточка, и раскрытая. */
fun blockIcon(icon: BlockIcon): ImageVector = when (icon) {
    BlockIcon.WAKE -> Icons.Outlined.WbSunny
    BlockIcon.SLEEP -> Icons.Outlined.Bedtime
    BlockIcon.SHOWER -> Icons.Outlined.Shower
    BlockIcon.FOOD -> Icons.Outlined.Restaurant
    BlockIcon.COFFEE -> Icons.Outlined.LocalCafe
    BlockIcon.COOK -> Icons.Outlined.SoupKitchen
    BlockIcon.WATER -> Icons.Outlined.LocalDrink
    BlockIcon.HEALTH -> Icons.Outlined.MedicalServices

    BlockIcon.WORK -> Icons.Outlined.Work
    BlockIcon.CODE -> Icons.Outlined.Code
    BlockIcon.STUDY -> Icons.Outlined.School
    BlockIcon.READ -> Icons.Outlined.MenuBook
    BlockIcon.WRITE -> Icons.Outlined.Create
    BlockIcon.CALL -> Icons.Outlined.Call
    BlockIcon.MAIL -> Icons.Outlined.MailOutline
    BlockIcon.CHAT -> Icons.Outlined.ChatBubbleOutline
    BlockIcon.MONEY -> Icons.Outlined.Payments

    BlockIcon.ROAD -> Icons.Outlined.DirectionsCar
    BlockIcon.WALK -> Icons.Outlined.DirectionsWalk
    BlockIcon.BIKE -> Icons.Outlined.DirectionsBike
    BlockIcon.FLIGHT -> Icons.Outlined.Flight
    BlockIcon.SPORT -> Icons.Outlined.DirectionsRun

    BlockIcon.HOUSE -> Icons.Outlined.Home
    BlockIcon.CLEAN -> Icons.Outlined.CleaningServices
    BlockIcon.LAUNDRY -> Icons.Outlined.LocalLaundryService
    BlockIcon.REPAIR -> Icons.Outlined.Handyman
    BlockIcon.SHOP -> Icons.Outlined.ShoppingBasket
    BlockIcon.PLANT -> Icons.Outlined.LocalFlorist

    BlockIcon.MEET -> Icons.Outlined.Group
    BlockIcon.CHILD -> Icons.Outlined.ChildCare
    BlockIcon.PETS -> Icons.Outlined.Pets
    BlockIcon.GIFT -> Icons.Outlined.CardGiftcard
    BlockIcon.SERVICE -> Icons.Outlined.VolunteerActivism

    BlockIcon.REST -> Icons.Outlined.SelfImprovement
    BlockIcon.MUSIC -> Icons.Outlined.MusicNote
    BlockIcon.MOVIE -> Icons.Outlined.Movie
    BlockIcon.GAME -> Icons.Outlined.SportsEsports
    BlockIcon.PHOTO -> Icons.Outlined.PhotoCamera
    BlockIcon.NATURE -> Icons.Outlined.Park

    BlockIcon.PLAIN -> Icons.Outlined.Schedule
}
