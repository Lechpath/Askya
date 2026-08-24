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

    /** Списки Yet — подраздел Scroll. */
    const val LISTS = "scroll/lists"

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
