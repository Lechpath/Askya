package app.askya.data.repository

import app.askya.data.db.dao.BridgeDao
import app.askya.data.entity.Bridge
import kotlinx.coroutines.flow.Flow

/**
 * Мосты: что к чему подключено.
 *
 * Каким мостом открывается конкретное дело, здесь не решается: это чистое
 * правило о трёх слоях, оно живёт в `bridges/DeedTarget.kt` и покрыто тестами.
 * Репозиторий держит только хранение.
 */
class BridgeRepository(private val dao: BridgeDao) {

    fun bridges(): Flow<List<Bridge>> = dao.observeAll()

    suspend fun all(): List<Bridge> = dao.all()

    suspend fun get(id: Long): Bridge? = dao.byId(id)

    suspend fun save(bridge: Bridge): Long =
        if (bridge.id == 0L) dao.insert(bridge) else dao.update(bridge).let { bridge.id }

    suspend fun delete(bridge: Bridge) = dao.delete(bridge)

    suspend fun markUsed(id: Long) = dao.markUsed(id, System.currentTimeMillis())
}
