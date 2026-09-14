package app.askya.bridges

import app.askya.data.entity.Bridge
import app.askya.platform.PlatformContext

/**
 * Уйти по мосту — туда, где дело делается за пределами Askya.
 *
 * Мост-ссылка открывается на любой системе. Мост-приложение ведёт в
 * приложение телефона, и у Windows-версии его нет: там об этом говорится
 * словами, а дело остаётся на месте. `true` — ушли, и по возвращении
 * Askya спросит «отметить?».
 */
expect fun crossBridge(context: PlatformContext, bridge: Bridge): Boolean
