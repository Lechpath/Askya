package app.askya.ui.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Тёплый кремовый лист, белые карточки — бумага Askya. Это не «светлая тема» в
// смысле Material, а бумага, на которой всё написано; тёмная ниже — та же
// комната при выключенном свете, а не другое приложение.
//
// Имена с приставкой Paper — сами краски, буквами шестнадцатеричного числа.
// Ниже те же слова без приставки заведены снова, но уже как **роли**: они
// читают выбранную тему и потому годятся только внутри разметки. Разделение
// нужно ровно затем, чтобы сама тема было из чего собрана: роль, читающая
// тему, из которой она же собирается, — круг.
val PaperCream = Color(0xFFFAF9F5)
val PaperPanel = Color(0xFFF0EEE6)
val PaperCard = Color(0xFFFFFFFF)
val PaperBorder = Color(0xFFE3E1D8)
val PaperInk = Color(0xFF1F1E1B)
val PaperMuted = Color(0xFF6F6D64)
val PaperDanger = Color(0xFFC0392B)

// Коралловый акцент — исходная краска Askya и первая из гаммы.
val CoralAccent = Color(0xFFD97757)
val CoralSoft = Color(0xFFF6E5DE)
val CoralInk = Color(0xFF8A4B32)

// Режим дня — собственная сигнальная шкала, а не роли Material: светофор нельзя
// свести к primary/error, не потеряв смысл цвета. Гамму он не слушает по той же
// причине: красный, пожелтевший вслед за выбранной гаммой, — уже не красный.
val ModeRed = Color(0xFFB33C2E)
val ModeRedSoft = Color(0xFFF7E3DF)
val ModeYellow = Color(0xFF9A7318)
val ModeGreen = Color(0xFF3E7A4F)
val ModeGreenSoft = Color(0xFFE5EFE5)

// Корешки книг. Три своих тона к тем, что уже есть в палитре: у книги цвет не
// роль и не сигнал, а способ узнать её на полке в лицо, и восьми хватает,
// чтобы соседние книги не путались. Гамме не подчиняются: перекрасить полку
// вслед за настройкой значило бы отобрать у книг лицо.
val SpineRose = Color(0xFFB0526B)
val SpineBlue = Color(0xFF3E6B8A)
val SpinePlum = Color(0xFF6B4F86)

/**
 * Когда приложению быть тёмным.
 *
 * Тремя ответами, а не выключателем: «как в системе» — не среднее между
 * светлым и тёмным, а третий, отдельный ответ, и чаще всего верный. Телефон
 * темнеет вечером сам, и приложение, которое этого не слышит, светит в лицо
 * ровно тогда, когда весь остальной экран уже погас.
 */
