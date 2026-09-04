package app.askya.bridges

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.widget.Toast
import app.askya.data.entity.Bridge
import app.askya.data.entity.BridgeKind
import app.askya.ui.components.openLink

/** Приложение телефона в окне выбора: чем его показать и как его открыть. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
)

/**
 * Что стоит на телефоне и как туда попасть.
 *
 * Ни одного имени чужого приложения здесь нет и не появится: Askya спрашивает у
 * системы список того, что показывает рабочий стол, и отдаёт его человеку.
 * Что он подключит к мосту — его дело; как только в коде появится «если это
 * такое-то приложение, то…», Askya начнёт зависеть от чужой прошивки и сломается
 * на её следующем обновлении.
 */
object BridgeApps {

    /**
     * Приложения с обычным входом — те же, что на рабочем столе.
     *
     * Без `<queries>` в манифесте на Android 11 и новее список пуст: система не
     * показывает, что стоит на телефоне, пока приложение не скажет, что ищет.
     *
     * Сама Askya из списка убрана: мост в неё же — это дверь в комнату, из
     * которой в неё и заходят.
     */
    fun installed(context: Context): List<InstalledApp> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = runCatching { manager.queryIntentActivities(intent, 0) }.getOrNull().orEmpty()

        return found
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = runCatching { info.loadLabel(manager).toString() }
                        .getOrDefault(info.packageName),
                    icon = runCatching { info.loadIcon(manager) }.getOrNull(),
                )
            }
            // По названию, а не по имени пакета: человек ищет глазами слово.
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Стоит ли ещё то, к чему подключён мост.
     *
     * Спрашивается не только в момент нажатия, но и при показе списка мостов:
     * мост со снесённым приложением должен быть видно, не дожидаясь, пока
     * человек на него нажмёт и упрётся.
     */
    fun alive(context: Context, bridge: Bridge): Boolean = when (bridge.kind) {
        BridgeKind.LINK -> bridge.target.isNotBlank()
        BridgeKind.APP -> launchIntent(context, bridge.target) != null
    }

    /** Как называется приложение, к которому подключён мост, — или пусто. */
    fun labelOf(context: Context, packageName: String): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(
            manager.getApplicationInfo(packageName, PackageManager.GET_META_DATA),
        ).toString()
    }.getOrDefault("")

    fun iconOf(context: Context, packageName: String): Drawable? = runCatching {
        context.packageManager.getApplicationIcon(packageName)
    }.getOrNull()

    /**
     * Перейти по мосту. Возвращает, получилось ли.
     *
     * Приложение снесли или переименовали — не падаем и не молчим: система
     * вернула `null` вместо входа, и человеку об этом говорится словами. Молча
     * ничего не сделавшая кнопка выглядит поломкой Askya, а не пропавшим
     * приложением.
     *
     * Ссылка уходит в уже написанный [openLink] — он умеет мягко промахиваться.
     */
    fun cross(context: Context, bridge: Bridge): Boolean {
        if (bridge.kind == BridgeKind.LINK) {
            openLink(context, bridge.target)
            return true
        }

        val intent = launchIntent(context, bridge.target)
        if (intent == null) {
            Toast.makeText(
                context,
                "«" + bridge.name + "»: приложения больше нет. Переподключите мост в настройках.",
                Toast.LENGTH_LONG,
            ).show()
            return false
        }
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun launchIntent(context: Context, packageName: String): Intent? = runCatching {
        context.packageManager.getLaunchIntentForPackage(packageName)
    }.getOrNull()
}
