package app.askya.data.sync

/**
 * Порция — всё, что одно устройство успело напридумывать с прошлого раза,
 * одним файлом.
 *
 * ## Почему порциями, а не «состоянием»
 *
 * Выкладывать в облако свою базу целиком — значит каждый раз решать, чья база
 * главнее, и однажды решить неверно. Порция говорит не «вот как у меня», а «вот
 * что у меня изменилось и когда»: такие сообщения складываются в любом порядке
 * и от любого числа устройств.
 *
 * Порции не правят и не перезаписывают: каждая ложится своим файлом с
 * возрастающим номером, и пишет в них только то устройство, чья это папка. При
 * таком порядке облаку не нужны ни замки, ни транзакции — хватает самого
 * глупого хранилища.
 *
 * ## Что внутри строки
 *
 * Имя строки ([Row.uid]), отметка правки ([Row.hlc]) и сами колонки. Убранная
 * строка едет без колонок и с [Row.dead]: «её больше нет» — такое же
 * изменение, как всякое другое, и доехать оно должно так же обязательно, иначе
 * другое устройство вернёт удалённое обратно.
 *
 * ## Номер схемы
 *
 * У порции лежит [schema] — версия базы того, кто её собрал. Устройство со
 * старой сборкой чужую порцию из будущего не применяет и не отмечает
 * прочитанной: там колонки, о которых оно не знает. Обновится — прочтёт.
 */
data class Pack(
    val schema: Int,
    /** Кто собрал: имя папки устройства в облаке. */
    val device: String,
    /** Как это устройство подписывает свою проигравшую копию: «с компьютера». */
    val from: String,
    val made: Long,
    val rows: List<Row>,
) {

    data class Row(
        val table: String,
        val uid: String,
        val hlc: Long,
        /**
         * Отметка версии, от которой эта правка оттолкнулась. Совпала с той,
         * что у принимающего, — правка продолжает его же строку; не совпала —
         * правили порознь, и это спор (см. [SyncEngine]).
         */
        val base: Long = 0,
        val dead: Boolean = false,
        /** Колонки строки; у убранной их нет. */
        val values: Map<String, Any?>? = null,
    )

    /** Порция, как она уезжает: текст, потом gzip. Шифрует уже [SyncEngine]. */
    fun bytes(): ByteArray = Crypt.squeeze(json().toByteArray())

    fun json(): String = Json.write(
        linkedMapOf(
            "pack" to FORMAT,
            "schema" to schema,
            "device" to device,
            "from" to from,
            "made" to made,
            "rows" to rows.map { row ->
                linkedMapOf(
                    "t" to row.table,
                    "u" to row.uid,
                    "h" to row.hlc,
                    "b" to row.base,
                    "d" to if (row.dead) 1 else 0,
                    "v" to row.values,
                )
            },
        ),
    )

    companion object {

        /** Версия самого формата порции — не путать со схемой базы. */
        const val FORMAT = 1

        /** Порции лежат по папкам устройств: `devices/<устройство>/000041.pack`. */
        const val DEVICES = "devices"

        /** Номер последней порции — чтобы не перебирать папку заново. */
        const val HEAD = "head"

        /** Докуда прочитаны чужие порции. Лежит в своей папке: читают все. */
        const val SEEN = "seen"

        fun nameOf(number: Int): String = number.toString().padStart(6, '0') + ".pack"

        fun numberOf(name: String): Int? = name.removeSuffix(".pack").toIntOrNull()

        /**
         * Разобрать. `null` — не порция, порча или формат, которого эта сборка
         * не знает: применять нечего, и падать тоже незачем.
         */
        fun of(bytes: ByteArray): Pack? = runCatching {
            val map = Json.read(String(Crypt.unsqueeze(bytes))) as Map<*, *>
            if ((map["pack"] as? Long)?.toInt() != FORMAT) return null
            Pack(
                schema = (map["schema"] as Long).toInt(),
                device = map["device"] as String,
                from = map["from"] as? String ?: "",
                made = map["made"] as? Long ?: 0L,
                rows = (map["rows"] as List<*>).map { raw ->
                    val row = raw as Map<*, *>
                    @Suppress("UNCHECKED_CAST")
                    Row(
                        table = row["t"] as String,
                        uid = row["u"] as String,
                        hlc = row["h"] as Long,
                        base = row["b"] as? Long ?: 0L,
                        dead = (row["d"] as? Long ?: 0L) != 0L,
                        values = row["v"] as? Map<String, Any?>,
                    )
                },
            )
        }.getOrNull()
    }
}
