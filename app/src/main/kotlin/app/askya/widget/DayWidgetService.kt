package app.askya.widget

import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import app.askya.R
import app.askya.app.AskyaApplication
import app.askya.data.entity.ScheduleItem
import app.askya.ui.components.formatTime
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

/** Поставщик строк для списка виджета. */
class DayWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val rowHeightDp = intent.getIntExtra(EXTRA_ROW_HEIGHT_DP, DEFAULT_ROW_HEIGHT_DP)
        return DayWidgetFactory(this, rowHeightDp)
    }

    companion object {
        const val EXTRA_ROW_HEIGHT_DP = "row_height_dp"

        /** Если размер виджета ещё не известен — пока не положили на стол. */
        const val DEFAULT_ROW_HEIGHT_DP = 80
    }
}

/**
 * Читает расписание и отдаёт строки виджету.
 *
 * `runBlocking` здесь уместен: система вызывает фабрику на своём фоновом потоке
 * и ждёт ответа синхронно — асинхронного контракта у неё просто нет.
 *
 * **Почему макетов много.** Высоту строки в списке виджета надёжно задаёт
 * только сам макет. Проверено на Android 15: `setViewLayoutHeight` на вложенном
 * контейнере молча не применяется, на корне строки — схлопывает виджет целиком,
 * а `setViewPadding` не заставляет список перемерить строку. Поэтому макеты
 * заготовлены на дюжину высот, а фабрика берёт ближайшую снизу к трети виджета:
 * три дела занимают его целиком, остальные достаются прокруткой.
 *
 * Угасания по номеру строки нет. Список прокручивается, а RemoteViews на
 * прокрутку не отзывается: четвёртая строка оставалась бы бледной и после того,
 * как её вывели на середину. Выделено только идущее сейчас дело — цветом.
 */
