package app.askya.data.db

import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * Несколько записей в базу — одним куском: либо все, либо ни одной.
 *
 * Своё имя поверх Room, а не его `withTransaction`: тот есть только у
 * Android. На телефоне это он и есть, слово в слово, — поведение базы от
 * переезда не поменялось. На компьютере то же самое делается соединением на
 * запись и немедленной транзакцией: так Room велит поступать вне Android.
 */
expect suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R

/**
 * Кто собирает базу вне Android: Room пишет его сам, по одному на цель, а
 * здесь только объявление. Без него база, объявленная в общем коде, не
 * заводится ни на одной системе.
 */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
