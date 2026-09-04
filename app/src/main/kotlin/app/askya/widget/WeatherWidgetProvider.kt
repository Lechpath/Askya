package app.askya.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.app.MainActivity
import app.askya.app.OPEN_ROUTE
import app.askya.app.OPEN_WEATHER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Виджет погоды на рабочем столе.
 *
 * Показывает то же, ради чего в меню приложения стоит строка погоды: сколько
 * сейчас градусов и что за окном, — и ещё четыре часа вперёд, ради которых в
 * раздел и заходят («брать ли зонт»). Дальше четырёх часов начинается прогноз,
 * а прогноз смотрят в разделе, а не на рабочем столе.
 *
 * Полупрозрачный и без подложки, как виджет дня: сквозь него видно обои.
 *
 * ## Откуда берётся погода
 *
 * Виджет **ничего не спрашивает у сети сам**. Всё, что он умеет, — прочитать
 * запомненный приложением ответ ([WeatherWidgetData]) и попросить
 * `WeatherRepository` обновиться. Так на телефоне остаётся один свод правил о
 * погоде: где мы, как часто спрашивать, что делать без сети и без разрешения.
 * Второй свод в виджете разошёлся бы с первым в первый же день — и человек
 * увидел бы на столе одну погоду, а в приложении другую.
 *
 * Обновившись, репозиторий сам перерисовывает виджет ([refresh]) — не потому,
 * что виджет об этом просил, а потому что новая погода есть новая погода:
 * сходило за ней приложение или рабочий стол, значения не имеет.
 *
 * Круга из этого не выходит: обновление, за которым не последовало нового
 * ответа (погода ещё свежая), никого не будит — репозиторий в таком случае
 * молчит.
 *
 * Просить недостаточно — надо ещё дождаться: приёмник намерения живёт до конца
 * `onUpdate`, а поднятый ради него процесс после этого могут усыпить вместе с
 * незаконченным запросом. Поэтому [onUpdate] берёт отсрочку (`goAsync`) и
 * отпускает её, когда репозиторий закончил разговор с сетью.
 */
class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, manager, id) }
        // Просьба обновиться идёт после отрисовки, а не до: запомненное надо
        // показать сейчас, а не после разговора с сетью.
        val job = (context.applicationContext as AskyaApplication).container.weather.refresh()
            ?: return

        // Приёмник живёт ровно до конца `onUpdate`, а процесс, поднятый ради
        // одного намерения, после этого разрешено усыпить или убить. Виджету
        // это стоило бы всего: он сам в сеть не ходит, а тот, кто ходит за него,
        // не успевал бы вернуться — и на столе оставалась бы ночная погода до
        // тех пор, пока приложение не откроют руками.
        //
        // `goAsync` держит приёмник живым, пока идёт запрос. Ожидание с
        // потолком: обещание «я скоро закончу» система принимает не навсегда,
        // и не отпущенный вовремя приёмник — это уже не замерший виджет, а
        // «приложение не отвечает».
        val finish = goAsync()
        WAITING.launch {
            try {
                withTimeoutOrNull(WAIT_MS) { job.join() }
            } finally {
                finish.finish()
            }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_weather)
        val state = runBlocking { WeatherWidgetData.read(context) }

        views.setViewVisibility(R.id.widget_weather_body, if (state.known) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_weather_empty, if (state.known) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_weather_empty, state.empty)

        if (state.known) {
            views.setTextViewText(R.id.widget_weather_mark, state.mark)
            views.setTextViewText(R.id.widget_weather_degrees, state.degrees)
            views.setTextViewText(R.id.widget_weather_words, state.words)
            // Место и время запроса стоят одной строкой: обе подписи про то,
            // «чья это погода и когда», и разводить их по разным углам
            // маленького виджета незачем.
            views.setTextViewText(
                R.id.widget_weather_place,
                listOf(state.place, state.aged).filter { it.isNotBlank() }.joinToString(" · "),
            )

            HOUR_ROWS.forEachIndexed { index, row ->
                val hour = state.hours.getOrNull(index)
                views.setViewVisibility(row.box, if (hour == null) View.INVISIBLE else View.VISIBLE)
                if (hour == null) return@forEachIndexed
                views.setTextViewText(row.time, hour.time)
                views.setTextViewText(row.sign, hour.mark)
                views.setTextViewText(row.degrees, hour.degrees)
            }
        }

        // Нажатие ведёт не «в приложение», а в раздел погоды: смотревший на
        // градусы хочет подробностей о них, а не сегодняшний день.
        val open = PendingIntent.getActivity(
            context,
            REQUEST,
            Intent(context, MainActivity::class.java)
                .putExtra(OPEN_ROUTE, OPEN_WEATHER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_weather_root, open)

        manager.updateAppWidget(id, views)
    }

    /** Один столбик часа: время, знак и градусы. */
    private data class HourRow(val box: Int, val time: Int, val sign: Int, val degrees: Int)

    companion object {

        /**
         * Свой номер запроса на намерение — чтобы оно не путалось с намерением
         * виджета дня: система различает их по номеру и получателю.
         */
        private const val REQUEST = 2

        /**
         * Сколько ждать погоду, держа приёмник.
         *
         * Двадцать секунд — потолок самого запроса: десять на соединение и
         * десять на ответ (`WeatherService.TIMEOUT_MS`). Дольше ждать нечего, а
         * система на приёмник, взявший отсрочку, отводит меньше минуты.
         */
        private const val WAIT_MS = 20_000L

        /**
         * Здесь ждут ответа приёмники. Не в приёмнике: его создают на одно
         * намерение и тут же забывают, а ожидание переживает и его, и — вот
         * ради чего всё — тот миг, когда `onUpdate` вернул управление.
         */
        private val WAITING = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val HOUR_ROWS = listOf(
            HourRow(
                box = R.id.widget_weather_hour_1,
                time = R.id.widget_weather_time_1,
                sign = R.id.widget_weather_sign_1,
                degrees = R.id.widget_weather_degrees_1,
            ),
            HourRow(
                box = R.id.widget_weather_hour_2,
                time = R.id.widget_weather_time_2,
                sign = R.id.widget_weather_sign_2,
                degrees = R.id.widget_weather_degrees_2,
            ),
            HourRow(
                box = R.id.widget_weather_hour_3,
                time = R.id.widget_weather_time_3,
                sign = R.id.widget_weather_sign_3,
                degrees = R.id.widget_weather_degrees_3,
            ),
            HourRow(
                box = R.id.widget_weather_hour_4,
                time = R.id.widget_weather_time_4,
                sign = R.id.widget_weather_sign_4,
                degrees = R.id.widget_weather_degrees_4,
            ),
        )

        /**
         * Перерисовать все виджеты погоды — из приложения, когда оно принесло
         * новую погоду.
         *
         * Ничего не делает, когда виджетов на столе нет: рассылать намерение
         * некому.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, WeatherWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }
}
