package app.askya.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.appContainer
import app.askya.data.preferences.SplashWhen
import app.askya.data.preferences.WeatherSettings
import app.askya.reminders.ReminderAlarms
import app.askya.ui.components.ScreenScaffold
import app.askya.ui.components.fadingVerticalScroll
import app.askya.ui.theme.AskyaPalette
import app.askya.ui.theme.FlowerColor
import app.askya.ui.theme.ThemeMode
import app.askya.ui.weather.PlaceDialog
import app.askya.video.VideoScale

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
 * Живёт она отдельным файлом ([SnapshotGroup]), потому что это не настройка, а
 * действие с последствиями.
 */
@Composable
fun SettingsScreen(onOpenMenu: () -> Unit, onOpenBridges: () -> Unit = {}) {
    val container = appContainer()
    val general by container.settings.settings
        .collectAsStateWithLifecycle(initialValue = container.settings.state.value)
    val echo by container.echoPreferences.settings
        .collectAsStateWithLifecycle(initialValue = container.echoPreferences.state.value)
    val video by container.videoPreferences.settings
        .collectAsStateWithLifecycle(initialValue = container.videoPreferences.state.value)
    val weather by container.weatherPreferences.settings
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())
    val bridges by container.bridgeRepository.bridges()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Экран блокировки — это уведомление, и без разрешения на уведомления
    // (Android 13+) выключатель ничего бы не показал.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — выключатель остаётся, уведомление просто не появится. */ }

    // Окно городов — то же самое, что открывается значком места в разделе
    // погоды. Своего у настроек нет: два окна об одном разошлись бы.
    var choosingPlace by remember { mutableStateOf(false) }

    // Окно — последним и внутри общего Box, как везде в приложении: оно
    // рисуется поверх страницы, а написанное раньше ушло бы под неё.
    Box(modifier = Modifier.fillMaxSize()) {

    ScreenScaffold(title = "Настройки", onNavigationClick = onOpenMenu) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .fadingVerticalScroll()
                .padding(horizontal = 16.dp),
        ) {

            SettingsGroup("Общее") {
                SettingChoice(
                    title = "Заставка при запуске",
                    hint = "Цветок, имя и приветствие.",
                    values = SplashWhen.entries,
                    chosen = general.splash,
                    label = { it.title },
                    onPick = { container.settings.setSplash(it) },
                )
                SettingChoice(
                    title = "Открывать при запуске",
                    hint = "С какого раздела начинается приложение.",
                    values = START_ROUTES,
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
                    hint = "«Как в системе» — темнеет вместе с телефоном. AskyaEcho и " +
                        "AskyaV остаются тёмными всегда: у плееров это не тема, а лицо.",
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
                    hint = "Знак приложения: в шапке, на заставке, вместо недостающей обложки. " +
                        "«Хамелеон» и здесь идёт за временем суток.",
                    values = FlowerColor.entries,
                    chosen = general.flower,
                    label = { it.title },
                    onPick = { container.settings.setFlower(it) },
                )
                SettingChoice(
                    title = "Цветок на заставке",
                    values = SPLASH_FLOWERS,
                    chosen = general.splashFlower,
                    label = { it?.title ?: "Как везде" },
                    onPick = { container.settings.setSplashFlower(it) },
                )
            }

            SettingsGroup("AskyaDay") {
                SettingSwitch(
                    title = "Заполняет новый день автоматически",
                    hint = "Открывая незанятый день, Askya разворачивает в него список дел. " +
                        "Выключено — день остаётся пустым, пока не заполнить его вручную.",
                    checked = general.autoFillDay,
                    onChange = { container.settings.setAutoFillDay(it) },
                )
                SettingSwitch(
                    title = "Список дела в шторке",
                    hint = "Дело со списком ложится в шторку уведомлений, и отметить строку " +
                        "можно, не открывая Askya. Тихо и без всплытия: это не напоминание, " +
                        "а карточка под рукой. Смахнули — вернётся, когда список изменится " +
                        "или когда Askya откроют.",
                    checked = general.deedShade,
                    // Убирать уведомления отсюда не надо: за выключателем
                    // следит сама шторка (`AskyaApplication.watchDeedTasksForShade`),
                    // и сделанное здесь вторым вызовом гонялось бы с записью
                    // настройки — какое из двух значений прочтётся, решал бы
                    // случай.
                    onChange = { container.settings.setDeedShade(it) },
                )
                SettingSwitch(
                    title = "Дела дня на экране блокировки",
                    hint = "Тот же список, что в виджете дня: что идёт сейчас и что дальше. " +
                        "Сторонних виджетов на экран блокировки Android не пускает, поэтому " +
                        "это тихое уведомление. На заблокированном телефоне его не смахнуть; " +
                        "смахнутое на разблокированном вернётся со следующим делом.",
                    checked = general.dayLockScreen,
                    // Уведомление ставит и убирает не выключатель, а поток
                    // настроек в `AskyaApplication.watchScheduleForLockScreen` —
                    // по той же причине, что у шторки выше.
                    onChange = { on ->
                        container.settings.setDayLockScreen(on)
                        if (on && ReminderAlarms.needsPermission()) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
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

            SettingsGroup("AskyaEcho") {
                SettingSwitch(
                    title = "Спрашивать, что поставить",
                    hint = "На входе в раздел Echo раскладывает карточки: продолжить, " +
                        "плейлист, папка, исполнитель, альбом, жанр. Выключено — " +
                        "открывается сразу плеер. О том, что уже играет, не " +
                        "спрашивают в любом случае.",
                    checked = echo.askOnStart,
                    onChange = { container.echoPreferences.setAskOnStart(it) },
                )
                SettingSwitch(
                    title = "Продолжать с последней песни",
                    hint = "Открывая раздел, плеер встаёт на том, что играло в прошлый раз.",
                    checked = echo.resumeLast,
                    onChange = { container.echoPreferences.setResumeLast(it) },
                )
                SettingSwitch(
                    title = "Затухание",
                    hint = "Пауза и следующая песня приходят плавно, а не обрывом.",
                    checked = echo.fade,
                    onChange = { container.echoPreferences.setFade(it) },
                )
                SettingSwitch(
                    title = "Пауза на выдернутых наушниках",
                    hint = "Без неё музыка продолжится в динамик — на весь автобус.",
                    checked = echo.pauseOnUnplug,
                    onChange = { container.echoPreferences.setPauseOnUnplug(it) },
                )
                SettingSwitch(
                    title = "Вспышки обложки в ритм",
                    hint = "Красиво, но слушает звук всё время, пока раздел открыт.",
                    checked = echo.pulse,
                    onChange = { container.echoPreferences.setPulse(it) },
                )
            }

            SettingsGroup("AskyaV") {
                SettingSwitch(
                    title = "Продолжать с места остановки",
                    hint = "Место помнится само; начало и конец файла не помнятся вовсе.",
                    checked = video.resume,
                    onChange = { container.videoPreferences.setResume(it) },
                )
                SettingSwitch(
                    title = "Жесты яркости и громкости",
                    hint = "Слева вверх-вниз — яркость, справа — громкость, как в VLC.",
                    checked = video.gestures,
                    onChange = { container.videoPreferences.setGestures(it) },
                )
                SettingSwitch(
                    title = "Не гасить экран",
                    hint = "Пока идёт фильм, телефон не засыпает.",
                    checked = video.keepAwake,
                    onChange = { container.videoPreferences.setKeepAwake(it) },
                )
                SettingSwitch(
                    title = "Разворачивать экран",
                    hint = "Широкий кадр ложится поперёк экрана, снятый стоя — оставляет его " +
                        "стоя. Повернуть руками можно всегда — кнопкой в шапке плеера.",
                    checked = video.autoRotate,
                    onChange = { container.videoPreferences.setAutoRotate(it) },
                )
                SettingChoice(
                    title = "Шаг перемотки",
                    hint = "Столько отматывает двойное касание по краю экрана.",
                    values = SEEK_STEPS,
                    chosen = video.seekStepSeconds,
                    label = { "$it с" },
                    onPick = { container.videoPreferences.setSeekStep(it) },
                )
                SettingChoice(
                    title = "Скорость по умолчанию",
                    hint = "С неё открывается всякий новый файл.",
                    values = RATES,
                    chosen = video.rate,
                    label = { rate -> if (rate == 1f) "обычная" else "${rate}x".replace(".0x", "x") },
                    onPick = { container.videoPreferences.setRate(it) },
                )
                SettingChoice(
                    title = "Кадр в экране",
                    hint = "Как ложится картинка у нового файла.",
                    values = VideoScale.entries,
                    chosen = video.scale,
                    label = { it.label },
                    onPick = { container.videoPreferences.setScale(it) },
                )
            }

            SettingsGroup("Погода") {
                SettingSwitch(
                    title = "Спрашивать погоду",
                    hint = "Единственное, ради чего приложение выходит в сеть. Выключено — " +
                        "не выходит ни разу, и запомненное стирается.",
                    checked = weather.enabled,
                    onChange = { container.weatherPreferences.setEnabled(it) },
                )
                SettingSwitch(
                    title = "Показывать в меню",
                    hint = "Строка рядом с именем приложения.",
                    checked = weather.inMenu,
                    onChange = { container.weatherPreferences.setInMenu(it) },
                )
                SettingAction(
                    title = "Места",
                    hint = "По телефону — берётся последнее известное системе. Заведённые " +
                        "города отменяют это: геолокация не спрашивается вовсе. Их может " +
                        "быть несколько, и в разделе они переключаются строкой под шапкой.",
                    value = weather.place?.name ?: "по телефону",
                    onClick = { choosingPlace = true },
                )
                SettingChoice(
                    title = "Обновлять не чаще",
                    hint = "Между запросами показывается запомненное — с подписью, когда оно " +
                        "спрошено.",
                    values = REFRESH_MINUTES,
                    chosen = weather.refreshMinutes,
                    label = { "$it мин" },
                    onPick = { container.weatherPreferences.setRefreshMinutes(it) },
                )
            }

            SettingsGroup("Мосты") {
                SettingAction(
                    title = "Мосты",
                    hint = "Чем дела дня делаются за пределами Askya: читалка, мессенджер, " +
                        "заметки по работе. Мост — пустая рамка, что к ней подключено, " +
                        "решаете вы: имён чужих приложений в Askya нет.",
                    value = if (bridges.isEmpty()) "нет" else "${bridges.size}",
                    onClick = onOpenBridges,
                )
            }

            LibraryGroup()

            SnapshotGroup(general)

            UpdateGroup(general)

            SettingsGroup("О приложении") {
                SettingAction(
                    title = "Знакомство с приложением",
                    hint = "Семь карточек о том, что здесь есть и зачем. Показывается один " +
                        "раз, на первом запуске; отсюда его можно открыть заново.",
                    value = "Показать",
                    onClick = { container.settings.setTourSeen(false) },
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (choosingPlace) PlaceDialog(onDismiss = { choosingPlace = false })
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

private val SEEK_STEPS = listOf(5, 10, 15, 30, 60)

private val RATES = listOf(1f, 1.25f, 1.5f, 2f)

private val REFRESH_MINUTES = listOf(15, 30, 60, 120)

