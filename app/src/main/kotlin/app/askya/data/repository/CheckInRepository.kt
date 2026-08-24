package app.askya.data.repository

import app.askya.data.db.dao.CheckInDao
import app.askya.data.entity.CheckIn
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class CheckInRepository(private val dao: CheckInDao) {

    fun checkIns(): Flow<List<CheckIn>> = dao.observeAll()

    fun checkInsOn(date: LocalDate): Flow<List<CheckIn>> = dao.observeByDate(date)

    suspend fun add(checkIn: CheckIn): Long = dao.insert(checkIn)

    suspend fun delete(checkIn: CheckIn) = dao.delete(checkIn)
}
