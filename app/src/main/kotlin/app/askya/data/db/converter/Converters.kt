package app.askya.data.db.converter

import androidx.room.TypeConverter
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.BookColor
import app.askya.domain.model.ListMark
import app.askya.domain.model.Priority
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Даты и время хранятся ISO-строками: они сортируются лексикографически так же,
 * как хронологически, поэтому ORDER BY по колонке работает без ухищрений.
 */
class Converters {

    @TypeConverter
    fun dateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun timeToString(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun stringToTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun dateTimeToString(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun stringToDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    /**
     * Важность хранится именем константы, а не порядковым номером:
     * перестановка или вставка значения тогда не портит уже записанные строки.
     * Неизвестное имя (запись из будущей версии) сводится к нейтральному
     * значению вместо падения.
     */
    @TypeConverter
    fun priorityToString(value: Priority): String = value.name

    @TypeConverter
    fun stringToPriority(value: String): Priority =
        Priority.entries.firstOrNull { it.name == value } ?: Priority.NORMAL

    /**
     * Знак хранится именем — по той же причине, что и важность. Пусто значит
     * «не выбирали», и знак выводится из названия; неизвестное имя приравнено
     * к пустому, а не роняет чтение.
     */
    @TypeConverter
    fun iconToString(value: BlockIcon?): String? = value?.name

    @TypeConverter
    fun stringToIcon(value: String?): BlockIcon? =
        value?.let { name -> BlockIcon.entries.firstOrNull { it.name == name } }

    /**
     * Цвет корешка книги — тоже именем. Пусто значит «не выбирали», и цвет
     * выводится из названия; неизвестное имя приравнено к пустому.
     */
    @TypeConverter
    fun bookColorToString(value: BookColor?): String? = value?.name

    @TypeConverter
    fun stringToBookColor(value: String?): BookColor? =
        value?.let { name -> BookColor.entries.firstOrNull { it.name == name } }

    /**
     * Знак строки списка — тоже именем. Неизвестное имя сводится к квадрату:
     * список, заведённый в будущей версии, откроется отмеченным по-другому,
     * но откроется.
     */
    @TypeConverter
    fun listMarkToString(value: ListMark): String = value.name

    @TypeConverter
    fun stringToListMark(value: String): ListMark =
        ListMark.entries.firstOrNull { it.name == value } ?: ListMark.SQUARE

    /**
     * Теги склеиваются переводом строки: в отличие от запятой он не встречается
     * внутри тега, поэтому разбор обратно однозначен без экранирования.
     */
    @TypeConverter
    fun tagsToString(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun stringToTags(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split("\n").filter { it.isNotBlank() }
}
