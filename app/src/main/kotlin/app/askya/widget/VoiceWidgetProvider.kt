package app.askya.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.askya.R
import app.askya.app.MainActivity
import app.askya.app.OPEN_ROUTE
import app.askya.app.OPEN_VOICE
import app.askya.app.SAY_NOW
import kotlinx.coroutines.runBlocking

/**
 * Виджет голосовой заметки: кружок с микрофоном на рабочем столе.
 *
 * ## Зачем он
 *
 * Заметку голосом записывают ровно тогда, когда руки заняты и мысль уйдёт
 * через десять секунд: на ходу, за рулём, с пакетами. Путь к ней был в четыре
 * нажатия — открыть Askya, меню, Scroll, «Голос», и уже там кнопку. К
 * четвёртому нажатию записывать обычно нечего.
 *
 * Виджет — тот же путь, свёрнутый в одно нажатие: кружок открывает раздел и
 * тут же начинает запись.
 *
 * ## Почему не пишет прямо с рабочего стола
 *
 * Виджет мог бы начать запись, не открывая приложения, — службой в шторке. Так
 * не сделано намеренно, и это то же правило, по которому запись кончается
 * вместе с уходом с экрана: **Askya не слушает микрофон, когда её не видно.**
 * Служба, пишущая с рабочего стола, — это ровно то, чем Askya быть не хочет, и
 * никакая экономия одного перехода этого не стоит.
 *
 * Есть и вторая причина, попроще: разрешение на микрофон спрашивает окно, а
 * окна у виджета нет. Виджет, который у половины людей молча не работает,
 * хуже виджета, который открывает экран.
 *
 * ## Две области нажатия
 *
 * Кружок начинает запись. Строка рядом открывает «Голос» и ничего не
 * начинает: записанное иногда просто слушают, и одно нажатие не должно
 * означать двух разных дел.
 *
 * ## Он не тикает
 *
 * Часов у виджета нет: показывать ему нечего, кроме последней записи, а она
 * меняется только тогда, когда её сделали. Тогда его и будят из раздела
 * ([refresh]). Всё остальное время он не стоит батарее ничего.
 */
class VoiceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, manager, id) }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_voice)
        val state = runBlocking { VoiceWidgetData.read(context) }

        views.setTextViewText(R.id.widget_voice_last, state.last)

        views.setOnClickPendingIntent(R.id.widget_voice_button, open(context, SAY, saying = true))
        views.setOnClickPendingIntent(R.id.widget_voice_words, open(context, LOOK, saying = false))

        manager.updateAppWidget(id, views)
    }

    /**
     * Намерение «открой раздел» — и, если просят, «и начни писать».
     *
     * `SINGLE_TOP`, как у виджета погоды: приложение, уже открытое, не
     * заводится вторым окном, а получает просьбу в `onNewIntent`.
     */
    private fun open(context: Context, request: Int, saying: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            Intent(context, MainActivity::class.java)
                .putExtra(OPEN_ROUTE, OPEN_VOICE)
                .putExtra(SAY_NOW, saying)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {

        /**
         * Свои номера запросов — чтобы намерения не путались ни между собой,
         * ни с намерениями соседних виджетов: система различает их по номеру и
         * получателю, и одинаковый номер у «записать» и «посмотреть» означал
         * бы, что одно из двух нажатий делает чужое дело.
         */
        private const val SAY = 3
        private const val LOOK = 4

        /**
         * Перерисовать виджеты голоса — из раздела, когда список записей
         * изменился.
         *
         * Ничего не делает, когда виджетов на столе нет.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, VoiceWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, VoiceWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
