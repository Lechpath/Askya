package app.askya.echo

/**
 * По какому признаку музыка разложена: исполнитель, альбом или жанр.
 *
 * Папки и плейлисты сюда не входят и входить не должны: папка — это место на
 * диске, плейлист — список, собранный руками, и оба уже существуют готовыми.
 * Здесь же три разбора, которых на телефоне нет ни в каком виде: их приходится
 * выводить из подписей файлов, и выводятся они по правилам ([EchoRules]).
 */
enum class EchoShelf(val title: String, val about: String, val nothing: String) {
    ARTIST("Исполнитель", "Кто это играет", "Ни у одной песни не подписан исполнитель"),
    ALBUM("Альбом", "Пластинки целиком", "Ни одна песня не приписана к альбому"),
    GENRE("Жанр", "На что это похоже", "Жанр не проставлен ни у одной песни"),
}

/** Полка: как называется и что на ней лежит. */
data class EchoBundle(
    val name: String,
    val tracks: List<Track>,
)

/**
 * Правила раскладки музыки — то, по чему Echo решает, куда какая песня.
 *
 * ## Зачем правила, а не просто группировка по тегу
 *
 * Разложить музыку по полю `artist` можно в одну строчку, и получится
 * бесполезно. На телефоне, куда песни попадают по одной, из десятка мест, одно
 * и то же имя записано десятком способов: «Би-2», «Би-2 feat. Чичерина»,
 * «би-2», «The Beatles» и «Beatles», «Nirvana», «Nirvana & Дэйв». Разложенные
 * по тегу как есть, они дают пятнадцать полок вместо четырёх, и на каждой по
 * одной песне — то есть список файлов, только длиннее.
 *
 * Поэтому подпись сперва приводится к canonical виду: отбрасывается всё, что
 * идёт после «feat.», «ft.», «при участии» и знака «&»; снимается артикль
 * «The»; выравниваются пробелы и регистр. Дальше — главное: **правила
 * выводятся из самой библиотеки**. Пройдя по всем песням, Echo видит, каким
 * написанием каждое имя встречается чаще, и это написание становится именем
 * полки. Не «правильное по справочнику», а то, которым подписано большинство
 * файлов на этом телефоне.
 *
 * ## И почему новые песни ложатся туда же
 *
 * Правила — это функция, а не список полок. Скачанная сегодня песня проходит
 * через те же [artistOf], [albumOf], [genreOf], что и все прежние, и потому
 * попадает на существующую полку, а не заводит рядом свою, отличающуюся
 * регистром буквы. Если её имя раньше не встречалось, она заводит новую полку —
 * и с этого момента правила знают и его: набор пересобирается при каждом
 * чтении библиотеки, то есть на каждом входе в раздел.
 *
 * ## Жанр — по соседям
 *
 * Жанр не проставлен у большинства скачанных файлов, и полка «Без жанра» из
 * трёхсот песен бессмысленна. Поэтому пустой жанр не остаётся пустым: он
 * берётся у того, чей он наверняка тот же, — у остальных песен того же
 * исполнителя, а если и там пусто, то у того же альбома. Это единственное
 * место, где Askya что-то домысливает за файл, и домысливает она ровно то, что
 * человек и сам бы сказал: песни одного исполнителя — одного жанра.
 */
