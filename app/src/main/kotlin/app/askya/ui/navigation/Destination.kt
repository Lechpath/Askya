package app.askya.ui.navigation

import androidx.annotation.DrawableRes
import app.askya.R

/**
 * Разделы бокового меню.
 *
 * Напоминаний здесь нет намеренно: на них ведёт один вход — колокольчик в
 * шапке AskyaDay, там же, где день и смотрят. Настроек — тоже: они не место, а
 * ящик с инструментами, и стоят кнопкой в нижнем ряду меню ([Routes.SETTINGS]).
 */
enum class Destination(
    val route: String,
    val label: String,
    @DrawableRes val icon: Int,
) {
    // Порядок перечисления — это и порядок в меню.
    //
    // Маршруты внутренние и остались прежними: переименование сломало бы
    // сохранённое состояние навигации без всякой пользы. Поэтому у Scroll
    // маршрут `notes`, а у плеера — `practices`: на их местах когда-то были
    // «Заметки» и «Практики».
    //
    // Знаки нарисованы свои (`res/drawable/ic_menu_*`), а не взяты из набора
    // Material: раздел с именем Scroll должен обозначаться свитком, а не
    // листом с загнутым углом, который есть в каждом приложении.
    TODAY("today", "AskyaDay", R.drawable.ic_menu_day),
    NOTES("notes", "Scroll", R.drawable.ic_scroll),
    ECHO("practices", "AskyaEcho", R.drawable.ic_menu_echo),

    // Маршрут `video` — новый раздел, и переименовывать в нём нечего: он
    // заводится сразу под своим именем, в отличие от старших соседей.
    VIDEO("video", "AskyaV", R.drawable.ic_menu_video),

    /**
     * Ledger — расходная книга: счета, статьи и записи о деньгах.
     *
     * Разделом, а не подразделом Scroll, хотя книга и там книга: в Scroll лежит
     * написанное словами, а здесь — считанное числами, и общего у них только
     * слово «запись». Подразделом Ledger стал бы местом, куда ходят через
     * чужое оглавление, а ходят в него по нескольку раз на дню — чаще, чем
     * в иной раздел меню.
     *
     * Последним в ряду: деньги записывают часто, но открывают приложение не
     * ради них, а ради дня.
     */
    LEDGER("ledger", "Ledger", R.drawable.ic_menu_ledger),

    /**
     * Threads — нити: личные начинания, которые тянутся неделями.
     *
     * Шестым и последним: нить не открывают по нескольку раз на дню, как день
     * или книгу, — в неё заходят раз в несколько дней спросить, как идёт. И
     * стоит она после Ledger нарочно: смета нити считается из книги, и раздел,
     * поставленный перед той, откуда берёт числа, читался бы главнее её.
     */
    THREADS("threads", "Threads", R.drawable.ic_menu_threads),
}

/** Маршруты экранов, которых нет в меню. */
object Routes {
    const val NOTE_EDIT = "note/{noteId}"

    /**
     * Настройки. Маршрут остался прежним — он внутренний, а сам вход переехал
     * из списка разделов в нижний ряд меню.
     */
    const val SETTINGS = "settings"

    const val REMINDERS = "reminders"

    /**
     * «Мосты» — чем дела дня делаются за пределами Askya. Своя страница, а не
     * кучка в настройках: у моста три вещи, их правят, и мостов бывает
     * с десяток. Вход — строкой в настройках; своего раздела в меню они не
     * заводят.
     */
    const val BRIDGES = "bridges"

    /**
     * «Прожитое» — счёт по выбранным делам. Внутри AskyaDay: вход из календаря,
     * там же, где смотрят на прошлое. Своего раздела в меню оно не заводит.
     */
    const val LIVED = "lived"

    /**
     * Погода. В меню разделом не стоит: на неё ведёт сама строка погоды в
     * шапке меню — там, где на неё и смотрят. Второй вход в то же место
     * заставлял бы спрашивать «а это те же самые?».
     */
    const val WEATHER = "weather"

    /** Списки Yet — подраздел Scroll. */
    const val LISTS = "scroll/lists"

    /**
     * «Голос» — подраздел Scroll: наговорённые заметки. Своего раздела в меню
     * они не заводят: заметка голосом — такая же запись, и лежит она там же,
     * где написанное.
     */
    const val VOICE = "scroll/voice"

    /**
     * Карта одной нити — замысел, разложенный узлами.
     *
     * Своим экраном, а не карточкой поверх ленты: в карте двигают пальцем,
     * приближают и подолгу сидят, а карточка Askya — это отступление в
     * сторону на полминуты. Раскрытая карточка у нити тоже осталась, но
     * открывается она уже из карты и отвечает на другой вопрос — «как идёт».
     */
    const val THREAD = "thread/{threadId}"

    fun thread(id: Long) = "thread/$id"

    /** Список Yet, открытый во весь экран. */
    const val YET_LIST = "yet/{listId}"

    fun yetList(id: Long) = "yet/$id"

    /** Разделы Scroll. */
    const val IMAGES = "scroll/images"

    /**
     * «Библиотека» — книги и файлы на одной полке. Прежние `scroll/files` и
     * `scroll/topics` объединены в неё; отдельная книга открывается [TOPIC].
     */
    const val LIBRARY = "scroll/library"

    const val TOPIC = "scroll/topic/{topicId}"

    fun topic(id: Long) = "scroll/topic/$id"

    /** Альбом «Изображений» — своя папка раздела, к книгам отношения не имеет. */
    const val ALBUM = "scroll/album/{albumId}"

    fun album(id: Long) = "scroll/album/$id"

    /** Карточка картинки: подпись и альбом сразу после загрузки. */
    const val IMAGE_CARD = "scroll/image-card/{noteId}"

    fun imageCard(id: Long) = "scroll/image-card/$id"

    /** Правка картинки и склейка коллажа — оба живут в «Изображениях». */
    const val IMAGE_EDIT = "scroll/image/{noteId}"

    fun imageEdit(id: Long) = "scroll/image/$id"

    const val COLLAGE = "scroll/collage"

    /**
     * Просмотр картинки с листанием соседних. [albumId] = [ALL] означает «все
     * картинки раздела»: листают тот же срез, что был в сетке под пальцем.
     */
    const val GALLERY = "scroll/gallery/{noteId}/{albumId}"

    fun gallery(noteId: Long, albumId: Long?) = "scroll/gallery/$noteId/${albumId ?: ALL}"

    /** «Не альбом, а весь раздел» — в маршруте номером быть нечему. */
    const val ALL = -1L

    /** Просмотр приложенного файла внутри Askya. */
    const val VIEW = "scroll/view/{noteId}"

    fun view(id: Long) = "scroll/view/$id"

    /** Список дел — из AskyaDay. Маршрут остался прежним, он внутренний. */
    const val TASKS = "about/tasks"

    /** [id] = [NEW] означает создание новой записи. */
    const val NEW = -1L

    fun noteEdit(id: Long) = "note/$id"

}