enum class ThemeMode(val title: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

/**
 * Цветовая гамма — та краска, которой в приложении отмечено «важное».
 *
 * Меняется именно акцент, а не бумага: кремовый лист и ночь — это лицо Askya,
 * и пять разных фонов сделали бы из неё пять разных приложений. Акцент же —
 * то, чем человек метит своё: им горит выбранное, им подчёркнута ссылка, им
 * подписан сегодняшний день.
 *
 * У каждой гаммы шесть красок, потому что тем две. Днём: [accent] — сама
 * краска, [soft] — её плашка, [deep] — буква на плашке. Ночью всё
 * переворачивается: [nightAccent] светлее фона, [nightSoft] — тёмная плашка,
 * [nightDeep] — светлая буква на ней. Взять дневные краски в ночь нельзя:
 * коралловая буква на почти чёрном читается как ржавчина.
 *
 * AskyaEcho и AskyaV гамме не подчиняются: закатный оранжевый там не акцент, а
 * имя раздела — так же, как у книг корешки.
 */
enum class AskyaPalette(
    val title: String,
    val accent: Color,
    val soft: Color,
    val deep: Color,
    val nightAccent: Color,
    val nightSoft: Color,
    val nightDeep: Color,
) {
    CORAL(
        "Коралл",
        CoralAccent, CoralSoft, CoralInk,
        Color(0xFFF08A3C), Color(0xFF3A2418), Color(0xFFF6D3B8),
    ),
    FOREST(
        "Хвоя",
        Color(0xFF3E7A4F), Color(0xFFE4EFE6), Color(0xFF2B5638),
        Color(0xFF74B98A), Color(0xFF1C2E22), Color(0xFFCFE7D6),
    ),
    SEA(
        "Море",
        Color(0xFF3E6B8A), Color(0xFFE2EBF2), Color(0xFF2A4C66),
        Color(0xFF7BB2D6), Color(0xFF17262F), Color(0xFFCFE3F0),
    ),
    PLUM(
        "Слива",
        Color(0xFF6B4F86), Color(0xFFECE5F3), Color(0xFF4B3560),
        Color(0xFFB291D0), Color(0xFF241C2E), Color(0xFFE2D5EE),
    ),
    AMBER(
        "Янтарь",
        Color(0xFF9A7318), Color(0xFFF6EEDA), Color(0xFF6D5210),
        Color(0xFFE0B347), Color(0xFF2E2513), Color(0xFFF3E3BC),
    ),

    // Три поздние гаммы. Взяты не «ещё каких-нибудь цветов», а тех мест
    // круга, которых в наборе не было: между кораллом и сливой зияла розовая
    // четверть, между хвоёй и морем — зелёно-синяя, а холодного серого не было
    // вовсе. Графит здесь не «выключенный цвет»: акцентом он читается как
    // грифель на бумаге, и тому, кому любая краска на листе мешает, он
    // единственный подходит.
    ROSE(
        "Роза",
        Color(0xFFB0526B), Color(0xFFF5E4E9), Color(0xFF7C3549),
        Color(0xFFD98BA0), Color(0xFF2E1A20), Color(0xFFF2D3DC),
    ),
    TEAL(
        "Бирюза",
        Color(0xFF2F7D74), Color(0xFFDFEDEA), Color(0xFF1F5A53),
        Color(0xFF6FBFB2), Color(0xFF142825), Color(0xFFC9E7E1),
    ),
    GRAPHITE(
        "Графит",
        Color(0xFF4A5560), Color(0xFFE6E9EC), Color(0xFF333C45),
        Color(0xFFA8B6C2), Color(0xFF1E242A), Color(0xFFD8E1E8),
    ),

    // Ещё три места круга — и все три взяты там, где прежняя гамма была
    // приглушена. Огонь горячее коралла, небо ярче моря, вишня темнее розы:
    // это не оттенки соседей, а другой накал того же угла. Askya до сих пор
    // говорила вполголоса, и тому, кому кремовый лист кажется слишком тихим,
    // выбрать было нечего.
    FIRE(
        "Огонь",
        Color(0xFFDC4E2A), Color(0xFFFBE3DB), Color(0xFF93301A),
        Color(0xFFFF8352), Color(0xFF38190F), Color(0xFFFFD4C2),
    ),
    SKY(
        "Небо",
        Color(0xFF2A80C4), Color(0xFFE2EEFA), Color(0xFF1B5688),
        Color(0xFF74BCF0), Color(0xFF12232F), Color(0xFFCFE6F9),
    ),
    CHERRY(
        "Вишня",
        Color(0xFFA32540), Color(0xFFF8E1E6), Color(0xFF72182C),
        Color(0xFFE4718C), Color(0xFF2F141A), Color(0xFFF7D3DB),
    ),

    /**
     * Хамелеон — не краска, а правило: он берёт её у той поры, в которую его
     * застали. Утром янтарь, днём небо, вечером слива — и делит он сутки ровно
     * там же, где расписание делит день на «Утро», «День» и «Вечер»
     * ([app.askya.ui.components.DayPart]).
     *
     * Затем и заведён: Askya — про день, и единственное, что в ней меняется
     * само, — час. Гамма, которая идёт за ним, говорит время суток раньше, чем
     * человек посмотрит на часы: лиловое приложение в руках — уже вечер.
     *
     * Развернуть его в настоящую гамму умеет [at]. Собственные краски у него
     * дневные, небесные: их берёт всякий, кто спросит цвет мимо [at], — и
     * пусть это будет полдень, а не чёрный прямоугольник.
     */
    CHAMELEON(
        "Хамелеон",
        Color(0xFF2A80C4), Color(0xFFE2EEFA), Color(0xFF1B5688),
        Color(0xFF74BCF0), Color(0xFF12232F), Color(0xFFCFE6F9),
    ),
}

/**
 * Краска цветка Askya — знака приложения.
 *
 * Отдельно от гаммы, и это не недосмотр. Гамма — голос **внутри** листа: ею
 * подчёркнуто выбранное слово и сегодняшний день, и меняют её под настроение
 * бумаги. Цветок же — лицо: он стоит на заставке, в шапке каждого раздела, на
 * месте недостающей обложки и на иконке запуска. Человеку, который любит
 * оранжевый цветок, но пишет заметки зелёным, пришлось бы выбирать одно из
 * двух — поэтому выборов два.
 *
 * [SUNSET] — та самая краска, которой цветок нарисован в `ic_flower.xml`, и
 * потому умолчание: до появления выбора он был только таким, и обновление не
 * должно перекрашивать знак приложения без спроса.
 *
 * Сам рисунок при этом не трогается — он тонируется на месте
 * ([app.askya.ui.components.AskyaFlower]). Восьми красок ради одного вектора
 * в `res/` не заводится.
 */
enum class FlowerColor(val title: String, val color: Color) {
    SUNSET("Закат", Color(0xFFEE8B3D)),
    CORAL("Коралл", CoralAccent),
    ROSE("Роза", Color(0xFFC2607A)),
    FOREST("Хвоя", Color(0xFF4E8F60)),
    SEA("Море", Color(0xFF4A7FA5)),
    PLUM("Слива", Color(0xFF7E5C9E)),
    AMBER("Янтарь", Color(0xFFD9A63C)),
    GRAPHITE("Графит", Color(0xFF5A6470)),
    FIRE("Огонь", Color(0xFFE4542B)),
    SKY("Небо", Color(0xFF3E93D1)),
    CHERRY("Вишня", Color(0xFFB32E4C)),

    /**
     * Тот же хамелеон, что и в гамме ([AskyaPalette.CHAMELEON]), и по тем же
     * часам: янтарный утром, небесный днём, лиловый вечером. Цветок для него —
     * место даже более подходящее, чем гамма: он и есть лицо приложения, и
     * лицо, меняющееся к вечеру, читается как живое, а не как сбой настройки.
     *
     * Своя краска у него дневная — на случай, если цвет спросят мимо [at].
     */
    CHAMELEON("Хамелеон", Color(0xFF3E93D1)),
}

/**
 * Дневная схема.
 *
 * Роли secondary заданы явно — их берут выделения в компонентах Material 3,
 * и без них подставляется собственный сиреневый, чужой этой палитре.
 */
private fun lightSchemeOf(palette: AskyaPalette) = lightColorScheme(
    primary = palette.accent,
    onPrimary = Color.White,
    primaryContainer = palette.soft,
    onPrimaryContainer = palette.deep,
    secondary = palette.accent,
    onSecondary = Color.White,
    secondaryContainer = palette.soft,
    onSecondaryContainer = palette.deep,
    background = PaperCream,
    onBackground = PaperInk,
    surface = PaperCard,
    onSurface = PaperInk,
    surfaceVariant = PaperPanel,
    onSurfaceVariant = PaperMuted,
    surfaceContainer = PaperPanel,
    surfaceContainerHigh = PaperPanel,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = PaperDanger,
    onError = Color.White,
)

/**
 * Ночная схема приложения — на той же темноте, что у Echo.
 *
 * Своей второй тьмы не заводится: у Askya уже есть ночь, в ней живёт плеер, и
 * два разных чёрных в одном приложении читались бы как недоделка. Отличие в
 * ступенях: лист — [Night], карточка на нём — [NightCard], подложка внутри
 * карточки — [NightCardSoft], черта — [NightEdge].
 *
 * Ступени взяты шире, чем у Echo, потому что там карточку держит обводка, а
 * здесь её нет: у Echo карточка обведена [NightBorder] и видна на любом фоне,
 * а карточка настроек, погоды и тренировок отличается от листа только цветом.
 * Отличие в один тон в тёмной части шкалы глаз не берёт — вечером, на
 * приглушённой яркости, карточки просто исчезали.
 */
private fun darkSchemeOf(palette: AskyaPalette) = darkColorScheme(
    primary = palette.nightAccent,
    onPrimary = Night,
    primaryContainer = palette.nightSoft,
    onPrimaryContainer = palette.nightDeep,
    secondary = palette.nightAccent,
    onSecondary = Night,
    secondaryContainer = palette.nightSoft,
    onSecondaryContainer = palette.nightDeep,
    background = Night,
    onBackground = NightInk,
    surface = NightCard,
    onSurface = NightInk,
    surfaceVariant = NightCardSoft,
    onSurfaceVariant = NightMuted,
    surfaceContainer = NightCard,
    surfaceContainerHigh = NightCardSoft,
    outline = NightEdge,
    outlineVariant = NightEdge,
    error = NightDanger,
    onError = Night,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Тема приложения.
 *
 * Динамические цвета Android 12+ намеренно не подключены и после появления
 * выбора гаммы тем более: иначе обои устройства перекрасили бы палитру, ради
 * которой всё и затевалось, — и настройка гаммы перестала бы что-либо решать.
 */
@Composable
fun AskyaTheme(
    dark: Boolean = false,
    palette: AskyaPalette = AskyaPalette.CORAL,
    flower: FlowerColor = FlowerColor.SUNSET,
    content: @Composable () -> Unit,
) {
    // Пора нужна одному хамелеону, а спрашивается у всех: неподвижной гамме
    // [at] вернёт её же, и лишней перерисовки на границе поры не случится —
    // цвета выйдут те же самые. Место для вопроса одно, и оно здесь: гамма и
    // цветок должны сменяться в один миг, а не каждый по своим часам.
    val part = rememberDayPart()
    val shown = palette.at(part)
    val ink = flower.at(part)

    CompositionLocalProvider(
        LocalNight provides dark,
        LocalFlowerColor provides ink.color,
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkSchemeOf(shown) else lightSchemeOf(shown),
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * Краска цветка Askya — сквозь всё приложение.
 *
 * Раздаётся темой, а не спрашивается у хранилища на месте: цветок стоит в
 * шапке каждого экрана, на заставке и на месте недостающей обложки, и два
 * десятка подписок на DataStore ради одного цвета — это два десятка мест,
 * которые разъедутся на первой же правке.
 *
 * Значение по умолчанию — закатный оранжевый, тот самый, которым цветок
 * нарисован: разметка, показанная без темы (предпросмотр, тест), должна
 * выглядеть как приложение, а не как чёрный силуэт.
 */
private val LocalFlowerColor = staticCompositionLocalOf { FlowerColor.SUNSET.color }

/** Краска цветка — та, что выбрана в настройках. */
val FlowerInk: Color
    @Composable @ReadOnlyComposable get() = LocalFlowerColor.current

/**
 * Ночь ли сейчас.
 *
 * Заведено не ради красок — их подставляет схема, — а ради тех мест, где
 * светлое и тёмное различаются **приёмом**, а не оттенком: черта вокруг
 * карточки, ночью необходимая, а днём лишняя.
 */
private val LocalNight = staticCompositionLocalOf { false }

val NightNow: Boolean
    @Composable @ReadOnlyComposable get() = LocalNight.current

/**
 * Черта, которой ночью отделена карточка.
 *
 * Днём её нет и не нужно: белая карточка на кремовом листе отделена сама —
 * тем, что она белее. Ночью так не выходит ни при какой разнице тонов, потому
 * что в тёмной части шкалы глаз почти не различает соседние, — и границу
 * приходится проводить чертой. Тем же приёмом собран AskyaEcho: там карточка
 * обведена [NightBorder] с самого начала.
 *
 * Тень сюда не годится: тень чёрная, а лист под ней и так почти чёрный.
 */
@Composable
fun Modifier.cardEdge(shape: Shape): Modifier =
    if (NightNow) border(1.dp, NightEdge, shape) else this

/**
 * Тень, которой днём приподнята карточка.
 *
 * Нужна там, где карточка лежит на такой же белой подложке и разницей тона не
 * отделяется вовсе: миниатюры в ленте Scroll. Ночью тени нет — она чёрная, а
 * лист под ней и так почти чёрный; ночью границу проводит [cardEdge].
 */
@Composable
fun Modifier.cardShade(shape: Shape, elevation: Dp = 4.dp): Modifier =
    if (NightNow) this else shadow(elevation, shape)

// ---------------------------------------------------------------------------
// Роли — те же слова, но читающие тему
// ---------------------------------------------------------------------------
//
// До появления тёмной темы это были просто краски, и по всему приложению они
// стоят под своими именами: `color = Ink`, `background = AccentSoft`. Имена
// оказались верными — они называют не оттенок, а место («буква», «плашка
// акцента»), — и менять их в двух сотнях мест значило бы переписать разметку
// ради переименования.
//
// Поэтому слова остались, а за ними встала выбранная схема. Читаются они
// только внутри разметки: `@ReadOnlyComposable` — обещание, что чтение темы
// ничего не пересобирает.
//
// Двух мест это не касается: подсветка найденного и вид ссылки собираются
// вне разметки (`SpanStyle` заводится один раз на файл), и там стоят сами
// краски. Обе — коралловые в любой гамме; на плашке подсветки это заметно
// меньше всего.

/** Бумага — то, на чём всё лежит. */
val Cream: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background

/** Карточка, лежащая на бумаге. */
val CardWhite: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surface

/** Буква. */
val Ink: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onBackground

/** Буква потише: подписи, пояснения, то, что читают вторым. */
val Muted: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Акцент выбранной гаммы. */
val Accent: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primary

/** Плашка акцента — под отмеченным словом. */
val AccentSoft: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

/** Буква на плашке акцента. */
val AccentInk: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onPrimaryContainer

/** Необратимое. */
val Danger: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.error

// Ночь AskyaEcho. Плеер — раздел, который живёт в темноте всегда:
// музыку слушают вечером и в дороге, и кремовый лист в этот момент светит в
// лицо. Закатный оранжевый — тот же коралловый акцент, доведённый до тепла
// заходящего солнца: раздел темнеет, но остаётся Askya.
//
// Эти же краски держат ночную схему всего приложения (выше): темнота у Askya
// одна.
val Night = Color(0xFF0B0A09)
val NightPanel = Color(0xFF16130F)
val NightBorder = Color(0xFF2A2520)
// Подложка внутри подложки: строка списка и место под обложку на карточке,
// которая сама уже стоит на NightPanel. Одним тоном светлее — ровно настолько,
// чтобы граница читалась без второй обводки.
val NightPanelSoft = Color(0xFF1F1B16)
// Ступени ночной схемы приложения. Те же, что у плеера, но разведённые
// заметнее: карточка здесь стоит без обводки, и разницу с листом держит она
// одна. Светлее сделать нельзя — ночь перестанет быть ночью; темнее уже было,
// и карточек не было видно.
val NightCard = Color(0xFF221E18)
val NightCardSoft = Color(0xFF2E2922)
val NightEdge = Color(0xFF3A332B)
val NightInk = Color(0xFFF3EFE8)
val NightMuted = Color(0xFF9A9187)
val Sunset = Color(0xFFF08A3C)
// Красный ночного раздела — светлее дневного Danger: тот на чёрном читается
// как ржавое пятно, а предупреждать должен цвет, который видно.
val NightDanger = Color(0xFFE57373)
val SunsetDeep = Color(0xFFD9542B)

/**
 * Тёмная схема — для AskyaEcho и AskyaV.
 *
 * Отдельная от ночной схемы приложения, хотя краски те же: там тьма — выбор
 * человека, здесь — свойство раздела. Оба плеера остаются тёмными и на светлой
 * теме, и акцент у них закатный при любой выбранной гамме: у раздела с именем
 * есть и свой цвет.
 */
private val EchoScheme = darkColorScheme(
    primary = Sunset,
    onPrimary = Night,
    primaryContainer = SunsetDeep,
    onPrimaryContainer = NightInk,
    secondary = Sunset,
    onSecondary = Night,
    secondaryContainer = NightPanel,
    onSecondaryContainer = NightInk,
    background = Night,
    onBackground = NightInk,
    surface = NightPanel,
    onSurface = NightInk,
    surfaceVariant = NightPanel,
    onSurfaceVariant = NightMuted,
    surfaceContainer = NightPanel,
    surfaceContainerHigh = NightPanel,
    outline = NightBorder,
    outlineVariant = NightBorder,
    error = NightDanger,
    onError = Night,
)

/** Тёмный режим раздела Echo: оборачивает только его экран. */
@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    // Краска цветка сюда приходит сверху сама и намеренно не подменяется:
    // Echo меняет тему, а не лицо приложения. Оранжевый акцент раздела и
    // цветок выбранного человеком цвета — это две разные вещи, и совпадали
    // они только потому, что выбора не было.
    CompositionLocalProvider(LocalNight provides true) {
        MaterialTheme(colorScheme = EchoScheme, shapes = AppShapes, content = content)
    }
}
