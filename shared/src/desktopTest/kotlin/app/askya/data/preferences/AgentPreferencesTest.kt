package app.askya.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.askya.data.sync.SyncSchema
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Ключ Claude и согласие на облако: умолчания, правила и то, куда они не попадают. */
class AgentPreferencesTest {

    private val folder = Files.createTempDirectory("askya-agent").toFile()
    private val file = File(folder, "${AgentPreferences.STORE}.preferences_pb")
    private val key = "sk-ant-api03-не-настоящий-ключ-1234"

    private var storeJob = Job()
    private fun store() = PreferenceDataStoreFactory.createWithPath(
        scope = CoroutineScope(Dispatchers.IO + storeJob),
        produceFile = { file.absolutePath.toPath() },
    )

    private val store = store()

    private suspend fun prefs(): AgentPreferences = AgentPreferences(store).also { it.ready() }

    @AfterTest
    fun clean() {
        storeJob.cancel()
        folder.deleteRecursively()
    }

    @Test
    fun `A — по умолчанию ключа нет и облако закрыто`() = runBlocking {
        val agent = prefs()
        assertEquals(AgentSettings(keyTail = null, allowCloud = false), agent.settings.first())
        assertFalse(agent.cloudAllowed)
        assertNull(agent.apiKey())
    }

    @Test
    fun `до чтения файла согласия нет, даже если в файле оно есть`() = runBlocking {
        prefs().apply { setKey(key); setAllowCloud(true) }
        // Чтение файла не запущено: его корутине не дают хода.
        val unread = AgentPreferences(store, CoroutineScope(StandardTestDispatcher()))
        assertFalse(unread.cloudAllowed, "ещё не прочитано — значит «нельзя»")
    }

    @Test
    fun `без ключа облако не включить`() = runBlocking {
        val agent = prefs()
        assertFalse(agent.setAllowCloud(true))
        assertFalse(agent.cloudAllowed)
        assertFalse(agent.settings.first().allowCloud)
    }

    @Test
    fun `B — ключ и согласие — облако открыто сразу`() = runBlocking {
        val agent = prefs()
        assertTrue(agent.setKey("  $key  "))
        assertFalse(agent.cloudAllowed, "ключ сам облако не включает")

        assertTrue(agent.setAllowCloud(true))
        assertTrue(agent.cloudAllowed)
        val shown = agent.settings.first()
        assertEquals("1234", shown.keyTail)
        assertTrue(shown.allowCloud)
        assertNotNull(shown.keyId)
        assertEquals(key, agent.apiKey())

        assertFalse(agent.setAllowCloud(false))
        assertFalse(agent.cloudAllowed)
    }

    @Test
    fun `C — удалили ключ — облако выключилось`() = runBlocking {
        val agent = prefs()
        agent.setKey(key)
        agent.setAllowCloud(true)

        agent.removeKey()

        assertFalse(agent.cloudAllowed)
        assertEquals(AgentSettings(keyTail = null, allowCloud = false), agent.settings.first())
        assertNull(agent.apiKey())
    }

    @Test
    fun `замена ключа и пустой ключ`() = runBlocking {
        val agent = prefs()
        agent.setKey(key)
        agent.setAllowCloud(true)
        val before = agent.settings.first().keyId
        assertTrue(agent.setKey("sk-ant-api03-другой-ключ-9876"))
        assertNotEquals(before, agent.settings.first().keyId, "новый ключ — новый номер")
        assertEquals("9876", agent.settings.first().keyTail)
        assertTrue(agent.cloudAllowed, "замена ключа согласие не трогает")

        assertFalse(agent.setKey("   "))
        assertFalse(agent.setKey("два слова"))
        assertEquals("sk-ant-api03-другой-ключ-9876", agent.apiKey())
    }

    @Test
    fun `согласие без ключа в файле читается как запрет`() = runBlocking {
        store.edit { it[booleanPreferencesKey("allow_cloud")] = true }
        val agent = prefs()
        assertFalse(agent.cloudAllowed)
        assertFalse(agent.settings.first().allowCloud)
    }

    @Test
    fun `переживает перезапуск`() = runBlocking {
        prefs().apply { setKey(key); setAllowCloud(true) }
        storeJob.cancelAndJoin()
        storeJob = Job()

        val again = AgentPreferences(store()).also { it.ready() }
        assertTrue(again.cloudAllowed)
        assertEquals(key, again.apiKey())
    }

    @Test
    fun `ключ не выводится ни на экран, ни в строку`() = runBlocking {
        val agent = prefs()
        agent.setKey(key)
        val shown = agent.settings.first()
        assertFalse(key in shown.toString())
        assertFalse(key in agent.toString())
        // У короткого ключа не видно и хвоста.
        agent.setKey("sk-short")
        assertEquals("", agent.settings.first().keyTail)
    }

    @Test
    fun `E — синхронизация настроек агента не возит`() {
        // Синхронизация возит только таблицы базы; настроек среди них нет вовсе.
        assertTrue(SyncSchema.TABLES.none { it.contains(AgentPreferences.STORE) }, SyncSchema.TABLES.toString())
    }
}
