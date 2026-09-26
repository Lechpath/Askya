package app.askya.agent

import app.askya.data.sync.Json

/**
 * Страница выборки — для инструментов, чей ответ может быть длинным (поиск по
 * заметкам). Остальным она не нужна, и навязывать её им незачем.
 *
 * Правило одно на всё приложение: выборку ограничивает тот, кто её делает, —
 * так же, как `LedgerDao.observeOnAccount` берёт не больше `limit` строк, а
 * синхронизация шлёт порции по `PORTION`. [ToolTurn] здесь ни при чём: его
 * `maxResultSize` — последняя страховка, которая отказывает целиком и ничего
 * не режет, а не способ листать. Инструмент, чья страница в неё не влезает,
 * должен брать меньше строк или короче их описывать.
 *
 * **Контракт в ответе:**
 * ```
 * "items":      [...]          — строки этой страницы
 * "hasMore":    true | false   — есть ли что-то дальше
 * "nextCursor": "…" | null     — откуда продолжать; null, если продолжать нечего
 * ```
 * **Контракт во входе:** `limit` (сколько строк) и `cursor` (откуда — ровно
 * тот `nextCursor`, что вернул прошлый ответ).
 *
 * Курсор непрозрачен: для модели это строка, которую надо вернуть как есть.
 * Сейчас внутри — сдвиг в выборке, упорядоченной самим инструментом; если
 * инструменту понадобится другое (например, «после записи с таким-то
 * временем»), он поменяет содержимое, а контракт останется прежним.
 * Проверяет курсор тот, кто его выдал: [ToolTurn] его не читает.
 */
data class PageRequest(val limit: Int, val offset: Int)

/** Страница: строки, есть ли ещё и откуда продолжать. */
data class ToolPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val nextCursor: String?,
) {
    /** Страница в виде ответа инструмента; [view] — как описать одну строку. */
    fun toResult(view: (T) -> Any?): Map<String, Any?> = mapOf(
        ITEMS to items.map(view),
        HAS_MORE to hasMore,
        NEXT_CURSOR to nextCursor,
    )

    companion object {
        const val ITEMS = "items"
        const val HAS_MORE = "hasMore"
        const val NEXT_CURSOR = "nextCursor"
        const val LIMIT = "limit"
        const val CURSOR = "cursor"

        /** Ключи входа страницы — чтобы добавить их к [readArgs]. */
        val KEYS: Set<String> = setOf(LIMIT, CURSOR)

        /**
         * Прочитать `limit` и `cursor`. [maxLimit] — сколько строк инструмент
         * согласен отдать за раз; больше просить нельзя.
         */
        fun ToolArgs.pageRequest(defaultLimit: Int, maxLimit: Int): PageRequest {
            val limit = int(LIMIT, defaultLimit, 1..maxLimit)
            val offset = string(CURSOR)?.let { offsetOf(it) } ?: 0
            return PageRequest(limit, offset)
        }

        /** Взять страницу из уже упорядоченной выборки. */
        fun <T> of(ordered: List<T>, request: PageRequest): ToolPage<T> {
            val items = ordered.drop(request.offset).take(request.limit)
            val end = request.offset + items.size
            val more = end < ordered.size
            return ToolPage(items, hasMore = more, nextCursor = if (more) cursorOf(end) else null)
        }

        /**
         * Взять страницу, которая целиком влезет в [budget] знаков JSON, — для
         * выборок, где одна строка бывает длинной (текст заметки).
         *
         * Строки берутся по порядку, пока влезают, и не больше `limit`. Не
         * влезла очередная — страница кончается раньше: `hasMore` — `true`, а
         * курсор указывает ровно на неё, так что она придёт следующей
         * страницей. Это не обрезка: ни одна строка не укорочена и не
         * потеряна, модель видит, что продолжение есть.
         *
         * Первая строка берётся всегда, даже если одна не влезает, — иначе
         * листание встало бы на месте. Такую страницу откажет целиком
         * страховка [ToolTurn]; чтобы до неё не доходило, у инструмента одна
         * строка должна быть заведомо меньше бюджета.
         *
         * [view] — то же описание строки, что потом пойдёт в [toResult]: мерить
         * надо то, что уйдёт модели.
         */
        fun <T> within(ordered: List<T>, request: PageRequest, budget: Int, view: (T) -> Any?): ToolPage<T> {
            val candidates = ordered.drop(request.offset).take(request.limit)
            // Обёртка с самым длинным курсором и `false` — с запасом на любой исход.
            var used = Json.write(
                mapOf(ITEMS to emptyList<Any?>(), HAS_MORE to false, NEXT_CURSOR to cursorOf(ordered.size)),
            ).length
            var taken = 0
            for (item in candidates) {
                val size = Json.write(view(item)).length + if (taken > 0) 1 else 0
                if (taken > 0 && used + size > budget) break
                used += size
                taken++
            }
            val end = request.offset + taken
            val more = end < ordered.size
            return ToolPage(candidates.take(taken), hasMore = more, nextCursor = if (more) cursorOf(end) else null)
        }

        private const val PREFIX = "o:"

        private fun cursorOf(offset: Int) = PREFIX + offset

        /**
         * Курсор, который инструмент не выдавал, — ошибка аргумента, а не начало
         * с нуля: молча начать сначала значило бы отдать модели те же строки
         * второй раз под видом новых.
         */
        private fun ToolArgs.offsetOf(cursor: String): Int =
            cursor.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toIntOrNull()?.takeIf { it >= 0 }
                ?: reject("$CURSOR: передайте nextCursor из прошлого ответа как есть")
    }
}
