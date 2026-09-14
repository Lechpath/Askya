package app.askya.data.repository

import app.askya.data.db.dao.YetDao
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.domain.model.ListMark
import app.askya.domain.markdown.ListInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class YetRepository(private val dao: YetDao) {

    fun lists(): Flow<List<YetList>> = dao.observeLists()

    /** Свежие списки — для «Недавнего» в меню. */
    fun recentLists(limit: Int): Flow<List<YetList>> = dao.observeRecentLists(limit)

    fun list(id: Long): Flow<YetList?> = dao.observeList(id)

    fun items(listId: Long): Flow<List<YetItem>> = dao.observeItems(listId)

    /** Сколько в каждом списке осталось: карточка списка показывает «3 из 12». */
    fun remaining(): Flow<Map<Long, Int>> = dao.observeAllItems()
        .map { all -> all.filterNot { it.done }.groupingBy { it.listId }.eachCount() }

    /** Сколько в каждом списке строк всего — вторая половина той же цифры. */
    fun sizes(): Flow<Map<Long, Int>> = dao.observeAllItems()
        .map { all -> all.groupingBy { it.listId }.eachCount() }

    /**
     * Строки всех списков разом, разложенные по списку.
     *
     * Нужны ленте Scroll: в карточку списка вписаны его первые пункты, и
     * спрашивать их подпиской на каждый список значило бы завести по подписке
     * на карточку. Порядок тот же, что на экране самого списка ([items]):
     * сделанное вниз, остальное по времени.
     */
    fun itemsByList(): Flow<Map<Long, List<YetItem>>> = dao.observeAllItems()
        .map { all ->
            all.groupBy { it.listId }.mapValues { (_, lines) ->
                lines.sortedWith(compareBy({ it.done }, { it.createdAt }, { it.id }))
            }
        }

    suspend fun addList(title: String, mark: ListMark): Long =
        dao.insertList(YetList(title = title.trim(), mark = mark))

    /**
     * Название и знак правятся вместе: в карточке списка они стоят рядом, и
     * решают их одним заходом.
     */
    suspend fun updateList(list: YetList, title: String, mark: ListMark) =
        dao.updateList(
            list.copy(
                title = title.trim(),
                mark = mark,
                updatedAt = LocalDateTime.now(),
            ),
        )

    /**
     * Отметить, что список трогали, — ради «Недавнего» в меню.
     *
     * Зовётся при всяком изменении его строк, а не только при правке названия:
     * список, в котором сегодня вычеркнули три пункта, — это и есть тот, к
     * которому вернутся ещё раз. Само название и знак трогают своей правкой
     * ([updateList]) и время пишут сами.
     *
     * Промах не роняет то, ради чего звали: строку могли убрать вместе со
     * списком, и колонка «когда трогали» у несуществующего списка — не беда,
     * из-за которой стоит терять вычеркнутый пункт.
     */
    private suspend fun touch(listId: Long) {
        dao.touchList(listId, LocalDateTime.now())
    }

    /** Список уходит вместе со строками: без этого они остались бы сиротами. */
    suspend fun deleteList(id: Long) {
        dao.deleteItemsOf(id)
        dao.deleteListById(id)
    }

    /**
     * Написанное в окне записи ложится строками: одна отправка — столько строк,
     * сколько их набрали или вставили. Разметка при этом снимается и остаётся
     * уровнем и отметкой ([ListInput]): в списке хранится написанное, а не то,
     * чем его записали.
     */
    suspend fun addLines(listId: Long, source: String) {
        ListInput.parse(source).forEach { line ->
            dao.insertItem(
                YetItem(
                    listId = listId,
                    text = line.text,
                    done = line.done,
                    nested = line.nested,
                ),
            )
        }
        touch(listId)
    }

    suspend fun toggle(item: YetItem) {
        dao.updateItem(item.copy(done = !item.done))
        touch(item.listId)
    }

    /** Убрать строку — в корзину на сутки. См. [ScheduleRepository.remove]. */
    suspend fun removeItem(id: Long) {
        val item = dao.item(id)
        dao.setItemRemoved(id, LocalDateTime.now())
        item?.let { touch(it.listId) }
    }

    suspend fun restoreItem(id: Long) = dao.setItemRemoved(id, null)

    suspend fun purgeTrash() = dao.purgeItems(LocalDateTime.now().minusDays(1))

    suspend fun deleteItem(id: Long) = dao.deleteItemById(id)

    suspend fun clearDone(listId: Long) {
        dao.deleteDoneOf(listId)
        touch(listId)
    }
}
