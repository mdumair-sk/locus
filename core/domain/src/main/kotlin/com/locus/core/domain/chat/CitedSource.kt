package com.locus.core.domain.chat

data class CitedSource(
    val noteId: String,
    val noteTitle: String,
    val headingPath: List<String> = emptyList(),
)
