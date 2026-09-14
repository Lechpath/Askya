package app.askya.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.preferences.AppSettings
import app.askya.data.preferences.SplashWhen
import app.askya.ui.navigation.Destination
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.AskyaPalette
import app.askya.ui.theme.FlowerColor
import app.askya.ui.theme.ThemeMode

/**
 * Настройки.
 *
 * Устроены не по алфавиту и не по важности, а **по местам, к которым
 * относятся**: сперва то, что про приложение целиком, потом по кучке на
 * раздел, в том же порядке, в каком разделы стоят в меню. Так настройку ищут
 * на самом деле: человек вспоминает не «как это называется», а «где я это
 * видел».
 *
 * ## Чего здесь нет
 *
 * **Того, что настраивают на месте.** Эквалайзер Echo, кегль внутри книги,
 * скорость видео посреди фильма — всё это правится там, где на результат
 * смотрят, и продублированное здесь означало бы два ответа на один вопрос.
 * Сюда вынесено только то, что человек решает один раз и надолго.
 *
 * **Аккаунта и облака.** Их нет в приложении, и строчка «не настроено» была бы
 * обещанием, которого никто не давал. Выгрузка есть ровно одна — «Слепок», — и
 * она не про облако: человек записывает файл сам и сам решает, где ему лежать.
 * Живёт она отдельным файлом (`SnapshotGroup`), потому что это не настройка, а
 * действие с последствиями.
 *
 * ## Общее и своё
 *
 * Экран общий у телефона и компьютера: общее, вид и AskyaDay. Остальное
 * приносит система — [dayExtras] и [groups]: у компьютера нет ни шторки, ни
 * плееров, ни погоды по месту, и строки о них были бы настройками пустоты.
 */
@Composable
fun SettingsScreen(
    onOpenMenu: () -> Unit,
    /** Разделы этой системы: с них можно начинать, о них говорят подсказки. */
    sections: List<Destination>,
    /** Есть ли у системы заставка при запуске. У компьютера её нет. */
    splash: Boolean = true,
    /** Строки AskyaDay, которые есть только у этой системы. */
    dayExtras: @Composable (AppSettings) -> Unit = {},
    /**
     * Группы после AskyaDay, которые у системы свои: у телефона Echo,
     * AskyaV, погода, мосты, Библиотека, Слепок и обновления.
     */
    groups: @Composable (AppSettings) -> Unit = {},
    /** Окна этих групп: они ложатся поверх всей страницы, а не внутри неё. */
    overlay: @Composable () -> Unit = {},
) {
    val container = appContainer()
    val general by container.settings.settings
        .collectAsStateWithLifecycle(initialValue = container.settings.state.value)
    val players = Destination.ECHO in sections || Destination.VIDEO in sections
    val startRoutes = START_ROUTES.filter { route -> sections.any { it.route == route } }

    Box(modifier = Modifier.fillMaxSize()) {

    ScreenScaffold(title = "Настройки", onNavigationClick = onOpenMenu) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fadingVerticalScroll()
                .padding(horizontal = 16.dp),
        ) {

            SettingsGroup("Общее") {
                if (splash) {
                    SettingChoice(
                        title = "Заставка при запуске",
                        hint = "Цветок, имя и приветствие.",
                        values = SplashWhen.entries,
                        chosen = general.splash,
                        label = { it.title },
                        onPick = { container.settings.setSplash(it) },
                    )
                }
                SettingChoice(
                    title = "Открывать при запуске",
                    hint = "С какого раздела начинается приложение.",
                    values = startRoutes,
                    chosen = general.startRoute,
                    label = { route -> START_NAMES[route] ?: route },
                    onPick = { container.settings.setStartRoute(it) },
                )
                SettingSwitch(
                    title = "Неделя с понедельника",
                    hint = "Влияет на календарь и на счёт недель.",
                    checked = general.weekStartsMonday,
                    onChange = { container.settings.setWeek(it) },
                )
            }

            SettingsGroup("Вид") {
                SettingChoice(
                    title = "Тема",
                    hint = if (players) {
                        "«Как в системе» — темнеет вместе с телефоном. AskyaEcho и " +
                            "AskyaV остаются тёмными всегда: у плееров это не тема, а лицо."
                    } else {
                        "«Как в системе» — темнеет вместе с системой."
                    },
                    values = ThemeMode.entries,
                    chosen = general.theme,
                    label = { it.title },
                    onPick = { container.settings.setTheme(it) },
                )
                SettingChoice(
                    title = "Цветовая гамма",
                    hint = "Краска, которой отмечено важное: слово, ссылка, сегодняшний день. " +
                        "«Хамелеон» — не краска, а час: утром янтарь, днём небо, вечером слива. " +
                        "Меняется там же, где расписание переходит к следующей части дня.",
                    values = AskyaPalette.entries,
                    chosen = general.palette,
                    label = { it.title },
                    onPick = { container.settings.setPalette(it) },
                )
                SettingChoice(
                    title = "Цветок Askya",
                    hint = (if (splash) "Знак приложения: в шапке, на заставке, вместо недостающей обложки. "
                    else "Знак приложения: в шапке и вместо недостающей обложки. ") +
                        "«Хамелеон» и здесь идёт за временем суток.",
                    values = FlowerColor.entries,
                    chosen = general.flower,
                    label = { it.title },
                    onPick = { container.settings.setFlower(it) },
                )
                if (splash) {
                    SettingChoice(
                        title = "Цветок на заставке",
                        values = SPLASH_FLOWERS,
                        chosen = general.splashFlower,
                        label = { it?.title ?: "Как везде" },
                        onPick = { container.settings.setSplashFlower(it) },
                    )
                }
            }

            SettingsGroup("AskyaDay") {
                SettingSwitch(
                    title = "Заполняет новый день автоматически",
                    hint = "Открывая незанятый день, Askya разворачивает в него список дел. " +
                        "Выключено — день остаётся пустым, пока не заполнить его вручную.",
                    checked = general.autoFillDay,
                    onChange = { container.settings.setAutoFillDay(it) },
                )
                dayExtras(general)
                SettingSwitch(
                    title = "Список дела на весь экран",
                    hint = "Открывая Askya, дело со списком показывается карточкой во весь " +
                        "экран — с ним и работают. Сворачивается одним касанием, и под ним " +
                        "то же расписание. Выключено — карточка открывается по нажатию, как " +
                        "всякая другая.",
                    checked = general.deedFullScreen,
                    onChange = { container.settings.setDeedFullScreen(it) },
                )
            }

            groups(general)

            Spacer(Modifier.height(40.dp))
        }
    }

    // Окно — последним и внутри общего Box, как везде в приложении: оно
    // рисуется поверх страницы, а написанное раньше ушло бы под неё.
    overlay()
    }
}

/** Маршруты, с которых имеет смысл начинать: те же, что в меню. */
private val START_ROUTES = listOf("today", "notes", "practices", "video", "ledger")

private val START_NAMES = mapOf(
    "today" to "AskyaDay",
    "notes" to "Scroll",
    "practices" to "AskyaEcho",
    "video" to "AskyaV",
    "ledger" to "Ledger",
)

/**
 * Краски цветка на заставке: «как везде» и те же восемь, что у знака
 * приложения. Пустое значение стоит первым — это умолчание, и выбирать его
 * человек будет чаще всего: два разных цвета у одного цветка нужны не всем.
 */
private val SPLASH_FLOWERS: List<FlowerColor?> = listOf(null) + FlowerColor.entries
