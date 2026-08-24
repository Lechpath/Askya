package app.askya.data.repository

import app.askya.data.db.dao.PracticeLogDao
import app.askya.data.entity.PracticeLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class PracticeRepository(private val dao: PracticeLogDao) {

    fun logs(): Flow<List<PracticeLog>> = dao.observeAll()

    fun logsSince(from: LocalDate): Flow<List<PracticeLog>> = dao.observeSince(from)

    fun practiceNames(): Flow<List<String>> = dao.observePracticeNames()

    suspend fun add(log: PracticeLog): Long = dao.insert(log)

    suspend fun save(log: PracticeLog) = dao.update(log)

    suspend fun delete(log: PracticeLog) = dao.delete(log)
}
