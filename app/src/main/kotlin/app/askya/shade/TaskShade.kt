package app.askya.shade

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity
import app.askya.app.OPEN_DEED
import app.askya.app.OPEN_ROUTE
import app.askya.app.OPEN_TODAY
import app.askya.data.entity.DeedTask
import app.askya.data.entity.ScheduleItem
import app.askya.ui.components.formatRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Список дела в шторке уведомлений.
 *
 * ## Зачем
 *
 * Дело со списком — единственное в Askya, к чему возвращаются по десять раз за
 * день и каждый раз ради одного касания: отметить строку. Ради этого касания
 * приходилось разблокировать телефон, найти Askya, открыть день, найти дело,
 * раскрыть карточку. Шторка отдаёт то же касание за один жест сверху вниз.
 *
 * Это не напоминание: оно ничего не напоминает и не звонит. Канал заведён
 * тихим и низкой важности — уведомление ложится в шторку молча и не всплывает
 * поверх экрана.
 *
 * ## Что показывается
 *
 * Дела сегодняшнего дня, у которых есть свой список и в списке осталось
 * несделанное. Каждое — своим уведомлением: «Работа» и «Вечером» это разные
 * дела, и слипшиеся в одно они потеряли бы то, ради чего их и открывают.
 * Отмеченное дело уходит из шторки целиком, дописанный до конца список —
 * тоже: шторка показывает то, что ещё предстоит.
 *
 * Строк показывается [ROWS], дальше — «и ещё столько-то»: уведомление
 * развёрнутым занимает высоту в четверть экрана, и список, не помещающийся в
 * неё, система обрежет молча, без всякого «ещё».
 *
 * ## Почему оно снимается пальцем
 *
 * `setOngoing` не ставится: несмахиваемое уведомление — это то, что человек
 * начинает ненавидеть к третьему дню. Смахнутое вернётся, когда список
 * изменится или когда Askya откроют, — и это честный порядок: шторку убрали,
 * потому что она сейчас мешает, а не потому, что список больше не нужен.
 */
object TaskShade {

    /** Тег своих уведомлений: по нему видно, какие в шторке наши. */
    private const val TAG = "askya-deed-tasks"

    private const val CHANNEL = "deed_tasks"

    /** Сколько строк списка помещается в развёрнутое уведомление. */
    private const val ROWS = 7

    /** Отмеченная строка — тем же серым, что и пустой знак рядом с ней. */
    private const val DONE_INK = 0xFF9A9A9A.toInt()

    /**
     * Цвет заголовка раздела в шторке — коралловый акцент Askya
     * ([app.askya.ui.theme.CoralAccent]). Зашит числом по той же причине, что
     * и серый выше: шторку рисует система своим набором красок, и `?attr`
     * указывал бы на чужую тему. Гамму человек может сменить, а шторка
     * останется коралловой — это цена одного числа против чтения темы из
     * приёмника будильника.
     */
    private const val HEAD_INK = 0xFFD97757.toInt()

    /**
     * Своя область, а не область экрана: шторку пересобирает и приёмник
     * нажатия, у которого экрана нет вовсе.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Пересобрать шторку. Возвращается сразу — работа уходит в фон. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        scope.launch { rebuild(app) }
    }

    /**
     * Пересобирает шторку по тому, что сейчас в базе.
     *
     * Целиком, а не по одному уведомлению: дело могли отметить, убрать, стереть
     * вместе со всем днём или дописать в нём последнюю строку, и ловить каждый
     * из этих случаев отдельным вызовом значило бы однажды забыть один.
     */
    private suspend fun rebuild(context: Context) {
        val container = (context.applicationContext as AskyaApplication).container
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Настройка спрашивается у хранилища, а не у его готового значения:
        // при запуске приложения диск ещё не прочитан, и `state.value` в эти
        // миллисекунды говорит «по умолчанию» — то есть «показывать». Выключивший
        // шторку человек получал бы её обратно на каждом запуске.
        if (!container.settings.settings.first().deedShade) {
            clear(context, manager, keep = emptySet())
            return
        }

        val today = LocalDate.now()
        val deeds = container.scheduleRepository.itemsOnce(today).associateBy { it.id }
        val tasks = container.deedTaskRepository.tasksOnce(today).groupBy { it.deedId }

        ensureChannel(manager)

        val shown = mutableSetOf<Int>()
        deeds.values
            .filterNot { it.done }
            .sortedBy { it.startTime }
            .forEach { deed ->
                val rows = tasks[deed.id].orEmpty()
                if (rows.none { !it.done && !it.heading }) return@forEach
                shown += deed.id.toInt()
                runCatching {
                    NotificationManagerCompat.from(context)
                        .notify(TAG, deed.id.toInt(), build(context, deed, rows))
                }
            }

        clear(context, manager, keep = shown)
    }

