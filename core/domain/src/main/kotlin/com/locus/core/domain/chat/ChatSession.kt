package com.locus.core.domain.chat

import java.time.Instant

data class ChatSession(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val modifiedAt: Instant,
)
