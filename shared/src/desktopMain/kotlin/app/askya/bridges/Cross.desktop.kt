package app.askya.bridges

import app.askya.data.entity.Bridge
import app.askya.data.entity.BridgeKind
import app.askya.platform.PlatformContext
import app.askya.platform.toast
import app.askya.ui.components.openLink

actual fun crossBridge(context: PlatformContext, bridge: Bridge): Boolean {
    if (bridge.kind == BridgeKind.LINK) {
        openLink(context, bridge.target)
        return true
    }
    context.toast("«" + bridge.name + "» — приложение телефона: с компьютера туда не уйти.")
    return false
}
