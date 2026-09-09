package app.askya.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.askya.app.AskyaApplication
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Держит виджет в ногу со временем.
 *
 * Обновления раз в полчаса виджету мало: он показывает, что идёт **сейчас**, и
 * ошибаться на двадцать минут для него — то же, что показывать вчерашний день.
 * Поэтому на ближайшую границу дела ставится будильник, а после срабатывания —
 * следующий.
 *
 * Будильник неточный (`setAndAllowWhileIdle`, а не `setExact`): точный на
 * Android 12+ требует отдельного разрешения, а виджету хватает «примерно
 * тогда». Но именно «тогда», а не «когда-нибудь»: обычный `set` в дремоте
 * откладывается до следующего пробуждения телефона, и дело успевало начаться,
 * а виджет об этом не знал — оно стояло в списке неподсвеченным. Разрешённый
 * в дремоте будильник срабатывает у границы, ценой одного пробуждения на дело.
 *
 * «Когда включается телефон» покрыто загрузкой: пробуждение экрана с Android 8
 * манифестом не ловится вовсе, а держать ради этого постоянную службу — цена,
 * которой виджет не стоит.
 */
object DayWidgetRefresh {

    fun scheduleNext(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val next = nextBoundary(context)
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    /**
     * Ближайший момент, когда картинка меняется: начало или конец дела. Если
     * на сегодня ничего не осталось — полночь: в неё виджет переключается на
     * новый день.
     *
     * Секунда сверху — чтобы будильник сработал уже после границы, а не ровно
     * на ней: иначе дело успевало бы считаться идущим ещё один заход.
     */
    private fun nextBoundary(context: Context): LocalDateTime {
        val schedule = (context.applicationContext as AskyaApplication).container.scheduleRepository
        val now = LocalDateTime.now()
        val today = runBlocking { schedule.itemsOnce(now.toLocalDate()) }

        val boundaries = today
            .flatMap { item -> listOfNotNull(item.startTime, item.endTime) }
            .map { time -> now.toLocalDate().atTime(time) }
            .filter { it.isAfter(now) }
            .sorted()

        val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return (boundaries.firstOrNull() ?: midnight).plusSeconds(1)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, DayWidgetAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST = 1
}

/** Будильник сработал: перерисовать и поставить следующий. */
class DayWidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DayWidgetProvider.refresh(context)
        DayWidgetRefresh.scheduleNext(context)
    }
}

/**
 * После перезагрузки будильники стираются, а виджет остаётся на столе. Без
 * этого он замирал бы на том, что показывал до выключения телефона, пока
 * система не разбудит его сама — то есть до получаса.
 */
class DayWidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Обновление приложения снимает будильники так же, как перезагрузка, —
        // и виджет замирал на том, что показывал до новой сборки.
        val known = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!known) return
        DayWidgetProvider.refresh(context)
        DayWidgetRefresh.scheduleNext(context)
    }
}
