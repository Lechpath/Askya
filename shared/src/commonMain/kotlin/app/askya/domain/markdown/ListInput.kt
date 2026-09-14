package app.askya.domain.markdown

/** Строка списка, разобранная из написанного: текст, вложенность, отметка. */
data class ListLine(
    val text: String,
    val nested: Boolean = false,
    val done: Boolean = false,
)

/**
 * Разбор написанного в окне записи в строки списка.
 *
 * Список в Askya записывают разметкой: «- хлеб», «1. позвонить», «- [x] уже
 * сделано», два пробела в начале — подпункт. Так его набирают в заметке, так же
 * приходит вставленное из письма или мессенджера, и в подразделе «Списки» это
 * должно значить то же самое, а не оставаться дефисами внутри строки.
 *
 * Разбор построчный, а не через [Markdown.parse]: в окно записи попадает и то,
 * что блочный разбор свернул бы в таблицу, код или черту, — а в списке это всё
 * равно строки, и терять их нельзя. Правила маркеров при этом взяты у
 * [Markdown], чтобы записанное и прочитанное не разъезжались.
 *
 * Одно окно — сколько угодно строк: список вставили целиком, отправили один
 * раз, и он лёг строками, а не одним комком.
 */
object ListInput {

    fun parse(source: String): List<ListLine> = source.lines()
        .mapNotNull(::lineOf)
        .filter { it.text.isNotBlank() }

    private fun lineOf(line: String): ListLine? {
        if (line.isBlank()) return null

        Markdown.TASK.matchEntire(line)?.let { m ->
            return marked(
                text = m.groupValues[3],
                nested = nested(m.groupValues[1]),
                done = m.groupValues[2].lowercase() == "x",
            )
        }
        Markdown.BULLET.matchEntire(line)?.let { m ->
            return marked(text = m.groupValues[2], nested = nested(m.groupValues[1]))
        }
        Markdown.NUMBER.matchEntire(line)?.let { m ->
            return marked(text = m.groupValues[2], nested = nested(m.groupValues[1]))
        }

        // Строка без маркера — тоже строка списка: чаще всего так и пишут,
        // просто перечисляя. Отступ перед ней читается как подпункт.
        return marked(text = line, nested = nested(line.takeWhile { it.isWhitespace() }))
    }

    /**
     * Галочка в конце строки — это отметка, а не буква.
     *
     * Пока строку нельзя было отметить, отмечали знаком: списки, набранные до
     * того, сплошь заканчиваются «✓». Оставить его текстом значило бы показать
     * человеку сделанное как несделанное — рядом с пустым квадратом.
     */
    private fun marked(text: String, nested: Boolean, done: Boolean = false): ListLine {
        val clean = text.trim()
        val ticked = clean.endsWith(TICK) || clean.endsWith(HEAVY_TICK)
        return ListLine(
            text = if (ticked) clean.dropLast(1).trim() else clean,
            nested = nested,
            done = done || ticked,
        )
    }

    /** Два пробела — уже подпункт: столько ставят руками, четыре приходят из .md. */
    private fun nested(indent: String): Boolean = indent.length >= 2

    private const val TICK = '✓'
    private const val HEAVY_TICK = '✔'
}
