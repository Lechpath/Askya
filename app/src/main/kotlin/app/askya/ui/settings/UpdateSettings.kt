package app.askya.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.askya.app.androidContainer
import app.askya.data.preferences.AppSettings
import app.askya.ui.components.ActionButton
import app.askya.ui.components.AskyaDialog
import app.askya.ui.components.DialogBadge
import app.askya.ui.components.DialogButtons
import app.askya.ui.components.DialogText
import app.askya.ui.components.DialogTitle
import app.askya.ui.components.fadingVerticalScroll
import app.askya.update.UpdateState

/**
 * «Обновление» в настройках: спросить облако, скачать, отдать установщику.
 *
 * ## Порядок ровно тот, о котором просили
 *
 * Нажали «Проверить» — приложение на секунду вышло в сеть и спросило GitHub,
 * нет ли сборки новее. Нет — сказало об этом и **ничего не тронуло**. Есть —
 * показало, что за сборка и что в ней нового; согласились — скачало; окно
 * установки дальше показывает система, и «да» в нём говорит человек.
 *
 * ## Почему об адресе здесь ни слова
 *
 * Откуда приезжает сборка — забота приложения, а не человека: он спрашивает
 * «есть ли новее», и ответ ему нужен один. Адрес остался настройкой
 * ([app.askya.data.preferences.DEFAULT_UPDATE_SOURCE]) на случай переезда, но
 * в глаза не лезет: строка с чужим репозиторием ничего не решала и только
 * просила разобраться.
 */
@Composable
fun UpdateGroup(settings: AppSettings) {
    val container = androidContainer()
    val updates = container.updates
    val state by updates.state.collectAsStateWithLifecycle()

    // Найденная версия помнится, пока открыт экран: окно закрывают «потом», а
    // строка должна и дальше говорить, что новее есть.
    var newer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state) {
        when (val now = state) {
            is UpdateState.Found -> newer = now.build.version
            is UpdateState.Latest -> newer = null
            else -> Unit
        }
    }

    // Согласие на установку из этого приложения — системный экран. Спрашивается
    // до скачивания, а не после: узнать о запрете, прождав сто мегабайт, обидно.
    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    SettingsGroup("Обновление") {
        SettingAction(
            title = "Проверить обновление",
            // Обычно под строкой пусто: номера версии справа хватает. Слово
            // появляется, только когда есть о чём сказать, — и краской.
            hint = newer?.let { "Есть новая версия $it" }.orEmpty(),
            hintAccent = newer != null,
            value = when (state) {
                is UpdateState.Asking -> "Спрашиваю…"
                is UpdateState.Getting -> "Качаю…"
                else -> updates.installed
            },
            onClick = { updates.check() },
        )

        SettingSwitch(
            title = "Проверять при запуске",
            hint = "Один короткий запрос не чаще раза в сутки и только он: найденное " +
                "обновление не качается само и тем более не ставится — о нём просто " +
                "сказано в этой строке. Выключено — проверка остаётся делом рук.",
            checked = settings.updateOnStart,
            onChange = { container.settings.setUpdateOnStart(it) },
        )
    }

    when (val now = state) {
        is UpdateState.Idle, is UpdateState.Asking -> Unit

        is UpdateState.Latest -> UpdateNotice(
            title = "Обновлений нет",
            text = "Стоит самая свежая сборка — ${now.version.ifBlank { "текущая" }}. " +
                "Ничего не тронуто.",
            onDismiss = { updates.forget() },
        )

        is UpdateState.Found -> AskyaDialog(
            onDismiss = { updates.forget() },
            badge = { DialogBadge(Icons.Outlined.CloudDownload) },
        ) {
            DialogTitle("Есть версия ${now.build.version}")
            DialogText(
                "Стоит ${updates.installed.ifBlank { "неизвестно что" }}. " +
                    if (now.build.size > 0) "Скачать ${weighUpdate(now.build.size)}?" else "Скачать?",
            )
            if (now.build.notes.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .fadingVerticalScroll(),
                ) {
                    Text(
                        text = now.build.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DialogButtons {
                ActionButton(
                    icon = Icons.Outlined.Close,
                    label = "Потом",
                    onClick = { updates.forget() },
                )
                ActionButton(
                    icon = Icons.Outlined.CloudDownload,
                    label = "Скачать",
                    accent = true,
                    onClick = {
                        if (updates.mayInstall()) {
                            updates.get(now.build)
                        } else {
                            // Без согласия на установку окно установщика не
                            // откроется вовсе — и сто мегабайт скачались бы зря.
                            consent.launch(updates.installConsent())
                        }
                    },
                )
            }
        }

        is UpdateState.Getting -> AskyaDialog(
            onDismiss = { updates.forget() },
            badge = { DialogBadge(Icons.Outlined.CloudDownload) },
        ) {
            DialogTitle("Качаю ${now.build.version}")
            DialogText(
                if (now.total > 0) {
                    "${weighUpdate(now.done)} из ${weighUpdate(now.total)}. " +
                        "Приложение можно не закрывать."
                } else {
                    "${weighUpdate(now.done)}. Сервер не сказал, сколько всего."
                },
            )
            DialogButtons {
                ActionButton(
                    icon = Icons.Outlined.Close,
                    label = "Отменить",
                    onClick = { updates.forget() },
                )
            }
        }

        is UpdateState.Ready -> AskyaDialog(
            onDismiss = { updates.forget() },
            badge = { DialogBadge(Icons.Outlined.Check) },
        ) {
            DialogTitle("Сборка ${now.build.version} скачана")
            DialogText(
                "Дальше окно установки показывает система — согласиться в нём надо " +
                    "самому. Записи, настройки и папка Askya остаются на месте: это " +
                    "обновление поверх, а не установка заново.",
            )
            DialogButtons {
                ActionButton(
                    icon = Icons.Outlined.Close,
                    label = "Потом",
                    onClick = { updates.forget() },
                )
                ActionButton(
                    icon = Icons.Outlined.Check,
                    label = "Установить",
                    accent = true,
                    onClick = { updates.install(now.file) },
                )
            }
        }

        is UpdateState.Failed -> UpdateNotice(
            title = "Не вышло",
            text = now.reason,
            onDismiss = { updates.forget() },
        )
    }
}

@Composable
private fun UpdateNotice(title: String, text: String, onDismiss: () -> Unit) {
    AskyaDialog(onDismiss = onDismiss, badge = { DialogBadge(Icons.Outlined.CloudDownload) }) {
        DialogTitle(title)
        DialogText(text)
        DialogButtons {
            ActionButton(
                icon = Icons.Outlined.Check,
                label = "Ясно",
                accent = true,
                onClick = onDismiss,
            )
        }
    }
}

private fun weighUpdate(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.0f МБ".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> (bytes / 1024).toString() + " КБ"
    else -> bytes.toString() + " Б"
}
