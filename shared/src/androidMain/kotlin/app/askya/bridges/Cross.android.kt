package app.askya.bridges

import app.askya.data.entity.Bridge
import app.askya.platform.PlatformContext

actual fun crossBridge(context: PlatformContext, bridge: Bridge): Boolean = BridgeApps.cross(context, bridge)
