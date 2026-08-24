package app.askya.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Реплика интервью.
 *
 * Хранится потому, что интервью длинное и его почти наверняка прервут:
 * без истории продолжить разговор нечем — модель не помнит ничего между
 * запросами, контекст каждый раз собирается заново.
 *
 * [fromUser] = false означает вопрос интервьюера.
 */
@Entity(tableName = "interview_messages")
data class InterviewMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromUser: Boolean,
    val text: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
