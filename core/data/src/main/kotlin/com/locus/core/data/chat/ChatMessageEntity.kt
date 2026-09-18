package com.locus.core.data.chat

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.locus.core.domain.chat.ChatRole
import java.time.Instant

@Entity(
    tableName = "chat_messages",
    foreignKeys =
        [
            ForeignKey(
                entity = ChatSessionEntity::class,
                parentColumns = ["id"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices =
        [
            Index(value = ["sessionId"]),
        ],
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: ChatRole,
    val content: String,
    val citationsJson: String,
    val timestamp: Instant,
)
