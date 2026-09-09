package app.askya.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.askya.app.appContainer
import app.askya.data.backup.SnapshotAlarms
import app.askya.data.backup.Snapshots
import app.askya.data.backup.snapshotWhen
import app.askya.data.preferences.AppSettings
import app.askya.reminders.ReminderAlarms
import app.askya.ui.components.AskyaAsk
import app.askya.ui.components.AskyaNotice
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * «Слепок» в настройках: записать, прочитать, напоминать.
 *
 * Отдельным файлом, а не строчками в [SettingsScreen], потому что это не три
 * настройки, а действие с последствиями: два системных окна выбора файла, два
 * предупреждения и перезапуск приложения. В общем списке настроек всё это
 * заняло бы столько же места, сколько остальные настройки вместе.
 *
 * Чего здесь нет — облака. Слепок кладут туда, куда человек сам показал, и
 * оттуда же читают. Askya не знает, где лежит файл, и знать не должна: как
 * только она начнёт куда-то его отправлять, у неё появится аккаунт.
 */
@Composable
fun SnapshotGroup(settings: AppSettings) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshots = container.snapshots

    var busy by remember { mutableStateOf(false) }

    // Вес будущего файла — до записи, а не после. Человек решает, класть ли
    // видео упражнений, глядя на число, а не наугад.
    var weight by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(settings.snapshotAt) {
        weight = snapshots.estimate()
    }
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Прочитанное из окна выбора ждёт подтверждения: чтение заменяет всё
    // целиком, и сказать об этом надо до того, как оно случилось, а не после.
    var pending by remember { mutableStateOf<Pair<Uri, Snapshots.Info?>?>(null) }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val target = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || target == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val done = snapshots.write(target)
            busy = false
            notice = done.fold(
                onSuccess = { size ->
                    container.settings.markSnapshot()
                    SnapshotAlarms.sync(context, settings.snapshotReminder, System.currentTimeMillis())
                    "Слепок записан" to
                        "Файл на " + weigh(size) + ". В нём вся база, настройки и картинки " +
                        "из папки Askya. Держите его не на этом же телефоне — иначе он " +
                        "разобьётся вместе с ним."
                },
                onFailure = { error ->
                    "Слепок не записался" to (error.message ?: "Файл не удалось записать до конца.")
                },
            )
        }
    }

    // Разрешение на уведомления спрашивается в тот момент, когда включают
    // напоминание, а не на первом запуске: до него оно ни о чём.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Отказали — будильник всё равно встаёт, просто промолчит. */ }

    val open = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val source = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || source == null) return@rememberLauncherForActivityResult
        scope.launch { pending = source to snapshots.describe(source) }
    }

    SettingsGroup("Слепок") {
        SettingAction(
            title = "Записать слепок",
            hint = "Вся память Askya одним файлом: база, настройки и картинки. Куда его " +
                "положить, решает система — папка на телефоне, флешка, чужое облако. " +
                "Askya не отправляет его никуда сама. Справа — сколько примерно " +
                "будет весить файл.",
            value = if (busy) "пишу…" else weight?.let { "~" + weigh(it) } ?: "Записать",
            onClick = {
                if (busy) return@SettingAction
                save.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_TITLE, snapshots.suggestedName()),
                )
            },
        )

        SettingAction(
            title = "Прочитать слепок",
            hint = "Заменяет всё целиком: и записи, и настройки. То, что накоплено после " +
                "слепка, при этом теряется — вернуть его будет неоткуда.",
            value = "Прочитать",
            onClick = {
                open.launch(
                    Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        // Не «application/zip»: проводники отдают слепку то
                        // «application/zip», то «octet-stream», то вовсе ничего,
                        // и по типу файл на половине телефонов оказался бы
                        // серым и невыбираемым.
                        .setType("*/*"),
                )
            },
        )

        SettingSwitch(
            title = "Напоминать раз в месяц",
            hint = "Через тридцать дней после последнего слепка — строчка в шторке. " +
                "Ничего не делает сама: слепок пишется только руками.",
            checked = settings.snapshotReminder,
            onChange = { on ->
                container.settings.setSnapshotReminder(on)
                if (on && ReminderAlarms.needsPermission()) {
                    askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                // Первый слепок мог ещё не случиться: тогда месяц начинает
                // идти отсюда, иначе напоминание пришло бы в ту же секунду.
                val since = settings.snapshotAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                if (settings.snapshotAt <= 0L) container.settings.markSnapshot(since)
                SnapshotAlarms.sync(context, on, since)
            },
        )

        SettingAction(
            title = "Последний слепок",
            hint = "Отсюда идёт отсчёт месяца.",
            value = lastSnapshot(settings.snapshotAt),
        )
    }

    val ask = pending
    if (ask != null) {
        val info = ask.second
        when {
            info == null -> AskyaNotice(
                title = "Это не слепок",
                text = "В файле нет описи Askya. Выберите тот zip, который записан " +
                    "пунктом «Записать слепок».",
                onDismiss = { pending = null },
            )

            !info.readable -> AskyaNotice(
                title = "Слепок новее приложения",
                text = "Он сделан версией Askya, которая знает о базе больше этой " +
                    "(схема " + info.schema + " против " + app.askya.data.db.AppDatabase.VERSION +
                    "). Обновите приложение и прочитайте его снова: разобрать вперёд " +
                    "нельзя — назад схема не переводится.",
                icon = Icons.Outlined.ErrorOutline,
                onDismiss = { pending = null },
            )

            else -> AskyaAsk(
                title = "Заменить всё этим слепком?",
                text = "Слепок от " + info.createdAt + ", картинок в нём " + info.images + ". " +
                    "Всё, что сейчас в приложении, будет заменено целиком — записи, дела, " +
                    "книги, списки, настройки. Написанное после этого слепка вернуть будет " +
                    "неоткуда. Askya закроется и откроется заново.",
                confirm = "Заменить",
                icon = Icons.Outlined.WarningAmber,
                onConfirm = {
                    val source = ask.first
                    pending = null
                    busy = true
                    scope.launch {
                        val done = snapshots.stageRestore(source)
                        busy = false
                        done.fold(
                            onSuccess = { restart(context) },
                            onFailure = { error ->
                                notice = "Слепок не прочитался" to
                                    (error.message ?: "Файл не удалось разобрать.")
                            },
                        )
                    }
                },
                onDismiss = { pending = null },
            )
        }
    }

    val said = notice
    if (said != null) {
        AskyaNotice(
            title = said.first,
            text = said.second,
            // Знак по итогу, а не один на оба: восклицательный круг над
            // словами «Слепок записан» читается как «что-то не так».
            icon = if (said.first.contains("не")) Icons.Outlined.ErrorOutline
            else Icons.Outlined.Inventory2,
            onDismiss = { notice = null },
        )
    }
}

/**
 * Перезапуск приложения после чтения слепка.
 *
 * Иначе никак: база подменена на диске, а открытое соединение смотрит в
 * прежний файл — всё, что покажет приложение до перезапуска, будет из старой
 * базы, а первая же запись испортит новую. Процесс убивается совсем, а не
 * пересоздаётся Activity: в живом процессе остаются и Room, и шесть DataStore
 * со своей памятью о том, что было записано минуту назад.
 */
private fun restart(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent?.let { context.startActivity(it) }
    Runtime.getRuntime().exit(0)
}

/** Вес файла словами: килобайты до мегабайта, дальше мегабайты. */
private fun weigh(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> (bytes / 1024) .toString() + " КБ"
    else -> bytes.toString() + " Б"
}

private fun lastSnapshot(at: Long): String {
    if (at <= 0L) return "не было"
    val moment = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault())
    return snapshotWhen(moment)
}
