package app.askya.data.backup

import app.askya.data.preferences.AgentPreferences
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Что «Слепок» телефона не возит: ключ Claude, согласие на облако и замок
 * остаются на этом устройстве.
 */
class SnapshotStoresTest {

    @Test
    fun `D — хранилище агента не входит в Слепок`() {
        assertFalse(AgentPreferences.STORE in Snapshots.STORES, Snapshots.STORES.toString())
    }

    @Test
    fun `аккаунт тоже не входит`() {
        assertFalse("account" in Snapshots.STORES, Snapshots.STORES.toString())
    }
}