class EchoRules private constructor(
    /**
     * Имена, встречающиеся в библиотеке сами по себе, без запятой.
     *
     * По ним разбираются совместные подписи: «3 Doors Down, Bob Seger» — это
     * 3 Doors Down, если у них на телефоне есть и свои полсотни песен. А в
     * «Bach, Johann Sebastian» запятая часть имени, и разделять по ней нельзя —
     * потому что «Bach» отдельным исполнителем в библиотеке не встречается.
     *
     * Это и есть то, ради чего правила выводятся из самой музыки: одна и та же
     * запятая значит разное, и решить, что она значит здесь, можно только
     * посмотрев, что ещё лежит на этом телефоне.
     */
    private val solo: Set<String>,
    /** Приведённое имя исполнителя — то написание, которым он подписан чаще. */
    private val artists: Map<String, String>,
    /** Приведённое имя альбома — тем же порядком. */
    private val albums: Map<String, String>,
    /** Жанр по приведённому имени исполнителя — тот, что встречается чаще. */
    private val genresOfArtist: Map<String, String>,
    /** Жанр по приведённому имени альбома. */
    private val genresOfAlbum: Map<String, String>,
) {

    /** Кто играет: имя с полки, а не то, что записано в теге этой песни. */
    fun artistOf(track: Track): String {
        val name = shorten(strip(track.artist))
        if (name.isEmpty()) return UNKNOWN_ARTIST
        return artists[fold(name)] ?: name
    }

    /**
     * Отбросить соавторов, перечисленных через запятую, — но только тех, после
     * кого остаётся имя, знакомое библиотеке. См. [solo].
     */
    private fun shorten(name: String): String {
        val at = name.indexOf(',')
        if (at <= 0) return name
        val head = name.substring(0, at).trim()
        return if (fold(head) in solo) head else name
    }

    /** Какая пластинка. Песни без альбома собираются в одну общую полку. */
    fun albumOf(track: Track): String {
        val key = fold(track.album)
        if (key.isEmpty()) return SINGLES
        return albums[key] ?: track.album.trim()
    }

    /** Какой жанр — из тега, а нет тега, так у соседей. */
    fun genreOf(track: Track): String {
        val own = track.genre.trim()
        if (own.isNotEmpty()) return own
        genresOfArtist[fold(shorten(strip(track.artist)))]?.let { return it }
        genresOfAlbum[fold(track.album)]?.let { return it }
        return UNKNOWN_GENRE
    }

    /**
     * Музыка, разложенная по выбранному признаку.
     *
     * Полки — по алфавиту, но «Неизвестный исполнитель», «Одиночные записи» и
     * «Без жанра» уходят в конец при любой букве: это не имена, а признание,
     * что имени нет, и стоять им среди имён не место.
     *
     * Внутри полки — тот же порядок, в каком музыка пришла из системы, то есть
     * по названию. Для альбома это неверно — пластинку слушают по номерам
     * дорожек, — но номера в MediaStore есть не у всех файлов, и порядок,
     * который у половины альбома верный, а у половины случайный, хуже
     * честного алфавита.
     */
    fun shelve(tracks: List<Track>, shelf: EchoShelf): List<EchoBundle> = tracks
        .groupBy { track ->
            when (shelf) {
                EchoShelf.ARTIST -> artistOf(track)
                EchoShelf.ALBUM -> albumOf(track)
                EchoShelf.GENRE -> genreOf(track)
            }
        }
        .map { (name, inside) -> EchoBundle(name, inside) }
        .sortedWith(
            compareBy<EchoBundle> { it.name in NAMELESS }.thenBy { it.name.lowercase() },
        )

    companion object {

        /** Полка для тех, у кого не подписан исполнитель. */
        const val UNKNOWN_ARTIST = "Неизвестный исполнитель"

        /** Песни, не приписанные ни к какой пластинке. */
        const val SINGLES = "Одиночные записи"

        /** И те, у кого жанра не нашлось даже у соседей. */
        const val UNKNOWN_GENRE = "Без жанра"

        private val NAMELESS = setOf(UNKNOWN_ARTIST, SINGLES, UNKNOWN_GENRE)

        /**
         * Вывести правила из того, что лежит на телефоне.
         *
         * Два прохода по библиотеке. Первый собирает имена, встречающиеся без
         * запятой, — без них не разобрать совместные подписи ([solo]). Второй
         * набирает, каким написанием каждое имя встречается чаще и какой жанр
         * чаще стоит у каждого исполнителя и каждого альбома.
         *
         * Дальше правила отвечают на вопросы о любой песне — и о той, которой
         * в этих проходах не было.
         */
        fun of(tracks: List<Track>): EchoRules {
            val artistSpellings = mutableMapOf<String, MutableMap<String, Int>>()
            val albumSpellings = mutableMapOf<String, MutableMap<String, Int>>()
            val artistGenres = mutableMapOf<String, MutableMap<String, Int>>()
            val albumGenres = mutableMapOf<String, MutableMap<String, Int>>()

            // Первый проход — только затем, чтобы узнать, кто на этом телефоне
            // встречается сам по себе. Без этого списка совместные подписи не
            // разобрать: см. [solo].
            val solo = tracks
                .map { strip(it.artist) }
                .filter { it.isNotEmpty() && !it.contains(',') }
                .map { fold(it) }
                .toSet()

            for (track in tracks) {
                val artist = shorten(strip(track.artist), solo)
                val artistKey = fold(artist)
                val album = track.album.trim()
                val albumKey = fold(album)
                val genre = track.genre.trim()

                if (artistKey.isNotEmpty()) {
                    artistSpellings.getOrPut(artistKey) { mutableMapOf() }.count(artist)
                    if (genre.isNotEmpty()) {
                        artistGenres.getOrPut(artistKey) { mutableMapOf() }.count(genre)
                    }
                }
                if (albumKey.isNotEmpty()) {
                    albumSpellings.getOrPut(albumKey) { mutableMapOf() }.count(album)
                    if (genre.isNotEmpty()) {
                        albumGenres.getOrPut(albumKey) { mutableMapOf() }.count(genre)
                    }
                }
            }

            return EchoRules(
                solo = solo,
                artists = artistSpellings.mapValues { it.value.commonest() },
                albums = albumSpellings.mapValues { it.value.commonest() },
                genresOfArtist = artistGenres.mapValues { it.value.commonest() },
                genresOfAlbum = albumGenres.mapValues { it.value.commonest() },
            )
        }

        /**
         * Отбросить приглашённых: «Би-2 feat. Чичерина» — это Би-2.
         *
         * Соавторство не теряется — оно осталось в подписи самой песни, а
         * полка — про того, чью пластинку человек ищет. Иначе каждый дуэт
         * заводит себе отдельную полку с единственной песней.
         *
         * Знак «&» разбирается тем же правилом. Запятая — не здесь, а в
         * [shorten]: в «Земфира, Rammstein» она разделяет двоих, а в «Bach,
         * Johann Sebastian» она часть имени, и различить одно от другого можно
         * только по тому, что ещё лежит на телефоне.
         */
        private fun strip(raw: String): String {
            var name = raw.trim()
            if (name.equals("<unknown>", ignoreCase = true)) return ""
            for (mark in FEATURING) {
                val at = name.indexOf(mark, ignoreCase = true)
                if (at > 0) name = name.substring(0, at)
            }
            return name.trim().trimEnd(',', '-', '—').trim()
        }

        /**
         * Ключ, по которому два написания считаются одним именем: без регистра,
         * без артикля, без сдвоенных пробелов и без «ё» против «е».
         */
        /** То же отбрасывание соавторов, но со списком, который ещё собирается. */
        private fun shorten(name: String, solo: Set<String>): String {
            val at = name.indexOf(',')
            if (at <= 0) return name
            val head = name.substring(0, at).trim()
            return if (fold(head) in solo) head else name
        }

        private fun fold(raw: String): String {
            var name = raw.trim().lowercase().replace('ё', 'е')
            for (article in ARTICLES) {
                if (name.startsWith(article)) name = name.removePrefix(article)
            }
            return name.replace(SPACES, " ").trim()
        }

        private fun MutableMap<String, Int>.count(value: String) {
            this[value] = (this[value] ?: 0) + 1
        }

        /**
         * Самое частое написание. При ничьей — то, что длиннее: из «Би-2» и
         * «Би-2 (Bi-2)» полное имя опознаётся глазом надёжнее сокращённого.
         */
        private fun Map<String, Int>.commonest(): String =
            entries.maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length },
            )?.key.orEmpty()

        private val FEATURING = listOf(
            " feat.", " feat ", " ft.", " ft ", " featuring ",
            " при участии ", " с участием ", " & ", " и др.",
        )

        private val ARTICLES = listOf("the ", "a ")

        private val SPACES = Regex("\\s+")
    }
}
