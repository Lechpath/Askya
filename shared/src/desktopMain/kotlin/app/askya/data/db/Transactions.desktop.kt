package app.askya.data.db

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

actual suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
