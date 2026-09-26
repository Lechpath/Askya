package app.askya.agent.tools

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.askya.agent.ToolKind
import app.askya.data.preferences.SettingsPreferences
import app.askya.data.repository.DeedTaskRepository
import app.askya.data.repository.LedgerRepository
import app.askya.data.repository.NoteRepository
import app.askya.data.repository.ReminderRepository
import app.askya.data.repository.ScheduleRepository
import app.askya.data.repository.YetRepository
import app.askya.data.sync.Json
import app.askya.testing.NoImages
import app.askya.testing.NoVoices
import app.askya.testing.TestDatabase
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Набор инструментов Askya: что в нём есть и чего быть не может. */
class AskyaToolsTest {

    private val base = TestDatabase()
    private val db = base.db
    private val prefsFolder = Files.createTempDirectory("askya-settings").toFile()
    private val settings = SettingsPreferences(
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { File(prefsFolder, "settings.preferences_pb").absolutePath.toPath() },
        ),
    )

    private val registry = askyaTools(
        ScheduleRepository(db.scheduleDao()),
        DeedTaskRepository(db.deedTaskDao()),
        ReminderRepository(db.reminderDao()),
        YetRepository(db.yetDao()),
        NoteRepository(db.noteDao(), db.topicDao(), db.albumDao(), NoImages, NoVoices),
        settings,
    )

    @AfterTest
    fun close() {
        base.close()
        prefsFolder.deleteRecursively()
    }

    @Test
    fun `в наборе ровно три инструмента чтения`() {
        assertEquals(listOf("get_today", "get_tasks", "search_notes"), registry.specs.map { it.name })
        assertTrue(registry.specs.all { it.kind == ToolKind.READ })
    }

    @Test
    fun `Ledger агенту недоступен`() {
        assertTrue(registry.specs.none { it.name.contains("ledger", ignoreCase = true) })
        // Его репозиторий набору даже не передать.
        val assemble = Class.forName("app.askya.agent.tools.AskyaToolsKt").declaredMethods.single { it.name == "askyaTools" }
        assertTrue(assemble.parameterTypes.none { it == LedgerRepository::class.java }, assemble.parameterTypes.toList().toString())
    }

    @Test
    fun `описания — простые значения, без сущностей Room`() {
        registry.specs.forEach { spec ->
            // Json.write пишет только строки, числа, флаги, списки и словари —
            // сущность в описании уронила бы запись.
            val written = Json.write(mapOf("name" to spec.name, "description" to spec.description, "schema" to spec.inputSchema))
            assertTrue("app.askya.data" !in written, spec.name)
        }
    }
}
