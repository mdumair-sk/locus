package com.locus.core.domain.chat

import java.time.Instant

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: ChatRole,
    val content: String,
    val citations: List<CitedSource> = emptyList(),
    val timestamp: Instant,
)
