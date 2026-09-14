package app.askya.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import app.askya.data.entity.Note
import app.askya.data.preferences.AppSettings

/**
 * Чем приложение отличается на телефоне и на компьютере — с точки зрения
 * меню и навигации.
 *
 * Общая навигация ([AskyaApp]) знает все общие экраны: день, Scroll, Ledger,
 * настройки. Остальное приносит система: у телефона — Echo, AskyaV, «Голос»,
 * правка картинок, погода, мосты; у Windows-версии пока ничего из этого, и
 * тогда в меню этих строк нет, а кнопки, ведущие туда, не рисуются. Кнопка,
 * ведущая в никуда, хуже отсутствующей.
 */
class PlatformShell(
    /** Разделы меню этой системы — по порядку. */
    val sections: List<Destination> = listOf(Destination.TODAY, Destination.NOTES, Destination.LEDGER),

    /** Играет ли музыка — для кнопки внизу меню. */
    val playing: @Composable () -> Boolean = { false },

    /**
     * Кнопка музыки внизу меню. Отвечает, нашлось ли что продолжить: нет —
     * меню уводит туда, где музыку выбирают. `null` — музыки у системы нет.
     */
    val onPlay: (() -> Boolean)? = null,

    /** Строка погоды в шапке меню; `null` — строки нет. */
    val weatherLine: @Composable () -> WeatherLine? = { null },

    /** Меню открыли — погоде пора освежиться, если ей положено. */
    val refreshWeather: () -> Unit = {},

    /** Есть ли подраздел «Голос» — там, где есть микрофон. */
    val voice: Boolean = false,

    /** Кто играет голосовые заметки поверх экрана; `null` — некому. */
    val voicePlayer: VoicePlayer? = null,

    /** Есть ли правка картинок и коллаж. */
    val imageEditing: Boolean = false,

    /** Есть ли мосты — приложения телефона, которыми делаются дела. */
    val bridges: Boolean = false,

    /** Есть ли заставка при запуске — цветок и приветствие перед первым экраном. */
    val splash: Boolean = false,

    /** Строки AskyaDay в настройках, которые есть только у этой системы. */
    val settingsDay: @Composable (AppSettings) -> Unit = {},

    /** Группы настроек после AskyaDay, свои у каждой системы. */
    val settingsGroups: @Composable (AppSettings, ShellNav) -> Unit = { _, _ -> },

    /** Окна этих групп — поверх всей страницы настроек. */
    val settingsOverlay: @Composable () -> Unit = {},

    /** Маршруты, которые есть только у этой системы. */
    val routes: NavGraphBuilder.(ShellNav) -> Unit = {},

    /** То, что лежит поверх всех экранов, — у телефона играющая заметка. */
    val overlay: @Composable () -> Unit = {},
)

/**
 * Что общая навигация даёт маршрутам системы: куда уходить и как.
 *
 * [sayNow] — функция, а не значение: граф навигации собирается один раз, а
 * просьба «начни писать» приходит и уходит позже.
 */
class ShellNav(
    val controller: NavHostController,
    val openDrawer: () -> Unit,
    val openSection: (String) -> Unit,
    val sayNow: () -> Boolean,
    val onSaid: () -> Unit,
)

/**
 * Голосовая заметка, играющая поверх экрана, — то, что о ней знает лента
 * Scroll: что звучит и как включить другую. Есть только у телефона.
 */
class VoicePlayer(
    /** Номер звучащей заметки и играет ли она сейчас, а не стоит на паузе. */
    val sounding: @Composable () -> Pair<Long?, Boolean>,
    val play: (Note) -> Unit,
)
