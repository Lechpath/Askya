package app.askya.data.repository

import app.askya.data.db.dao.ReminderDao
import app.askya.data.entity.Reminder
import kotlinx.coroutines.flow.Flow

class ReminderRepository(private val dao: ReminderDao) {

    fun reminders(): Flow<List<Reminder>> = dao.observeAll()

    /** Напоминания, привязанные к делам дня. */
    fun forItems(): Flow<List<Reminder>> = dao.observeForItems()

    suspend fun get(id: Long): Reminder? = dao.getById(id)

    suspend fun enabled(): List<Reminder> = dao.enabled()

    /** Напоминания названных дел — чтобы снять их вместе с самими делами. */
    suspend fun forItems(itemIds: List<Long>): List<Reminder> = dao.byItems(itemIds)

    suspend fun add(reminder: Reminder): Long = dao.insert(reminder)

    suspend fun save(reminder: Reminder) = dao.update(reminder)

    suspend fun delete(reminder: Reminder) = dao.delete(reminder)
}
