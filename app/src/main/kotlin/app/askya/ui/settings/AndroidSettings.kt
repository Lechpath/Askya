package app.askya.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.androidContainer
import app.askya.data.preferences.AppSettings
import app.askya.data.preferences.WeatherSettings
import app.askya.reminders.ReminderAlarms
import app.askya.video.VideoScale

/*
 * Настройки, которые есть только у телефона. Сам экран общий
 * (`shared/…/SettingsScreen.kt`); сюда вынесено то, о чём у компьютера
 * говорить нечего: шторка и экран блокировки, плееры, погода по месту, мосты,
 * Библиотека, Слепок телефона, обновления и знакомство.
 */

/** Строки AskyaDay, которые есть только у телефона. */
@Composable
fun AndroidDaySettings(general: AppSettings) {
    val container = androidContainer()

    // Экран блокировки — это уведомление, и без разрешения на уведомления
    // (Android 13+) выключатель ничего бы не показал.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — выключатель остаётся, уведомление просто не появится. */ }

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
}

/** Группы после AskyaDay — в том же порядке, в каком разделы стоят в меню. */
@Composable
fun AndroidSettingsGroups(
    general: AppSettings,
    onOpenBridges: () -> Unit,
    onChoosePlace: () -> Unit,
) {
    val container = androidContainer()
    val echo by container.echoPreferences.settings
        .collectAsStateWithLifecycle(initialValue = container.echoPreferences.state.value)
    val video by container.videoPreferences.settings
        .collectAsStateWithLifecycle(initialValue = container.videoPreferences.state.value)
    val weather by container.weatherPreferences.settings
        .collectAsStateWithLifecycle(initialValue = WeatherSettings())
    val bridges by container.bridgeRepository.bridges()
        .collectAsStateWithLifecycle(initialValue = emptyList())

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
            onClick = onChoosePlace,
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
}

private val SEEK_STEPS = listOf(5, 10, 15, 30, 60)

private val RATES = listOf(1f, 1.25f, 1.5f, 2f)

private val REFRESH_MINUTES = listOf(15, 30, 60, 120)