    /**
     * Убрать из шторки свои уведомления, кроме названных.
     *
     * Спрашивается у системы, что там висит, а не помнится своим списком:
     * помнить его пришлось бы между запусками приложения, а процесс живёт
     * меньше, чем уведомление.
     */
    private fun clear(context: Context, manager: NotificationManager, keep: Set<Int>) {
        runCatching {
            manager.activeNotifications
                .filter { it.tag == TAG && it.id !in keep }
                .forEach { NotificationManagerCompat.from(context).cancel(TAG, it.id) }
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL) != null) return
        val channel = NotificationChannel(
            CHANNEL,
            "Списки дел",
            // Тихо и без всплытия поверх экрана: это не напоминание, а карточка,
            // положенная под руку. Звонить ей не о чем.
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Список задач внутри дела — под рукой, в шторке."
        channel.setShowBadge(false)
        manager.createNotificationChannel(channel)
    }

    private fun build(context: Context, deed: ScheduleItem, tasks: List<DeedTask>): Notification {
        // Заголовки разделов в счёт не идут: отмечать в них нечего, и «2 из 9»
        // с двумя заголовками внутри обещало бы девять дел там, где их семь.
        // В самой шторке они стоят — тем же порядком, что и в карточке.
        val lines = tasks.filterNot { it.heading }
        val left = lines.count { !it.done }
        val meta = formatRange(deed.startTime, deed.endTime) +
            " · " + (lines.size - left) + " из " + lines.size

        val big = RemoteViews(context.packageName, R.layout.shade_tasks)
        big.setTextViewText(R.id.shade_title, deed.title)
        big.setTextViewText(R.id.shade_meta, meta)
        big.removeAllViews(R.id.shade_rows)
        tasks.take(ROWS).forEach { task -> big.addView(R.id.shade_rows, row(context, task)) }

        val hidden = tasks.size - ROWS
        if (hidden > 0) {
            big.setTextViewText(R.id.shade_more, "и ещё $hidden")
        } else {
            big.setTextViewText(R.id.shade_more, "")
        }

        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_flower)
            // Свёрнутым уведомление остаётся обычным: название дела и сколько
            // в нём осталось. Список нужен развёрнутым, а свёрнутая шторка —
            // это строка, и втискивать в неё галочки некуда.
            .setContentTitle(deed.title)
            .setContentText(meta)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomBigContentView(big)
            .setContentIntent(open(context, deed.id))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_shade_box_done),
                    "Дело сделано",
                    intent(context, TaskShadeReceiver.ACTION_DONE, deed.id),
                ).build(),
            )
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Строка списка. Нажимается целиком: попасть пальцем в квадратик двадцати
     * точек шириной, не глядя, нельзя, а в шторку смотрят на ходу.
     */
    private fun row(context: Context, task: DeedTask): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.shade_task_row)
        view.setTextViewText(R.id.shade_row_text, task.text)

        // Заголовок раздела: без квадрата и без нажатия — отмечать в нём
        // нечего. Пустой знак вместо квадрата, а не спрятанный: RemoteViews
        // умеет менять картинку, а прятать её пришлось бы видимостью, которая
        // потом остаётся у переиспользованной строки.
        if (task.heading) {
            view.setImageViewResource(R.id.shade_row_mark, R.drawable.ic_shade_box_blank)
            view.setTextColor(R.id.shade_row_text, HEAD_INK)
            return view
        }

        view.setImageViewResource(
            R.id.shade_row_mark,
            if (task.done) R.drawable.ic_shade_box_done else R.drawable.ic_shade_box,
        )
        // Отмеченное не вычёркивается, а гаснет — как в списках Yet:
        // зачёркнутое читается как отменённое, а строку выполнили.
        if (task.done) view.setTextColor(R.id.shade_row_text, DONE_INK)
        view.setOnClickPendingIntent(
            R.id.shade_row,
            intent(context, TaskShadeReceiver.ACTION_TOGGLE, task.id),
        )
        return view
    }

    /** Нажали на само уведомление — открыть день на этом деле. */
    private fun open(context: Context, deedId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            // Адрес, а не только дополнения: два намерения с разными
            // дополнениями система считает одним и тем же, и без своего адреса
            // все уведомления открывали бы одно и то же дело — то, чьё
            // намерение создали первым.
            .setData(Uri.parse("askya://deed/$deedId"))
            .putExtra(OPEN_ROUTE, OPEN_TODAY)
            .putExtra(OPEN_DEED, deedId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Нажали на строку или на «Дело сделано». Про адрес — см. [open]. */
    private fun intent(context: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, TaskShadeReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("askya://$action/$id"))
                .putExtra(TaskShadeReceiver.EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
