package com.locus.core.domain.chat

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeSessions(): Flow<List<ChatSession>>

    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>

    suspend fun createSession(name: String): ChatSession

    suspend fun appendMessage(
        sessionId: String,
        message: ChatMessage,
    )
}
