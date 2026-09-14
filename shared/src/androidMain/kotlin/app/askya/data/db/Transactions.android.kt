package app.askya.data.db

import androidx.room.RoomDatabase
import androidx.room.withTransaction as roomTransaction

actual suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    roomTransaction(block)
