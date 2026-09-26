package app.askya.desktop

import app.askya.data.preferences.AgentPreferences
import kotlin.test.Test
import kotlin.test.assertFalse

/** Что «Слепок» Windows-версии не возит: ключ Claude, согласие на облако и замок. */
class DesktopStoresTest {

    @Test
    fun `D — хранилище агента не входит в Слепок`() {
        assertFalse(AgentPreferences.STORE in DesktopContainer.STORES, DesktopContainer.STORES.toString())
    }

    @Test
    fun `аккаунт тоже не входит`() {
        assertFalse("account" in DesktopContainer.STORES, DesktopContainer.STORES.toString())
    }
}
