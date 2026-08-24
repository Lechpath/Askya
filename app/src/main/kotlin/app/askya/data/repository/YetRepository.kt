package app.askya.data.repository

import app.askya.data.db.dao.YetDao
import app.askya.data.entity.YetItem
import app.askya.data.entity.YetList
import app.askya.domain.model.ListMark
import app.askya.domain.markdown.ListInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class YetRepository(private val dao: YetDao) {

    fun lists(): Flow<List<YetList>> = dao.observeLists()

    fun list(id: Long): Flow<YetList?> = dao.observeList(id)

    fun items(listId: Long): Flow<List<YetItem>> = dao.observeItems(listId)

    /** Сколько в каждом списке осталось: карточка списка показывает «3 из 12». */
    fun remaining(): Flow<Map<Long, Int>> = dao.observeAllItems()
        .map { all -> all.filterNot { it.done }.groupingBy { it.listId }.eachCount() }

    /** Сколько в каждом списке строк всего — вторая половина той же цифры. */
    fun sizes(): Flow<Map<Long, Int>> = dao.observeAllItems()
        .map { all -> all.groupingBy { it.listId }.eachCount() }

    suspend fun addList(title: String, mark: ListMark): Long =
        dao.insertList(YetList(title = title.trim(), mark = mark))

    /** Название и знак правятся вместе: в карточке списка они стоят рядом. */
    suspend fun updateList(list: YetList, title: String, mark: ListMark) =
        dao.updateList(list.copy(title = title.trim(), mark = mark))

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
    }

    suspend fun toggle(item: YetItem) = dao.updateItem(item.copy(done = !item.done))

    suspend fun deleteItem(id: Long) = dao.deleteItemById(id)

    suspend fun clearDone(listId: Long) = dao.deleteDoneOf(listId)
}
