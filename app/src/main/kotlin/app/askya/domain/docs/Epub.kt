package app.askya.domain.docs

/**
 * Книга из epub.
 *
 * epub — это zip, внутри которого html-страницы и опись: `container.xml`
 * говорит, где лежит опись (`.opf`), опись — какие страницы в книге есть и в
 * каком порядке их читать, а оглавление (`.ncx` у второй версии формата,
 * `nav.xhtml` у третьей) — как называются главы.
 *
 * Читается всё это своими руками, без библиотеки: чужая читалка epub — это
 * несколько мегабайт и своя вёрстка поверх нашей страницы, а нужно из книги
 * ровно то же, что и из всякого документа в Scroll, — текст по главам.
 *
 * Порядок глав берётся из `spine`, а не из оглавления: оглавление
 * необязательно и часто неполно, а `spine` — это и есть книга по порядку.
 * Названия глав, наоборот, берутся из оглавления и только если его нет — из
 * первого заголовка страницы, а совсем ни при чём — «Глава N».
 *
 * Пустые страницы (обложка, титул, вставка со шрифтами) в главы не попадают:
 * листать книгу через три пустых экрана человек не должен.
 */
internal fun parseEpub(parts: Map<String, ByteArray>): BookText? {
    val container = parts.entryIgnoringCase("META-INF/container.xml")?.asMarkup()
    // Опись ищется по описи, а если её нет — просто по расширению: битый
    // `container.xml` не повод не открыть книгу.
    val opfPath = container?.let { attributeOf(it, "rootfile", "full-path") }
        ?: parts.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
        ?: return null
    val opf = parts.entryIgnoringCase(opfPath)?.asMarkup() ?: return null

    val description = parseOpf(opf)
    if (description.spine.isEmpty()) return null

    // Оглавление: сперва третья версия формата (страница с `nav`), потом
    // вторая (`toc.ncx`). У книг, собранных для обеих, они одинаковы.
    val navPath = description.navHref?.let { resolveEntry(opfPath, it) }
    val ncxPath = description.ncxHref?.let { resolveEntry(opfPath, it) }
        ?: parts.keys.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }

    val titles = HashMap<String, String>()
    ncxPath?.let { path ->
        parts.entryIgnoringCase(path)?.asMarkup()?.let { titles += parseNcx(it, path) }
    }
    navPath?.let { path ->
        parts.entryIgnoringCase(path)?.asMarkup()?.let { nav ->
            // Оглавление третьей версии дописывается поверх: там названия
            // обычно точнее, а пропущенное второй версией остаётся от неё.
            titles += parseNav(nav, path)
        }
    }

    val chapters = ArrayList<BookChapter>()
    description.spine.forEach { href ->
        val path = resolveEntry(opfPath, href)
        val page = parts.entryIgnoringCase(path) ?: return@forEach
        val blocks = htmlBlocks(page.asMarkup())
        if (blocks.none { it is BookBlock.Paragraph }) return@forEach

        val title = titles[path]
            ?: firstHeading(blocks)
            ?: "Глава ${chapters.size + 1}"
        chapters += BookChapter(title = title.normalizeSpaces(), blocks = blocks)
    }

    if (chapters.isEmpty()) return null

    return BookText(
        title = description.title.ifBlank { "Книга" },
        author = description.author,
        chapters = chapters,
    )
}

/** Что сказано в описи книги. */
private class OpfDescription(
    val title: String,
    val author: String,
    /** Страницы в порядке чтения. */
    val spine: List<String>,
    val ncxHref: String?,
    val navHref: String?,
)

/**
 * Разбор описи.
 *
 * Порядок чтения задан ссылками на записи описи, а не путями: сперва
 * собирается «номер записи → файл», потом по нему разворачивается `spine`.
 */
