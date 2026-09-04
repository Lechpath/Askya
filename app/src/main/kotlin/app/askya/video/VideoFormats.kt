package app.askya.video

import org.videolan.libvlc.util.Extensions

/**
 * Какие файлы AskyaV берётся открыть.
 *
 * Список не переписан руками, а взят у самого VLC — `Extensions.VIDEO` и
 * `Extensions.SUBTITLES` лежат в той же библиотеке, которая эти файлы и
 * разбирает. Свой список тут был бы враньём в обе стороны: в нём не хватало бы
 * форматов, которые плеер открывает, и стояли бы те, которые он не открывает,
 * — а обещание «как в VLC» проверяется ровно на том файле, который человек
 * принёс.
 *
 * Расширения в наборе лежат с точкой («.mkv»), как их отдаёт VLC.
 *
 * Тип от системного провайдера не спрашивается: у всего, кроме mp4, он
 * `application/octet-stream`. Имя врёт реже — то же правило, что в разборе
 * документов (`domain/docs/Documents.kt`).
 */
object VideoFormats {

    /** Видеофайлы — те же, что открывает VLC. */
    val video: Set<String> get() = Extensions.VIDEO

    /** Субтитры отдельным файлом: их подкладывают к фильму рядом. */
    val subtitles: Set<String> get() = Extensions.SUBTITLES

    /** Видео ли это — по имени файла. */
    fun isVideo(name: String): Boolean = extensionOf(name) in Extensions.VIDEO

    /** Субтитры ли это — по имени файла. */
    fun isSubtitle(name: String): Boolean = extensionOf(name) in Extensions.SUBTITLES

    /**
     * Что показывать в системном выборе файла.
     *
     * Одного «video» со звёздочкой мало: провайдеры отдают mkv, avi и ts как
     * `application/octet-stream`, и с ним одним половина коллекции в выборе
     * оказалась бы серой. Поэтому берётся и то, и другое, а лишнее
     * отсеивается по имени уже после выбора.
     *
     * (Звёздочка тут написана словом не из кокетства: `/` со звёздочкой внутри
     * комментария Kotlin считает началом вложенного комментария, и файл
     * перестаёт собираться.)
     */
    val pickTypes: Array<String> = arrayOf("video/*", "application/octet-stream")

    /** Что показывать в выборе субтитров: их тип провайдеры зовут как попало. */
    val subtitlePickTypes: Array<String> = arrayOf("text/*", "application/octet-stream")

    /** Расширение с точкой и в нижнем регистре; пустая строка — его нет. */
    private fun extensionOf(name: String): String {
        val clean = name.substringBefore('?').substringBefore('#').trimEnd('/')
        val dot = clean.lastIndexOf('.')
        if (dot < 0 || dot == clean.length - 1) return ""
        return clean.substring(dot).lowercase()
    }
}
