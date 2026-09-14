package app.askya.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.askya.platform.Notices
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.AskyaNotice
import app.askya.ui.navigation.PlatformShell
import app.askya.ui.settings.SettingAction
import app.askya.ui.settings.SettingsGroup
import app.askya.ui.scroll.chooseFiles
import app.askya.ui.scroll.chooseSaveFile
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File

/**
 * Windows-версия с точки зрения меню и навигации: AskyaDay, Scroll и Ledger.
 * Echo, AskyaV, «Голос», правки картинок, погоды и мостов у неё нет — строк о
 * них в меню и настройках тоже нет.
 */
@Composable
fun rememberDesktopShell(container: DesktopContainer, onRestored: () -> Unit): PlatformShell {
    val asking = remember { mutableStateOf<Ask?>(null) }
    return remember(container) {
        PlatformShell(
            settingsGroups = { _, _ -> DesktopSettingsGroups(container, asking) },
            settingsOverlay = { DesktopSettingsOverlay(container, asking, onRestored) },
        )
    }
}

/** Окно поверх настроек: прочитать ли слепок или что с ним не так. */
sealed interface Ask {
    data class Restore(val file: File, val info: DesktopSnapshots.Info) : Ask
    data class Problem(val text: String) : Ask
}

@Composable
private fun DesktopSettingsGroups(container: DesktopContainer, asking: MutableState<Ask?>) {
    val scope = rememberCoroutineScope()
    val snapshots = container.snapshots

    SettingsGroup("Слепок") {
        SettingAction(
            title = "Сделать слепок",
            hint = "Вся память Askya одним файлом: база, настройки и картинки. Тот же " +
                "файл, что делает телефон, — им данные и переезжают между телефоном и " +
                "компьютером. Куда его положить, решаете вы; сама Askya его никуда не отправляет.",
            onClick = {
                val target = chooseSaveFile("Сделать слепок", snapshots.suggestedName())
                if (target != null) {
                    scope.launch {
                        snapshots.write(target)
                            .onSuccess { size -> Notices.show("Слепок готов: ${megabytes(size)}") }
                            .onFailure { asking.value = Ask.Problem("Слепок не записался: ${it.message}") }
                    }
                }
            },
        )
        SettingAction(
            title = "Прочитать слепок",
            hint = "Всё, что сейчас в Askya на компьютере, заменится содержимым слепка. " +
                "Слепок телефона читается так же, как свой.",
            onClick = {
                val source = chooseFiles("Прочитать слепок", arrayOf("application/zip")).firstOrNull()
                if (source != null) {
                    scope.launch {
                        val info = snapshots.describe(source)
                        asking.value = when {
                            info == null -> Ask.Problem("Это не слепок Askya.")
                            !info.readable && info.schema > app.askya.data.db.AppDatabase.VERSION ->
                                Ask.Problem("Слепок сделан более новой Askya. Обновите Askya на компьютере.")
                            !info.readable ->
                                Ask.Problem(
                                    "Слепок сделан старой Askya на телефоне. Обновите её там и " +
                                        "сделайте слепок заново — компьютер поднимает только слепки своей версии.",
                                )
                            else -> Ask.Restore(source, info)
                        }
                    }
                }
            },
        )
    }

    SettingsGroup("Папка Askya") {
        // Путь — в подсказке, а не в значении справа: значение там короткое
        // («2.9»), и длинный путь отнимал у названия всю ширину — оно шло
        // столбиком, по букве в строку.
        SettingAction(
            title = "Открыть папку",
            hint = "Здесь лежат база, настройки и копии картинок. Только на этом " +
                "компьютере: в облако папка сама не уезжает.\n${container.home.absolutePath}",
            onClick = { runCatching { Desktop.getDesktop().open(container.home) } },
        )
    }

    SettingsGroup("О приложении") {
        SettingAction(
            title = "Askya для Windows",
            hint = "AskyaDay, Scroll и Ledger — те же, что на телефоне. Напоминания " +
                "звонят, пока Askya открыта или свёрнута к часам.",
            value = appVersion(),
        )
    }
}

@Composable
private fun DesktopSettingsOverlay(
    container: DesktopContainer,
    asking: MutableState<Ask?>,
    onRestored: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (val ask = asking.value) {
        null -> Unit
        is Ask.Problem -> AskyaNotice(
            title = "Слепок",
            text = ask.text,
            onDismiss = { asking.value = null },
        )
        is Ask.Restore -> AskyaAsk(
            title = "Прочитать слепок?",
            text = "Слепок от ${ask.info.createdAt}" +
                (if (ask.info.app.isNotBlank()) " (${ask.info.app})" else "") +
                ", картинок: ${ask.info.images}. Всё, что сейчас в Askya на компьютере, " +
                "заменится им. Askya закроется и откроется уже с ним.",
            confirm = "Прочитать",
            icon = Icons.Outlined.Inventory2,
            danger = true,
            onConfirm = {
                asking.value = null
                scope.launch {
                    container.snapshots.stageRestore(ask.file)
                        .onSuccess { onRestored() }
                        .onFailure { asking.value = Ask.Problem("Слепок не прочитался: ${it.message}") }
                }
            },
            onDismiss = { asking.value = null },
        )
    }
}

private fun megabytes(size: Long): String =
    if (size < 1024 * 1024) "${size / 1024} КБ" else "%.1f МБ".format(size / 1024.0 / 1024.0)
