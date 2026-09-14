package app.askya.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.askya.app.androidContainer
import app.askya.data.library.AskyaLibrary
import app.askya.ui.components.AskyaNotice
import kotlinx.coroutines.launch

/**
 * «Библиотека Askya» в настройках: своя папка в корне памяти.
 *
 * Отдельным файлом, а не строчками в [SettingsScreen], по той же причине, что
 * и «Слепок»: это не настройка, а место с последствиями — системный экран
 * разрешения, выбор файлов, копирование. В общем списке всё это заняло бы
 * больше, чем остальные настройки вместе.
 *
 * ## Почему разрешение спрашивается здесь, а не на первом запуске
 *
 * «Доступ ко всем файлам» — широкое право, и просить его молча, экраном
 * приветствия, — значит просить не объяснив. Здесь рядом с кнопкой написано,
 * зачем оно и что будет, если его не дать: ничего не сломается, папки просто
 * не будет, а копии останутся лежать в «Pictures/Askya» и «Movies/Askya».
 *
 * ## Что делает «Добавить файлы»
 *
 * Копирует выбранное в библиотеку — не ссылается на него. Это и есть весь
 * смысл папки: после копии исходник можно стереть, вычистить галерею, вынуть
 * карту памяти — то, что легло в Askya, останется.
 */
@Composable
fun LibraryGroup() {
    val container = androidContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = container.library

    // Счётчик пересчёта: разрешение выдают на чужом экране, файлы приходят из
    // чужого окна выбора — состояние папки меняется там, где на неё никто не
    // смотрит, и перечитывать его надо по возвращении.
    var tick by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Map<AskyaLibrary.Shelf, Int>>(emptyMap()) }
    var weight by remember { mutableStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(tick) {
        library.prepare()
        ready = library.ready()
        counts = library.counts()
        weight = library.weight()
    }

    val consent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { tick++ }

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { tick++ }

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { picked ->
        if (picked.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            var done = 0
            picked.forEach { uri ->
                val name = nameOf(context, uri)
                val mime = context.contentResolver.getType(uri).orEmpty()
                if (library.copyIn(uri, name, mime) != null) done++
            }
            busy = false
            tick++
            notice = if (done == picked.size) {
                "Скопировано в библиотеку" to
                    "Файлов: $done. Они лежат в «${library.folderName}» и останутся там, " +
                    "даже если стереть исходники."
            } else {
                "Скопировано не всё" to
                    "Легло $done из ${picked.size}. Остальные не прочитались или не " +
                    "хватило места."
            }
        }
    }

    SettingsGroup("Библиотека Askya") {
        SettingAction(
            title = "Папка приложения",
            hint = if (ready) {
                "Всё, что Askya кладёт к себе, лежит здесь: «Фото», «Музыка», «Видео», " +
                    "«Файлы». Копии, а не ссылки — уборка галереи их не тронет."
            } else {
                "Своей папки пока нет. Askya складывает копии в «Pictures/Askya» и " +
                    "«Movies/Askya» — они видны проводником, но разложены по чужим разделам."
            },
            value = if (ready) library.folderName else "нет",
        )

        if (!ready) {
            SettingAction(
                title = "Завести папку",
                hint = "Android не даёт создать папку в корне памяти без разрешения " +
                    "«Доступ ко всем файлам». Его выдают на системном экране; отказ ничего " +
                    "не ломает — всё останется как сейчас. Читать чужие файлы Askya от " +
                    "этого не начнёт: наружу из неё уходит только запрос погоды.",
                value = "Разрешить",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        consent.launch(allFilesScreen(context))
                    } else {
                        ask.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
            )
        } else {
            AskyaLibrary.Shelf.entries.forEach { shelf ->
                SettingAction(
                    title = shelf.title,
                    hint = shelf.about,
                    value = countWord(counts[shelf] ?: 0),
                )
            }

            SettingAction(
                title = "Занято",
                hint = "Столько места держит библиотека. Лишнее убирают из неё проводником — " +
                    "приложение перечитывает папку на каждом заходе.",
                value = weigh(weight),
            )

            SettingAction(
                title = "Добавить файлы",
                hint = "Копирует выбранное в библиотеку. Именно копирует: исходник после " +
                    "этого можно стереть, и в Askya ничего не пропадёт.",
                value = if (busy) "Копирую…" else "Выбрать",
                onClick = { if (!busy) pick.launch(arrayOf("*/*")) },
            )
        }
    }

    notice?.let { (title, text) ->
        AskyaNotice(
            title = title,
            text = text,
            icon = Icons.Outlined.FolderOpen,
            onDismiss = { notice = null },
        )
    }
}

/**
 * Системный экран, где выдают «Доступ ко всем файлам».
 *
 * Сперва — экран этого приложения; часть прошивок его не знает, и тогда
 * остаётся общий список, где приложение ищут в нём самом. Без запасного
 * варианта кнопка на таких телефонах не открывала бы ничего.
 */
private fun allFilesScreen(context: Context): Intent {
    val own = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    val can = context.packageManager.resolveActivity(own, 0) != null
    return if (can) own else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}

/** Имя выбранного документа — у того, кто его отдал. */
private fun nameOf(context: Context, uri: Uri): String {
    val given = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val at = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (at >= 0 && cursor.moveToFirst()) cursor.getString(at) else null
        }
    }.getOrNull()
    return given?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "файл" }
}

/** Вес папки словами. Своя, а не общая со «Слепком»: та живёт в своём файле. */
private fun weigh(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f ГБ".format(bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024 -> "%.1f МБ".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> (bytes / 1024).toString() + " КБ"
    else -> bytes.toString() + " Б"
}

/** «12 файлов» — числом и словом: одна цифра в столбце читается как ошибка. */
private fun countWord(count: Int): String = when {
    count == 0 -> "пусто"
    count % 10 == 1 && count % 100 != 11 -> "$count файл"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count файла"
    else -> "$count файлов"
}