private fun parseOpf(source: String): OpfDescription {
    val items = HashMap<String, String>()
    var navId: String? = null
    var ncxId: String? = null
    val spineIds = ArrayList<String>()
    var title = ""
    var author = ""
    var reading: String? = null
    val text = StringBuilder()

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    "item" -> {
                        val id = attributes["id"] ?: return
                        val href = attributes["href"] ?: return
                        items[id] = href
                        if (attributes["properties"].orEmpty().contains("nav")) navId = id
                        if (attributes["media-type"] == "application/x-dtbncx+xml") ncxId = id
                    }
                    "spine" -> attributes["toc"]?.let { if (ncxId == null) ncxId = it }
                    "itemref" -> attributes["idref"]?.let { spineIds += it }
                    "dc:title", "dc:creator" -> {
                        reading = name
                        text.setLength(0)
                    }
                }
            }

            override fun close(name: String) {
                if (name != reading) return
                val value = text.toString().normalizeSpaces()
                when (name) {
                    "dc:title" -> if (title.isEmpty()) title = value
                    "dc:creator" -> if (author.isEmpty()) author = value
                }
                reading = null
                text.setLength(0)
            }

            override fun text(value: String) {
                if (reading != null) text.append(value)
            }
        },
    )

    return OpfDescription(
        title = title,
        author = author,
        spine = spineIds.mapNotNull { items[it] },
        ncxHref = ncxId?.let { items[it] },
        navHref = navId?.let { items[it] },
    )
}

/**
 * Оглавление второй версии формата: `navPoint` с подписью и ссылкой.
 *
 * Ссылка приходит вместе с якорем внутри страницы («chapter1.xhtml#part2») —
 * якорь отбрасывается: Askya показывает страницу целиком, и разрезать её по
 * якорям значило бы собирать вёрстку заново.
 */
private fun parseNcx(source: String, path: String): Map<String, String> {
    val titles = HashMap<String, String>()
    var label = StringBuilder()
    var reading = false

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                when (name) {
                    "navlabel" -> {
                        reading = true
                        label = StringBuilder()
                    }
                    "content" -> {
                        val src = attributes["src"] ?: return
                        val target = resolveEntry(path, src)
                        val text = label.toString().normalizeSpaces()
                        if (text.isNotEmpty()) titles.putIfAbsent(target, text)
                    }
                }
            }

            override fun close(name: String) {
                if (name == "navlabel") reading = false
            }

            override fun text(value: String) {
                if (reading) label.append(value)
            }
        },
    )

    return titles
}

/** Оглавление третьей версии: обычные ссылки на странице `nav`. */
private fun parseNav(source: String, path: String): Map<String, String> {
    val titles = HashMap<String, String>()
    var href: String? = null
    val label = StringBuilder()

    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                if (name != "a") return
                href = attributes["href"]
                label.setLength(0)
            }

            override fun close(name: String) {
                if (name != "a") return
                val link = href ?: return
                val text = label.toString().normalizeSpaces()
                if (text.isNotEmpty()) titles[resolveEntry(path, link)] = text
                href = null
                label.setLength(0)
            }

            override fun text(value: String) {
                if (href != null) label.append(value)
            }
        },
    )

    return titles
}

/** Одно свойство одного тега — когда ради него разбирать весь файл незачем. */
private fun attributeOf(source: String, tag: String, attribute: String): String? {
    var found: String? = null
    scanMarkup(
        source,
        object : MarkupSink {
            override fun open(name: String, attributes: Map<String, String>) {
                if (name == tag && found == null) found = attributes[attribute]
            }
        },
    )
    return found
}

/**
 * Файл в архиве по имени, не считаясь с регистром.
 *
 * Внутри zip имена чувствительны к регистру, а собирают epub чем попало:
 * `META-INF` встречается и как `meta-inf`, а путь из описи бывает записан не
 * тем регистром, что сам файл.
 */
private fun Map<String, ByteArray>.entryIgnoringCase(path: String): ByteArray? =
    this[path] ?: entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value