private class DayWidgetFactory(
    private val service: DayWidgetService,
    rowHeightDp: Int,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<ScheduleItem> = emptyList()
    private var tomorrow = false

    /**
     * Ближайшая сверху заготовленная высота: три строки должны закрыть виджет
     * целиком. Недобор виднее перебора — снизу заглядывает четвёртое дело.
     */
    private val layoutIndex = ROW_LAYOUTS
        .indexOfFirst { (height, _) -> height >= rowHeightDp }
        .takeIf { it >= 0 }
        ?: ROW_LAYOUTS.lastIndex

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val schedule = (service.applicationContext as AskyaApplication).container.scheduleRepository
        val now = LocalDateTime.now()

        val state = runBlocking {
            DayWidgetData.select(
                now = now,
                today = schedule.itemsOnce(now.toLocalDate()),
                tomorrow = schedule.itemsOnce(now.toLocalDate().plusDays(1)),
            )
        }
        items = state.items
        tomorrow = state.tomorrow
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(service.packageName, ROW_LAYOUTS[layoutIndex].second)

        // Строку рабочий стол просит по числу, которое узнал до последнего
        // `onDataSetChanged`: дело успело уйти в прошлое, список стал короче — и
        // такого номера в нём уже нет. Пустая строка вместо падения: следующий
        // заход всё равно приходит следом, и место под неё исчезнет само.
        //
        // Ценой ошибки здесь было не пустое место, а замерший виджет: падение
        // на биндер-потоке уносило всё приложение, а вместе с ним и связь
        // рабочего стола со списком. Связь остаётся мёртвой навсегда — строки
        // застывают на том, что стол успел получить, и не меняются больше ни по
        // будильнику, ни из приложения.
        val item = items.getOrNull(position) ?: return views

        // Завтрашнее не бывает идущим сейчас: в одиннадцать вечера утренний
        // подъём формально «уже начался» и подсветился бы как текущее дело.
        //
        // Час спрашивается заново, а не берётся из отбора: строки рабочий стол
        // просит и просто так — перерисовывая экран, — и тогда подсветка встаёт
        // на место сама, не дожидаясь будильника.
        val current = !tomorrow && DayWidgetData.isNow(item, LocalDateTime.now().toLocalTime())
        val ink = ContextCompat.getColor(service, R.color.widget_ink)
        val accent = ContextCompat.getColor(service, R.color.widget_accent)
        val muted = ContextCompat.getColor(service, R.color.widget_muted)

        views.setTextViewText(R.id.item_time, timeOf(item))
        views.setTextViewText(R.id.item_title, item.title)
        views.setTextColor(R.id.item_title, if (current) accent else ink)
        views.setTextColor(R.id.item_time, if (current) accent else muted)

        // Тап по строке открывает приложение: заполняется шаблон, заданный
        // провайдером, — своего PendingIntent на строку коллекция не разрешает.
        views.setOnClickFillInIntent(R.id.item_root, Intent())

        return views
    }

    /**
     * Время дела, а у завтрашних — с днём.
     *
     * Подпись стоит на каждой строке, а не только на первой: список
     * прокручивается, и на второй строке напоминание нужно не меньше.
     */
    private fun timeOf(item: ScheduleItem): String {
        val span = item.endTime
            ?.let { "${formatTime(item.startTime)} – ${formatTime(it)}" }
            ?: formatTime(item.startTime)
        return if (tomorrow) "Завтра · $span" else span
    }

    override fun getLoadingView(): RemoteViews? = null

    /**
     * Типов столько же, сколько заготовленных высот. Тип строки система выводит
     * из макета сама, но верхнюю границу спрашивает здесь: занизишь — список
     * переиспользует строку прежней высоты после того, как виджет растянули.
     */
    override fun getViewTypeCount(): Int = ROW_LAYOUTS.size

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        /** Высота в dp и макет под неё, по возрастанию — по нему идёт поиск. */
        val ROW_LAYOUTS = listOf(
            44 to R.layout.widget_day_item_44,
            46 to R.layout.widget_day_item_46,
            48 to R.layout.widget_day_item_48,
            50 to R.layout.widget_day_item_50,
            52 to R.layout.widget_day_item_52,
            54 to R.layout.widget_day_item_54,
            56 to R.layout.widget_day_item_56,
            58 to R.layout.widget_day_item_58,
            60 to R.layout.widget_day_item_60,
            62 to R.layout.widget_day_item_62,
            64 to R.layout.widget_day_item_64,
            66 to R.layout.widget_day_item_66,
            68 to R.layout.widget_day_item_68,
            70 to R.layout.widget_day_item_70,
            72 to R.layout.widget_day_item_72,
            74 to R.layout.widget_day_item_74,
            76 to R.layout.widget_day_item_76,
            78 to R.layout.widget_day_item_78,
            80 to R.layout.widget_day_item_80,
            82 to R.layout.widget_day_item_82,
            84 to R.layout.widget_day_item_84,
            86 to R.layout.widget_day_item_86,
            88 to R.layout.widget_day_item_88,
            90 to R.layout.widget_day_item_90,
            92 to R.layout.widget_day_item_92,
            94 to R.layout.widget_day_item_94,
            96 to R.layout.widget_day_item_96,
            98 to R.layout.widget_day_item_98,
            100 to R.layout.widget_day_item_100,
            102 to R.layout.widget_day_item_102,
            104 to R.layout.widget_day_item_104,
            106 to R.layout.widget_day_item_106,
            108 to R.layout.widget_day_item_108,
            110 to R.layout.widget_day_item_110,
            112 to R.layout.widget_day_item_112,
            114 to R.layout.widget_day_item_114,
            116 to R.layout.widget_day_item_116,
            118 to R.layout.widget_day_item_118,
            120 to R.layout.widget_day_item_120,
            122 to R.layout.widget_day_item_122,
            124 to R.layout.widget_day_item_124,
            126 to R.layout.widget_day_item_126,
            128 to R.layout.widget_day_item_128,
            130 to R.layout.widget_day_item_130,
            132 to R.layout.widget_day_item_132,
            134 to R.layout.widget_day_item_134,
            136 to R.layout.widget_day_item_136,
            138 to R.layout.widget_day_item_138,
            140 to R.layout.widget_day_item_140,
            142 to R.layout.widget_day_item_142,
            144 to R.layout.widget_day_item_144,
            146 to R.layout.widget_day_item_146,
            148 to R.layout.widget_day_item_148,
            150 to R.layout.widget_day_item_150,
            152 to R.layout.widget_day_item_152,
            154 to R.layout.widget_day_item_154,
            156 to R.layout.widget_day_item_156,
            158 to R.layout.widget_day_item_158,
            160 to R.layout.widget_day_item_160,
            162 to R.layout.widget_day_item_162,
            164 to R.layout.widget_day_item_164,
            166 to R.layout.widget_day_item_166,
            168 to R.layout.widget_day_item_168,
            170 to R.layout.widget_day_item_170,
            172 to R.layout.widget_day_item_172,
            174 to R.layout.widget_day_item_174,
            176 to R.layout.widget_day_item_176,
            178 to R.layout.widget_day_item_178,
            180 to R.layout.widget_day_item_180,
            182 to R.layout.widget_day_item_182,
            184 to R.layout.widget_day_item_184,
            186 to R.layout.widget_day_item_186,
            188 to R.layout.widget_day_item_188,
            190 to R.layout.widget_day_item_190,
            192 to R.layout.widget_day_item_192,
            194 to R.layout.widget_day_item_194,
            196 to R.layout.widget_day_item_196,
            198 to R.layout.widget_day_item_198,
            200 to R.layout.widget_day_item_200,
        )
    }
}
