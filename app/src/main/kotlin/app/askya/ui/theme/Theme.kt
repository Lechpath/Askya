package app.askya.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Тёплый кремовый фон, белые карточки, коралловый акцент.
val Cream = Color(0xFFFAF9F5)
val Panel = Color(0xFFF0EEE6)
val CardWhite = Color(0xFFFFFFFF)
val Border = Color(0xFFE3E1D8)
val Ink = Color(0xFF1F1E1B)
val Muted = Color(0xFF6F6D64)
val Accent = Color(0xFFD97757)
val AccentSoft = Color(0xFFF6E5DE)
val AccentInk = Color(0xFF8A4B32)
val Danger = Color(0xFFC0392B)

// Режим дня — собственная сигнальная шкала, а не роли Material: светофор нельзя
// свести к primary/error, не потеряв смысл цвета. Тона приглушены под кремовый фон.
val ModeRed = Color(0xFFB33C2E)
val ModeRedSoft = Color(0xFFF7E3DF)
val ModeYellow = Color(0xFF9A7318)
val ModeYellowSoft = Color(0xFFF6EEDA)
val ModeGreen = Color(0xFF3E7A4F)
val ModeGreenSoft = Color(0xFFE5EFE5)

// Корешки книг. Три своих тона к тем, что уже есть в палитре: у книги цвет не
// роль и не сигнал, а способ узнать её на полке в лицо, и восьми хватает,
// чтобы соседние книги не путались. Приглушены под кремовый фон, как режимы.
val SpineRose = Color(0xFFB0526B)
val SpineBlue = Color(0xFF3E6B8A)
val SpinePlum = Color(0xFF6B4F86)

/**
 * Тема только светлая, динамические цвета Android 12+ намеренно не подключены:
 * иначе обои устройства перекрасили бы палитру, ради которой всё и затевалось.
 *
 * Роли secondary заданы явно — их берут выделения в компонентах Material 3,
 * и без них подставляется собственный сиреневый, чужой этой палитре.
 */
private val Scheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = AccentInk,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = AccentSoft,
    onSecondaryContainer = AccentInk,
    background = Cream,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Panel,
    onSurfaceVariant = Muted,
    surfaceContainer = Panel,
    surfaceContainerHigh = Panel,
    outline = Border,
    outlineVariant = Border,
    error = Danger,
    onError = Color.White,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AskyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, shapes = AppShapes, content = content)
}

// Ночь AskyaEcho. Плеер — единственный раздел, который живёт в темноте:
// музыку слушают вечером и в дороге, и кремовый лист в этот момент светит в
// лицо. Закатный оранжевый — тот же коралловый акцент, доведённый до тепла
// заходящего солнца: раздел темнеет, но остаётся Askya.
val Night = Color(0xFF0B0A09)
val NightPanel = Color(0xFF16130F)
val NightBorder = Color(0xFF2A2520)
// Подложка внутри подложки: строка списка и место под обложку на карточке,
// которая сама уже стоит на NightPanel. Одним тоном светлее — ровно настолько,
// чтобы граница читалась без второй обводки.
val NightPanelSoft = Color(0xFF1F1B16)
val NightInk = Color(0xFFF3EFE8)
val NightMuted = Color(0xFF9A9187)
val Sunset = Color(0xFFF08A3C)
// Красный ночного раздела — светлее дневного Danger: тот на чёрном читается
// как ржавое пятно, а предупреждать должен цвет, который видно.
val NightDanger = Color(0xFFE57373)
val SunsetDeep = Color(0xFFD9542B)

/**
 * Тёмная схема — только для AskyaEcho.
 *
 * Отдельная схема, а не флаг в [AskyaTheme]: тьма здесь не настройка и не
 * системный режим, а свойство одного раздела. Остальное приложение остаётся
 * кремовым, чем бы ни был переключён телефон.
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
    error = Color(0xFFE57373),
    onError = Night,
)

/** Тёмный режим раздела Echo: оборачивает только его экран. */
@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = EchoScheme, shapes = AppShapes, content = content)
}
