package app.askya.ui.scroll

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import app.askya.platform.LocalPlatformContext
import app.askya.platform.PlatformContext
import app.askya.platform.openStream
import app.askya.app.appContainer
import app.askya.domain.docs.ImportedNote
import app.askya.domain.docs.NoteImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Чем кончился перенос: сколько файлов взяли и сколько заметок из них вышло.
 *
 * Два числа, а не одно, потому что человек приносит файлы, а получает заметки,
 * и отношение между ними каждый раз своё: один `.enex` даёт две тысячи
 * записей, а двадцать `.txt` — двадцать. Сказать только «перенесено 2000»
 * значило бы не ответить на вопрос «а тот файл вообще прочитался?».
 */
data class ImportOutcome(val notes: Int, val files: Int)

/**
 * Перенос заметок из другого приложения — выбор файлов и запись их к себе.
 *
 * Разбор живёт в [NoteImport] и о системе ничего не знает; здесь всё
 * остальное: системный выбор, чтение по ссылке и запись в Библиотеку.
 *
 * ## Куда они ложатся
 *
 * В Библиотеку, к записям без книги, — туда же, куда ложится быстрая заметка.
 * Отдельная книга «Перенесённое» напрашивается, но она была бы вторым местом
 * для записанного, и человеку пришлось бы помнить, откуда взялась заметка,
 * чтобы её найти. Разложить их по книгам он может и сам — тем же движением,
 * каким раскладывает свои.
 *
 * ## Почему всё сразу, без выбора «что переносить»
 *
 * Список из двух тысяч чужих заметок с галочками — это работа на вечер, и
 * работа вслепую: имена там свои, вспомнить по ним, что внутри, нельзя. Проще
 * перенести всё и убрать лишнее потом, поиском по своей же полке, где заметки
 * уже открываются и читаются.
 *
 * ## Постоянного доступа не берём
 *
 * В отличие от файла, который заводят записью, выгрузка читается один раз,
 * прямо сейчас: дальше живут уже свои заметки, а до чужого файла Askya больше
 * никогда не дотянется. Права на процесс отпущено ограниченное число, и копить
 * их за то, что уже прочитано, незачем. То же правило, что у ввоза картинок.
 */
@Composable
fun rememberNoteImport(
    onStarted: () -> Unit,
    onDone: (ImportOutcome) -> Unit,
): () -> Unit {
    val context = LocalPlatformContext.current
    val notes = appContainer().noteRepository
    val scope = rememberCoroutineScope()

    return rememberFilePicker(NoteImport.pickTypes, keep = false) { picked ->
        onStarted()

        scope.launch {
            // Чтение и разбор — на своём потоке: архив Такаута разбирается
            // секундами, а замерший на это время экран читается как поломка.
            val found = withContext(Dispatchers.IO) {
                picked.flatMap { file -> context.notesIn(file) }
            }

            // Запись — по одной, а не пачкой: пачка на две тысячи записей одной
            // сделкой держала бы базу занятой, и полка под окном всё это время
            // стояла бы пустой. По одной она наполняется на глазах.
            var made = 0
            found.forEach { note ->
                runCatching { notes.quickNote(note.title, note.body) }
                    .onSuccess { made++ }
            }
            onDone(ImportOutcome(notes = made, files = picked.size))
        }
    }
}

/** Что нашлось в одном выбранном файле. Не прочитался — пусто, и это не беда. */
private fun PlatformContext.notesIn(file: PickedFile): List<ImportedNote> = runCatching {
    val bytes = openStream(file.uri)?.use { it.swallow() } ?: return emptyList()
    NoteImport.read(file.name, bytes)
}.getOrDefault(emptyList())

/**
 * Прочитать поток, но не больше [WHOLE].
 *
 * Предел не от жадности: выгрузка Такаута бывает на гигабайт, и `readBytes` на
 * такой заканчивается не ошибкой, которую видно, а тем, что приложение молча
 * закрывается.
 */
private fun java.io.InputStream.swallow(): ByteArray {
    val out = ByteArrayOutputStream()
    val chunk = ByteArray(64 * 1024)
    while (out.size() < WHOLE) {
        val read = read(chunk)
        if (read <= 0) break
        out.write(chunk, 0, read)
    }
    return out.toByteArray()
}

/** Столько от одного файла читаем и не больше. */
private const val WHOLE = 96 * 1024 * 1024
