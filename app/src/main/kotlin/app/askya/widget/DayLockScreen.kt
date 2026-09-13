package app.askya.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.core.app.NotificationManagerCompat
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity
import app.askya.app.OPEN_ROUTE
import app.askya.app.OPEN_TODAY
import app.askya.data.entity.ScheduleItem
import app.askya.ui.components.formatRange
import app.askya.ui.components.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/**
 * Дела дня на экране блокировки — тот же список, что в виджете дня.
 *
 * ## Почему уведомление, а не виджет
 *
 * Сторонние виджеты на экран блокировки Android не пускает с версии 5.0, а
 * MIUI и HyperOS держат там только свои. Уведомление — единственное, что
 * приложение может туда положить: оно видно на заблокированном телефоне, если
 * человек не запретил уведомления на экране блокировки в настройках системы.
 *
 * ## Что показывается
 *
 * Отбор тот же, что у виджета ([DayWidgetData.select]): от текущего момента
 * вперёд, прошедшего нет, а когда на сегодня всё — завтрашнее. Свёрнутым (а на
 * экране блокировки уведомления лежат свёрнутыми) — идущее или ближайшее дело
 * и следом за ним остальные одной строкой. Развёрнутым — столбиком.
 *
 * Идущее сейчас выделено жирным, а не цветом: цветные куски текста система в
 * обычном уведомлении перекрашивает своей краской, жирный — оставляет.
 *
 * Пустой день — никакого уведомления: «на сегодня всё» на экране блокировки —
 * это надпись, которая ничего не сообщает, но занимает место.
 *
 * ## Почему висит
 *
 * `setOngoing` стоит нарочно, в отличие от шторки со списками дела: с
 * Android 14 такое уведомление на заблокированном телефоне не смахнуть
 * случайным жестом, а на разблокированном — можно. Смахнутое вернётся со
 * следующей границей дела.
 *
 * ## Кто его обновляет
 *
 * Те же будильники на границы дел, что держат виджет ([DayWidgetRefresh]), —
 * и пока приложение живо, поток расписания в `AskyaApplication`.
 */
object DayLockScreen {

    private const val CHANNEL = "day_lock_screen"

    /** Одно уведомление на всё: список дня один. */
    private const val ID = 1

    private const val TAG = "askya-day-lock"

    /**
     * Строк в развёрнутом виде. Системный столбик (`InboxStyle`) больше семи
     * не показывает, а седьмую отдаём под «и ещё».
     */
    private const val ROWS = 6

    /** Сколько дел уходит в строку под заголовком свёрнутого уведомления. */
    private const val LINE_AFTER = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Пересборки идут по одной: поток расписания и будильник могут прийти
     * разом, и опоздавший со старым списком лёг бы поверх свежего.
     */
    private val order = Mutex()

    /** Пересобрать уведомление. Возвращается сразу — работа уходит в фон. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { update(app) }
    }

    /**
     * Пересобрать и дождаться — для приёмников: им процесс держат живым,
     * только пока они сами не отпустят (`goAsync`).
     */
    suspend fun update(context: Context) {
        order.withLock { runCatching { rebuild(context.applicationContext) } }
    }

    /** [update] из приёмника: процесс держится, пока пересборка не кончится. */
    fun updateAsync(receiver: BroadcastReceiver, context: Context) {
        val app = context.applicationContext
        val done = receiver.goAsync()
        scope.launch {
            try {
                update(app)
            } finally {
                done.finish()
            }
        }
    }

    /** Включено ли — для тех, кому надо знать, нужен ли ещё будильник. */
    suspend fun enabled(context: Context): Boolean =
        (context.applicationContext as AskyaApplication).container.settings.settings.first().dayLockScreen

    private suspend fun rebuild(context: Context) {
        val container = (context.applicationContext as AskyaApplication).container
        val notifications = NotificationManagerCompat.from(context)

        // Настройка — у хранилища, а не у готового значения: на запуске диск
        // ещё не прочитан, и значение по умолчанию выключило бы уведомление,
        // которое человек включил (см. то же в `TaskShade`).
        if (!container.settings.settings.first().dayLockScreen) {
            notifications.cancel(TAG, ID)
            return
        }

        val now = LocalDateTime.now()
        val schedule = container.scheduleRepository
        val state = DayWidgetData.select(
            now = now,
            today = schedule.itemsOnce(now.toLocalDate()),
            tomorrow = schedule.itemsOnce(now.toLocalDate().plusDays(1)),
        )

        // Цепочку будильников заводит и включение этой настройки: виджета на
        // столе может не быть, а без будильника список застыл бы на том деле,
        // которое шло в момент включения.
        DayWidgetRefresh.scheduleNext(context)

        if (state.items.isEmpty()) {
            notifications.cancel(TAG, ID)
            return
        }

        ensureChannel(context)
        runCatching { notifications.notify(TAG, ID, build(context, state, now)) }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            "День на экране блокировки",
            // Низкая, а не минимальная: минимальную система с экрана
            // блокировки прячет, а ради него канал и заведён. Звука и
            // всплытия у низкой нет.
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Дела дня от текущего часа — видны, не разблокируя телефон."
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun build(context: Context, state: DayWidgetState, now: LocalDateTime): Notification {
        val moment = now.toLocalTime()
        val items = state.items
        val first = items.first()
        val current = !state.tomorrow && DayWidgetData.isNow(first, moment)

        // Заголовок — то, ради чего смотрят на экран: что идёт или что дальше.
        val title = when {
            state.tomorrow -> "Завтра · ${formatTime(first.startTime)} ${first.title}"
            current -> first.endTime
                ?.let { "${first.title} · до ${formatTime(it)}" }
                ?: first.title
            else -> "${formatTime(first.startTime)} ${first.title}"
        }
        val rest = items.drop(1)
        val text = rest.take(LINE_AFTER)
            .joinToString(" · ") { "${formatTime(it.startTime)} ${it.title}" }
            .let { if (rest.size > LINE_AFTER) "$it · …" else it }

        val column = Notification.InboxStyle()
            .setBigContentTitle(if (state.tomorrow) "Завтра" else "Сегодня")
        items.take(ROWS).forEach { item ->
            column.addLine(line(item, !state.tomorrow && DayWidgetData.isNow(item, moment)))
        }
        val hidden = items.size - ROWS
        if (hidden > 0) column.setSummaryText("и ещё $hidden")

        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_flower)
            .setContentTitle(title)
            .setContentText(text.ifEmpty { null })
            .setStyle(column)
            .setContentIntent(open(context))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_EVENT)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Строка столбика: время и название, идущее — жирным целиком. */
    private fun line(item: ScheduleItem, current: Boolean): CharSequence {
        val text = "${formatRange(item.startTime, item.endTime)}   ${item.title}"
        if (!current) return text
        return SpannableString(text).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun open(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST,
        Intent(context, MainActivity::class.java)
            .putExtra(OPEN_ROUTE, OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Свой номер запроса: шторка и виджет открывают MainActivity тем же
     * классом, и под одним номером система отдала бы им одно намерение на всех.
     */
    private const val REQUEST = 7
}
