package app.askya.data.backup

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity

/**
 * «Пора сделать новый слепок» — раз в месяц.
 *
 * Тем же `AlarmManager`, которым Askya ставит напоминания о делах, и по той же
 * причине, по какой не берётся `WorkManager`: своей службы у приложения нет и
 * заводить её ради одной строчки в шторке не за чем.
 *
 * Будильник неточный: слепок делают не в 12:00, а «на днях», и выпрашивать
 * ради этого `SCHEDULE_EXACT_ALARM` отдельным экраном системных настроек было
 * бы платой не по товару.
 *
 * Отсчёт идёт от последнего слепка, а не от календарного первого числа:
 * человеку важно «месяц не делал», а не «наступил сентябрь». Сделал слепок —
 * месяц пошёл заново.
 */
object SnapshotAlarms {

    private const val CHANNEL = "snapshot"
    private const val NOTIFICATION = 90_001
    private const val REQUEST = 90_001

    /** Месяц — тридцать суток. Календарные месяцы разной длины здесь ни к чему. */
    const val PERIOD_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * Ставит или снимает напоминание по тому, что записано в настройках.
     *
     * Зовётся отовсюду, где эти две вещи меняются: при запуске приложения, при
     * щелчке выключателя и после каждого записанного слепка. Одна дорога вместо
     * трёх — иначе выключенное напоминание однажды осталось бы стоять.
     */
    fun sync(context: Context, enabled: Boolean, lastAt: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = pendingIntent(context)

        if (!enabled || lastAt <= 0L) {
            manager.cancel(intent)
            return
        }

        val at = lastAt + PERIOD_MS
        // Прошедшее не ставится: система выстрелила бы им немедленно. Месяц
        // уже прошёл — значит, напомнить надо сейчас, а не будильником.
        if (at <= System.currentTimeMillis()) {
            manager.cancel(intent)
            notify(context)
            return
        }
        manager.set(AlarmManager.RTC_WAKEUP, at, intent)
    }

    /** Перепланировать после перезагрузки: будильники её не переживают. */
    fun reschedule(context: Context) {
        val settings = (context.applicationContext as AskyaApplication).container.settings.state.value
        sync(context, settings.snapshotReminder, settings.snapshotAt)
    }

    internal fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Слепок",
                // LOW — молча. Это не будильник и не дело: строчка в шторке,
                // которую увидят, когда возьмут телефон в руки.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val open = PendingIntent.getActivity(
            context,
            REQUEST,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle("Месяц без слепка")
            .setContentText("Записи за это время живут в одном месте. Настройки → Слепок.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION, notification)
        }
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, SnapshotReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Месяц прошёл — сказать об этом. */
class SnapshotReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val settings = (context.applicationContext as AskyaApplication).container.settings.state.value
        if (!settings.snapshotReminder) return
        SnapshotAlarms.notify(context)
    }
}
