package app.askya.domain.markdown

/**
 * Ссылки внутри строки: где они начинаются и куда на самом деле ведут.
 *
 * Разбор здесь, а не в виде: адрес в записи — такая же часть разметки, как
 * жирное и списки, и проверять его правильность удобнее без экрана.
 *
 * Ссылка попадает в запись двумя способами. Подписанной — `[слово](адрес)`,
 * так её кладёт окно «Ссылка». И голой — человек скопировал адрес в браузере и
 * вставил в текст; это самый частый способ, и до сих пор такой адрес оставался
 * просто буквами: он не был подчёркнут и никуда не вёл.
 */
object Links {
    /**
     * Адрес, начинающийся ровно в этом месте строки, — или `null`.
     *
     * Ищется от места, а не по всей строке разом: разбор разметки идёт по
     * символам, и спрашивать «а не отсюда ли начинается ссылка» дешевле, чем
     * держать список найденных заранее и сверяться с ним на каждом шаге.
     *
     * Внутри слова адрес не начинается: `почта@askya.www.тут` — не ссылка, и
     * подчёркивать её хвост нельзя.
     */
    fun at(text: String, index: Int): String? {
        if (index < 0 || index >= text.length) return null
        if (index > 0 && (text[index - 1].isLetterOrDigit() || text[index - 1] == '@')) return null

        val match = START.matchAt(text, index) ?: return null
        // Точка и скобка в конце — это конец предложения, а не часть адреса.
        return match.value.trimEnd(*TAIL)
            .takeIf { it.isNotEmpty() && !it.endsWith("://") && !it.equals("www.", ignoreCase = true) }
    }

    /**
     * Адрес, готовый для системы.
     *
     * Браузер открывается по схеме: `askya.app` без `https://` для Android —
     * не адрес страницы, а строка, и открывать её нечем. Схему, набранную
     * человеком, ничем не подменяем: `mailto:`, `tel:` и прочее — тоже ссылки,
     * и ведут они не в браузер.
     */
    fun web(raw: String): String {
        val url = raw.trim()
        return when {
            url.isEmpty() -> url
            url.startsWith("www.", ignoreCase = true) -> encoded("https://$url")
            HTTP.containsMatchIn(url) -> encoded(url)
            // Чужая схема — чужие правила: `mailto:` с русским именем ящика
            // кодировать нельзя, а `tel:` кодировать нечего.
            SCHEME.containsMatchIn(url) -> url
            DOMAIN.containsMatchIn(url) -> encoded("https://$url")
            else -> url
        }
    }

    /**
     * Всё, что вне ASCII, — процентами.
     *
     * Адрес с русскими буквами в пути — обычное дело: так их пишет jw.org и
     * почти всякий сайт с локализованными разделами, и так он копируется из
     * браузера на телефоне. Но в запросе такой адрес должен ехать закодированным
     * — сервер (точнее, стоящая перед ним раздача) на сырые байты в пути
     * отвечает отказом, и человек видит 403 на ссылке, которая в браузере
     * открывается.
     *
     * Кодируется побайтово в UTF-8 — так этого и ждёт `URI`. Знаки разметки
     * адреса (`:/?#&=%`) в ASCII и остаются собой: закодировать их значило бы
     * сломать сам адрес, а `%` не трогается ещё и затем, чтобы уже
     * закодированная ссылка не закодировалась второй раз.
     */
    private fun encoded(url: String): String {
        if (url.all { it.code in PRINTABLE }) return url

        val out = StringBuilder(url.length)
        for (char in url) {
            if (char.code in PRINTABLE) {
                out.append(char)
                continue
            }
            for (byte in char.toString().toByteArray(Charsets.UTF_8)) {
                out.append('%').append(HEX[(byte.toInt() shr 4) and 0xF]).append(HEX[byte.toInt() and 0xF])
            }
        }
        return out.toString()
    }

    /** Адрес с самого начала: со схемой или с привычного «www.». */
    private val START = Regex("""(https?://|www\.)[^\s<>"'`\[\]()]+""", RegexOption.IGNORE_CASE)

    /** Страница в сети — только у неё адрес кодируется процентами. */
    private val HTTP = Regex("""^https?://""", RegexOption.IGNORE_CASE)

    /** Схема — то, что стоит до двоеточия: `https:`, `mailto:`, `tel:`. */
    private val SCHEME = Regex("""^[a-zA-Z][a-zA-Z0-9+.\-]*:""")

    /** Домен без схемы: точка, зона из букв и дальше либо косая, либо конец. */
    private val DOMAIN = Regex("""^[^\s/@]+\.[a-zA-Z]{2,}([/?#]|$)""")

    /** Печатный ASCII без пробела: всё остальное в адресе едет процентами. */
    private val PRINTABLE = 0x21..0x7E

    private const val HEX = "0123456789ABCDEF"

    private val TAIL = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '»', '"', '\'', '—')
}
