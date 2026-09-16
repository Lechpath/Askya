package app.askya.data.sync

import app.askya.domain.model.LinkKind

/**
 * Что известно о каждой таблице, которая едет в облако.
 *
 * ## Зачем это отдельно от [SyncSchema]
 *
 * [SyncSchema] отвечает на вопрос «за какими таблицами следить», и ему хватает
 * списка имён. Здесь — то, без чего строку нельзя увезти на другое устройство и
 * положить обратно: чем в ней ключ, какие колонки ведут на другие строки и что
 * делать со спорной версией.
 *
 * ## Главное: номера строк не едут никуда
 *
 * `id` раздаёт счётчик таблицы, и на двух устройствах седьмым делом окажутся
 * разные дела. Поэтому в порцию не кладётся ни сам `id`, ни колонки, которые на
 * него ссылаются: строка списка внутри дела едет не с `deedId = 42`, а с именем
 * того дела ([refs]), и на другом устройстве имя превращается в тамошний номер.
 * Увези мы номер как есть — строка встала бы под чужое дело, и это было бы
 * не «не синхронизировалось», а «перепуталось».
 *
 * Привязка дела ([link]) — тот же номер, только внутри строки: `note:87`.
 * Разбирается и собирается так же.
 *
 * ## Порядок
 *
 * Список идёт родителями вперёд: книга раньше записи, дело раньше его списка.
 * Так у ссылки почти всегда уже есть на что указывать, когда доходит очередь до
 * ссылающегося.
 */
object SyncTables {

    data class Table(
        val name: String,
        /** Колонка ключа. */
        val key: String = "id",
        /**
         * Ключ раздаёт сама база. Такой ключ в порцию не кладётся вовсе — на
         * другом устройстве он будет свой. У `generated_days` ключ природный
         * (дата) и потому едет.
         */
        val born: Boolean = true,
        /** Колонка → таблица, на строку которой она ведёт. */
        val refs: Map<String, String> = emptyMap(),
        /** Ссылки, без которых строки не бывает: не нашлась — строку не кладём. */
        val musts: Set<String> = emptySet(),
        /** Колонка «вид:адрес» — привязка дела. */
        val link: String? = null,
        /** Заголовок: им подписывается спорная копия. */
        val title: String? = null,
        /**
         * Спорную строку не решать поздней правкой, а оставить обе версии.
         * Только у записей Scroll: их пишут долго и с двух сторон, и потерять
         * абзац здесь — потерять написанное. У дела или строки счёта терять
         * нечего: там одна строчка, и поздняя правка и есть верная.
         */
        val keepBoth: Boolean = false,
    )

    /** Вид привязки → куда она ведёт. Мост не едет в облако вовсе — см. ниже. */
    val LINKED: Map<String, String> = mapOf(
        LinkKind.BOOK.key to "scroll_topics",
        LinkKind.NOTE.key to "notes",
        LinkKind.YET.key to "yet_lists",
    )

    val ALL: List<Table> = listOf(
        Table("scroll_topics", title = "title"),
        Table("image_albums", title = "title"),
        Table("yet_lists", title = "title"),
        Table("ledger_accounts", title = "title"),
        Table("ledger_categories", title = "title"),
        Table(
            "notes",
            refs = mapOf("topicId" to "scroll_topics", "albumId" to "image_albums"),
            title = "title",
            keepBoth = true,
        ),
        Table("schedule_items", link = "link", title = "title"),
        Table("routine_items", link = "link", title = "title"),
        Table("generated_days", key = "date", born = false),
        Table(
            "deed_tasks",
            refs = mapOf("deedId" to "schedule_items"),
            musts = setOf("deedId"),
            title = "text",
        ),
        Table(
            "yet_items",
            refs = mapOf("listId" to "yet_lists"),
            musts = setOf("listId"),
            title = "text",
        ),
        Table("reminders", refs = mapOf("itemId" to "schedule_items"), title = "title"),
        Table(
            "ledger_entries",
            refs = mapOf(
                "accountId" to "ledger_accounts",
                "toAccountId" to "ledger_accounts",
                "categoryId" to "ledger_categories",
            ),
            musts = setOf("accountId"),
            title = "note",
        ),
    )

    fun of(name: String): Table? = ALL.firstOrNull { it.name == name }
}
