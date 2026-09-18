package com.locus.core.data.chat

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
)
