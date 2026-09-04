package app.askya.data.db.converter

import androidx.room.TypeConverter
import app.askya.domain.model.AccountKind
import app.askya.domain.model.BlockIcon
import app.askya.domain.model.MarkColor
import app.askya.domain.model.EntryKind
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
     * Краска-метка — тоже именем: ею покрашены и корешок книги, и счёт в
     * Ledger. Пусто значит «не выбирали», и краска выводится из названия;
     * неизвестное имя приравнено к пустому.
     */
    @TypeConverter
    fun markColorToString(value: MarkColor?): String? = value?.name

    @TypeConverter
    fun stringToMarkColor(value: String?): MarkColor? =
        value?.let { name -> MarkColor.entries.firstOrNull { it.name == name } }

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

    /**
     * Что записано в книге и что за счёт — тоже именами, по тому же правилу.
     *
     * Имя здесь важнее обычного: `kind` разбирается прямо в SQL, где считаются
     * остатки счетов (`LedgerDao.observeDeltas`), — по строке 'EARN', а не по
     * номеру. Порядковый номер в перечислении сдвинулся бы от вставки нового
     * значения, и запрос молча начал бы считать доходы расходами.
     */
    @TypeConverter
    fun entryKindToString(value: EntryKind): String = value.name

    @TypeConverter
    fun stringToEntryKind(value: String): EntryKind =
        EntryKind.entries.firstOrNull { it.name == value } ?: EntryKind.SPEND

    @TypeConverter
    fun accountKindToString(value: AccountKind): String = value.name

    @TypeConverter
    fun stringToAccountKind(value: String): AccountKind =
        AccountKind.entries.firstOrNull { it.name == value } ?: AccountKind.CARD
}
