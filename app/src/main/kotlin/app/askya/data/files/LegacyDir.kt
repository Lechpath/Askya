package app.askya.data.files

import android.content.Context
import java.io.File

/**
 * Папка, куда писали до Библиотеки: своя папка приложения во внешней памяти.
 *
 * Осталась ради тех файлов, что уже там лежат, и ради Android до одиннадцатого,
 * где корневая папка Askya не заводится. Внешней памяти может не быть вовсе —
 * тогда своя папка внутри приложения: файл должен куда-то лечь.
 */
fun legacyAppDir(context: Context, name: String = "Askya"): File {
    val external = runCatching { context.getExternalFilesDir(name) }.getOrNull()
    val dir = external ?: File(context.filesDir, name)
    if (!dir.exists()) dir.mkdirs()
    return dir
}
