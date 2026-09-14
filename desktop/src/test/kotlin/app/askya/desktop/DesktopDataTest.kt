package app.askya.desktop

import app.askya.data.entity.RoutineItem
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * База Windows-версии на настоящем SQLite: то, что ломается молча и чего не
 * поймать щелчками по окну, — несколько записей одной транзакцией и Слепок,
 * прочитанный на другом «компьютере».
 */
class DesktopDataTest {

    private val homes = mutableListOf<File>()

    private fun home(): File = Files.createTempDirectory("askya-test").toFile().also { homes += it }

    @AfterTest
    fun cleanUp() {
        homes.forEach { it.deleteRecursively() }
    }

    /**
     * `withTransaction` у компьютера — своё, поверх соединения Room. Если DAO
     * внутри него не видят того же соединения, это не ошибка, а вечное
     * ожидание: пул на запись у SQLite один.
     */
    @Test
    fun severalWritesInOneTransaction() = runBlocking {
        val container = DesktopContainer(home())
        val day = LocalDate.of(2026, 9, 14)
        val items = listOf(
            RoutineItem(title = "Зарядка", startTime = LocalTime.of(7, 0)),
            RoutineItem(title = "Почта", startTime = LocalTime.of(9, 30)),
        )

        container.routineRepository.addToDay(day, items)

        val written = container.scheduleRepository.itemsOnce(day).map { it.title }.sorted()
        assertEquals(listOf("Зарядка", "Почта"), written)
        container.database.close()
    }

    /** Слепок, сделанный одной Askya, поднимается другой — с записями и картинками. */
    @Test
    fun snapshotMovesNotesAndImages() = runBlocking {
        val first = DesktopContainer(home())
        val noteId = first.noteRepository.quickNote("Список на завтра", "хлеб\nмолоко")

        // Картинка — копией в папку Askya, как её приносит окно выбора.
        val picture = File(first.home, "source.png")
        javaClass.getResourceAsStream("/pixel.png").use { input ->
            picture.outputStream().use { input!!.copyTo(it) }
        }
        val copy = first.imageStore.importFrom(picture.toURI().toString(), "пиксель.png", "image/png")
        requireNotNull(copy)
        val imageId = first.noteRepository.addFile(copy, "пиксель.png", "image/png", isImage = true, topicId = null)

        val zip = File(first.home, "askya.zip")
        first.snapshots.write(zip).getOrThrow()
        first.database.close()

        val second = DesktopContainer(home())
        val info = second.snapshots.describe(zip)
        assertTrue(info?.readable == true, "слепок своей версии должен читаться")
        assertEquals(1, info?.images)

        second.snapshots.stageRestore(zip).getOrThrow()
        second.snapshots.applyPending()
        second.snapshots.finishImages()

        assertEquals("Список на завтра", second.noteRepository.get(noteId)?.title)
        val moved = second.noteRepository.get(imageId)?.uri
        assertTrue(second.imageStore.isOurs(moved), "картинка должна переехать в папку второй Askya")
        second.database.close()
    }
}
