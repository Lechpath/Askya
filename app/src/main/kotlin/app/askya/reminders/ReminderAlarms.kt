package app.askya.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity
import app.askya.data.backup.SnapshotAlarms
import app.askya.data.entity.Reminder
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

/**
 * Напоминания: будильник в системе и уведомление, когда он сработает.
 *
 * `AlarmManager`, а не `WorkManager`: напоминание привязано к минуте, а
 * WorkManager обещает выполнить работу «когда-нибудь около» и в дремлющем
 * телефоне откладывает её на четверть часа. Для «напомни в 19:00» это негодно.
 *
 * Будильник неточный (`set`, а не `setExact`): точный требует разрешения
 * `SCHEDULE_EXACT_ALARM`, которое с Android 14 выпрашивают отдельным экраном
 * системных настроек. Неточный система сдвигает на минуты, и для напоминания
 * о деле это приемлемая цена за то, что оно просто работает без уговоров.
 *
 * Канал под каждую мелодию: звук уведомления в Android задаётся каналом, и
 * после создания канал уже не переделать. Одним каналом на все напоминания
 * сменить мелодию можно было бы только всем сразу — а мелодию выбирают этому
 * делу. Плюс отдельный тихий канал: «молча» — тоже способ.
 *
 * Звучит по-будильничьи ([AudioAttributes.USAGE_ALARM]), а не по-уведомленчьи:
 * беззвучный режим глушит звонок и уведомления, но не будильники. Напоминание,
 * которое человек сам поставил на час, — это будильник и есть, и молчать ему
 * из-за переключателя на боку телефона незачем. Режим «не беспокоить» пропускает
 * будильники, если их там не запретили отдельно.
 */
object ReminderAlarms {

    private const val CHANNEL_GROUP = "reminders_group"
    private const val CHANNEL_SILENT = "reminders_silent"

    /** Канал мелодии: у каждой свой, потому что звук канала не переделать. */
    private const val CHANNEL_SOUND = "reminder_sound_"

    /**
     * Прежний общий канал звучащих напоминаний. Он играл звуком уведомления и
     * потому молчал в беззвучном режиме; переделать его нельзя — только не
     * пользоваться им больше и убрать из настроек телефона, чтобы там не висел
     * канал, которым ничего не звонит.
     */
    private const val CHANNEL_LEGACY = "reminders"

    private const val EXTRA_ID = "reminder_id"

    fun schedule(context: Context, reminder: Reminder) {
        if (!reminder.enabled) {
            cancel(context, reminder.id)
            return
        }

        val at = reminder.date.atTime(reminder.time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Прошедшее не ставится: система выстрелила бы им немедленно, и
        // человек получил бы напоминание о том, что уже прошло.
        if (at <= System.currentTimeMillis()) return

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent(context, reminder.id))
    }

    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, id))
    }

    /** Перепланировать всё после перезагрузки: будильники её не переживают. */
    fun rescheduleAll(context: Context) {
        val reminders = (context.applicationContext as AskyaApplication).container.reminderRepository
        runBlocking { reminders.enabled() }.forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        // Номер напоминания — код заявки: без этого второе напоминание
        // затирало бы первое.
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, reminder: Reminder) {
        val channel = ensureChannels(context, reminder)

        val open = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(reminder.title)
            .setContentText("Напоминание Askya")
            .setContentIntent(open)
            .setAutoCancel(true)
            // Будильник, а не сообщение: по этому признаку система решает,
            // пропускать ли звук в «не беспокоить».
            .setCategory(Notification.CATEGORY_ALARM)
            .build()

        // Разрешение на уведомления могли не дать: тогда система молча
        // откажет, и падать из-за этого напоминанию незачем.
        runCatching {
            NotificationManagerCompat.from(context).notify(reminder.id.toInt(), notification)
        }
    }

    /**
     * Заводит канал под этот способ и возвращает его.
     *
     * Тихий канал один на все молчащие напоминания; у звучащих канал свой на
     * каждую мелодию — иначе выбранная однажды песня осталась бы у всех
     * напоминаний навсегда. Названием канала стоит имя мелодии: в настройках
     * телефона человек увидит ровно то, что выбирал.
     */
    private fun ensureChannels(context: Context, reminder: Reminder): String {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return CHANNEL_SILENT

        manager.createNotificationChannelGroup(
            NotificationChannelGroup(CHANNEL_GROUP, "Напоминания"),
        )
        // Каналов у напоминаний столько, сколько выбирали мелодий, и без
        // группы они рассыпались бы по настройкам вперемешку со всем прочим.
        runCatching { manager.deleteNotificationChannel(CHANNEL_LEGACY) }

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SILENT,
                "Напоминания без звука",
                // LOW — это и есть «без звука и без вибрации» в терминах
                // системы: канал с этой важностью появляется в шторке молча.
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                group = CHANNEL_GROUP
                setSound(null, null)
            },
        )

        if (reminder.silent) return CHANNEL_SILENT

        val id = CHANNEL_SOUND + soundKey(reminder.sound)
        manager.createNotificationChannel(
            NotificationChannel(
                id,
                reminder.soundTitle ?: "Напоминания",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                group = CHANNEL_GROUP
                setSound(
                    ReminderSounds.uriOf(reminder.sound),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
            },
        )

        // Мелодию из музыки телефона играет система, а файл — чужой: без явной
        // выдачи права шторка на части телефонов промолчит. Отказ здесь не
        // страшен — там, где право и не требовалось, оно и так есть.
        runCatching {
            reminder.sound?.let { sound ->
                context.grantUriPermission(
                    "com.android.systemui",
                    ReminderSounds.uriOf(sound),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }

        return id
    }

    /**
     * Имя канала по мелодии. Ссылка целиком в имя не годится — в ней есть
     * что угодно, а канал живёт в настройках телефона; берётся её отпечаток.
     */
    private fun soundKey(sound: String?): String =
        sound?.let { Integer.toHexString(it.hashCode()) } ?: "default"

    /** Нужно ли просить разрешение на уведомления — оно появилось в Android 13. */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/** Будильник сработал: показать уведомление. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("reminder_id", -1L)
        if (id <= 0) return

        val reminders = (context.applicationContext as AskyaApplication).container.reminderRepository
        val reminder = runBlocking { reminders.get(id) } ?: return
        if (!reminder.enabled) return

        ReminderAlarms.notify(context, reminder)
    }
}

/**
 * После перезагрузки будильники стёрты — ставим заново.
 *
 * Заодно и месячное напоминание про «Слепок»: приёмник тот же, потому что
 * повод один — перезагрузка, а второй приёмник на то же событие означал бы
 * два места, где помнят про будильники Askya.
 */
class ReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ReminderAlarms.rescheduleAll(context)
        SnapshotAlarms.reschedule(context)
    }
}
