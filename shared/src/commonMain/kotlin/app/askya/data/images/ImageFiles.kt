package app.askya.data.images

/**
 * Папка Askya под картинки — то, что о ней знает общий код.
 *
 * На телефоне это `ImageStore`: Библиотека, `Pictures/Askya` или своя папка
 * приложения, смотря что разрешено. На компьютере — папка «Изображения»
 * внутри данных Askya. Ссылки на копии лежат в записях Scroll строкой, и
 * каждая система понимает свои: `content://…` у телефона, `file:/…` у
 * компьютера.
 */
interface ImageFiles {

    /** Где лежат картинки — словами, для окон и подсказок. */
    val folderName: String

    /**
     * Копирует выбранный файл к себе и отдаёт ссылку на копию; `null` — копия
     * не сделана. [source] — ссылка, как её отдала система при выборе.
     */
    suspend fun importFrom(source: String, name: String, mime: String): String?

    /** Переносит старую копию на новое место; `null` — переносить нечего. */
    suspend fun adopt(uri: String?): String?

    /** Переименовывает копию; `null` — не наша или не вышло. */
    suspend fun rename(uri: String?, name: String): String?

    fun mimeOf(uri: String): String

    /**
     * Ссылка, которую сможет прочитать чужое приложение, — для «поделиться».
     * Старую копию по её `file://` телефон наружу не отдаст, и её ведут через
     * свой провайдер; `null` — отдать нечего.
     */
    fun shareLink(uri: String?): String?

    /** Лежит ли файл в папке Askya — чужие файлы Askya не трогает. */
    fun isOurs(uri: String?): Boolean

    /** Убирает копию вместе с записью; чужой файл не трогается. */
    suspend fun delete(uri: String?)
}
