package app.askya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ответ на вопрос из «AskyaKnew» — опросника, который работает без сети.
 *
 * Ключ — номер вопроса: на один вопрос один ответ, и повторное прохождение
 * перезаписывает старое, а не копит дубликаты.
 *
 * [question] хранится рядом с ответом, хотя список вопросов лежит в коде:
 * список ещё будет меняться, и без записанной формулировки старые ответы
 * съехали бы на чужие вопросы молча.
 */
@Entity(tableName = "self_answers")
data class SelfAnswer(
    @PrimaryKey val position: Int,
    val question: String,
    val answer: String,
)
