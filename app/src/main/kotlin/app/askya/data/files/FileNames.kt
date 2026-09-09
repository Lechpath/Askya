package app.askya.data.files

import android.content.Context
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Имена файлов, которые Askya кладёт на диск сама.
 *
 * Правило одно на всех и родилось трижды подряд — у голосовых заметок, у
 * кусков видео и у Библиотеки: **отметка времени в начале, очищенное имя
 * следом**. По отметке файлы в папке идут по порядку и не затирают друг друга,
 * сколько бы их ни записали в один день, а очистка убирает из имени то, чего
 * файловая система не переносит.
 *
 * Три копии этого правила жили в трёх местах и успели разойтись в мелочах.
 * Здесь они сведены в одно, потому что расходиться им нельзя: Библиотека
 * узнаёт уже помеченное имя по той же отметке ([ALREADY_STAMPED]) и второй раз
 * его не метит, а узнать она может только то, что записано её же правилом.
 *
 * Картинки сюда не входят намеренно: у них отметка с миллисекундами
 * (`ImageStore`) — снимков за одну секунду бывает несколько, и секунды им мало.
 */
val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

/** Уже помеченное имя: `20260904-120000-...`. */
val ALREADY_STAMPED = Regex("""^\d{8}-\d{6}-""")

/**
 * Всё, чего в имени файла быть не должно. Буквы и цифры любого языка
 * остаются — имя пишут по-русски чаще, чем по-английски.
 */
val UNSAFE_IN_NAME = Regex("""[^\p{L}\p{N}._-]+""")

/**
 * Очищенное имя без хвоста: то, что встанет после отметки времени.
 *
 * [fallback] — на случай, когда чистить оказалось нечего: имя из одних знаков
 * препинания превращается в пустоту, а файл без имени не заведёшь.
 */
fun cleanBaseName(name: String, fallback: String): String =
    name.substringBeforeLast('.', name)
        .replace(UNSAFE_IN_NAME, "_")
        .trim('_')
        .take(48)
        .ifBlank { fallback }

/** Имя с отметкой времени в начале: `20260904-120000-имя`. */
fun stampedName(name: String, fallback: String): String =
    FILE_STAMP.format(LocalDateTime.now()) + "-" + cleanBaseName(name, fallback)

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
