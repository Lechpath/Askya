package app.askya.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.widget.RemoteViews
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import kotlin.math.ceil

/**
 * Виджет расписания на рабочем столе.
 *
 * Показывает последовательность дел от текущего момента: что идёт сейчас и что
 * дальше. Прошедшее не показывается — отдавать ему место значит не показать то,
 * ради чего смотрят.
 *
 * Три дела занимают виджет целиком: высота строки считается здесь как треть его
 * высоты и уходит в фабрику. Остальные достаются прокруткой — в RemoteViews она
 * есть только у коллекции, поэтому внутри `ListView`, а не столбик строк.
 *
 * Полупрозрачный: сквозь него видно обои.
 */
class DayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, manager, id) }
        DayWidgetRefresh.scheduleNext(context)
    }

    /**
     * Будильник ставится и здесь: виджет живёт дольше приложения, и процесс
     * может больше никогда не запуститься — а картинка должна меняться.
     */
    override fun onEnabled(context: Context) {
        DayWidgetRefresh.scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        DayWidgetRefresh.cancel(context)
    }

    /**
     * Виджет растянули или сжали: высота строки — треть его высоты, значит её
     * нужно пересчитать, иначе три дела перестанут занимать виджет целиком.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        render(context, manager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_day)
        val state = readState(context)

        views.setTextViewText(R.id.widget_empty, state.empty)

        val hasItems = state.items.isNotEmpty()
        views.setViewVisibility(R.id.widget_list, if (hasItems) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_empty, if (hasItems) View.GONE else View.VISIBLE)

        val data = Intent(context, DayWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            putExtra(DayWidgetService.EXTRA_ROW_HEIGHT_DP, rowHeightDp(context, manager, id))
            // Момент установки приложения: макеты строк лежат в apk, и рабочий
            // стол держит уже разложенные строки в кеше. Без этой метки после
            // обновления он показывал бы строки прежнего вида — данные в них
            // менялись, а сам макет оставался старым.
            putExtra(EXTRA_BUILD_STAMP, buildStamp(context))
            // Уникальные данные на каждый виджет и на каждый его размер: система
            // различает фабрики по ним, и без этого после растягивания виджета
            // строки остались бы прежней высоты.
            setData(Uri.parse(toUri(Intent.URI_INTENT_SCHEME)))
        }
        views.setRemoteAdapter(R.id.widget_list, data)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_empty, open)
        views.setOnClickPendingIntent(R.id.widget_mark, open)
        views.setPendingIntentTemplate(R.id.widget_list, open)

        manager.updateAppWidget(id, views)
        // Список сам не перечитывает данные: без этого виджет показывал бы то,
        // что фабрика прочитала в прошлый раз.
        manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
    }

    /**
     * Треть высоты виджета — столько отводится строке.
     *
     * Высота берётся из `MAX_HEIGHT`, хотя имя обещает обратное: система отдаёт
     * пару «minWidth × maxHeight» для портрета и «maxWidth × minHeight» для
     * ландшафта. По `MIN_HEIGHT` строки выходили ниже трети, и в виджет
     * заглядывала четвёртая.
     *
     * Пока размер не известен — виджет только кладут на стол — остаётся
     * значение по умолчанию.
     */
    private fun rowHeightDp(context: Context, manager: AppWidgetManager, id: Int): Int {
        val options = manager.getAppWidgetOptions(id)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (heightDp <= 0) return DayWidgetService.DEFAULT_ROW_HEIGHT_DP

        // Округление вверх: если три строки чуть не добирают до низа, туда
        // заглядывает четвёртая. Лишний dp съедает список — это незаметно.
        val usable = heightDp / hostScale(context) - VERTICAL_PADDING_DP
        val row = ceil(usable / WIDGET_VISIBLE_ROWS).toInt()
        return row.coerceAtLeast(MIN_ROW_HEIGHT_DP)
    }

    /**
     * Во сколько раз dp рабочего стола крупнее наших.
     *
     * Размер виджета система сообщает в dp экрана, а макет строки рабочий стол
     * раскладывает в собственных: виджеты он держит одного вида независимо от
     * того, какой масштаб изображения выбран в настройках телефона. При
     * уменьшенном масштабе строка выходила короче заказанной, и в виджет
     * заглядывало четвёртое дело.
     *
     * Отношение считается по «родной» плотности экрана: если масштаб не трогали,
     * оно равно единице и ничего не меняет.
     */
    private fun hostScale(context: Context): Float {
        val stable = DisplayMetrics.DENSITY_DEVICE_STABLE / DisplayMetrics.DENSITY_DEFAULT.toFloat()
        if (stable <= 0f) return 1f
        return (context.resources.displayMetrics.density / stable).coerceIn(0.5f, 2f)
    }

    private fun readState(context: Context): DayWidgetState {
        val schedule = (context.applicationContext as AskyaApplication).container.scheduleRepository
        val now = LocalDateTime.now()
        return runBlocking {
            DayWidgetData.select(
                now = now,
                today = schedule.itemsOnce(now.toLocalDate()),
                tomorrow = schedule.itemsOnce(now.toLocalDate().plusDays(1)),
            )
        }
    }

    /** Когда apk последний раз ставили — метка для кеша рабочего стола. */
    private fun buildStamp(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime

    companion object {
        /** Отступы подложки сверху и снизу — из `widget_day.xml`. */
        private const val VERTICAL_PADDING_DP = 16

        /** Ниже этого название в строке уже не прочитать. */
        private const val MIN_ROW_HEIGHT_DP = 44

        private const val EXTRA_BUILD_STAMP = "build_stamp"

        /** Перерисовать все виджеты — из приложения и по будильнику. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DayWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, DayWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
