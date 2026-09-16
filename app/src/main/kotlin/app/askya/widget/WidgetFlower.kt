package app.askya.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.ui.theme.FlowerColor
import app.askya.ui.theme.at
import app.askya.ui.theme.dayPartNow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Цветок в углу виджета дня — той краской, что выбрана в настройках
 * («Цветок Askya»), а не зашитой в `ic_flower.xml`.
 *
 * Прежде он оставался закатным при любом выборе: вектор разворачивает рабочий
 * стол, темы приложения там нет, и `?attr`, каким красится системная
 * заставка, не разрешился бы. Но у RemoteViews есть свой путь —
 * `ImageView.setColorFilter(int)` вызывается удалённо, — и перекрашивается
 * тот же рисунок, а не восемь его копий: ровно как в приложении
 * ([app.askya.ui.components.AskyaFlower]).
 *
 * Кадров у знака тридцать шесть, и красится каждый: флиппер показывает их по
 * очереди, и один незакрашенный мигал бы закатом раз в 3,6 секунды.
 */
internal object WidgetFlower {

    /** Кадры `ViewFlipper` из `widget_day.xml` — по порядку. */
    private val FRAMES = intArrayOf(
        R.id.widget_mark_0, R.id.widget_mark_1, R.id.widget_mark_2, R.id.widget_mark_3,
        R.id.widget_mark_4, R.id.widget_mark_5, R.id.widget_mark_6, R.id.widget_mark_7,
        R.id.widget_mark_8, R.id.widget_mark_9, R.id.widget_mark_10, R.id.widget_mark_11,
        R.id.widget_mark_12, R.id.widget_mark_13, R.id.widget_mark_14, R.id.widget_mark_15,
        R.id.widget_mark_16, R.id.widget_mark_17, R.id.widget_mark_18, R.id.widget_mark_19,
        R.id.widget_mark_20, R.id.widget_mark_21, R.id.widget_mark_22, R.id.widget_mark_23,
        R.id.widget_mark_24, R.id.widget_mark_25, R.id.widget_mark_26, R.id.widget_mark_27,
        R.id.widget_mark_28, R.id.widget_mark_29, R.id.widget_mark_30, R.id.widget_mark_31,
        R.id.widget_mark_32, R.id.widget_mark_33, R.id.widget_mark_34, R.id.widget_mark_35,
    )

    /** Покрасить все кадры знака краской, выбранной сейчас. */
    fun paint(context: Context, views: RemoteViews) {
        val ink = chosen(context).at(dayPartNow()).color.toArgb()
        FRAMES.forEach { frame -> views.setInt(frame, "setColorFilter", ink) }
    }

    /**
     * Когда краска сменится сама — только у хамелеона, на границах пор.
     *
     * Будильнику виджета ([DayWidgetRefresh]) эти минуты нужны наравне с
     * границами дел: иначе лиловый вечерний цветок приходил бы на стол с
     * первым делом вечера или через полчаса, а не в шесть. Полночь сюда не
     * входит — в неё виджет и так перерисовывается на новый день.
     */
    fun turnsAfter(context: Context, now: LocalDateTime): List<LocalDateTime> {
        if (chosen(context) != FlowerColor.CHAMELEON) return emptyList()
        return listOf(LocalTime.of(10, 0), LocalTime.of(18, 0))
            .map { now.toLocalDate().atTime(it) }
            .filter { it.isAfter(now) }
    }

    /**
     * Выбор из настроек — с ожиданием диска, а не `state`: виджет будит процесс
     * сам, и готового значения к этой минуте ещё нет — `state` отдал бы
     * умолчание, то есть закат.
     */
    private fun chosen(context: Context): FlowerColor {
        val settings = (context.applicationContext as AskyaApplication).container.settings
        return runBlocking { settings.settings.first().flower }
    }
}
