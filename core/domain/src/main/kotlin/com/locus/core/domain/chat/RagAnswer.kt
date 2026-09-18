package com.locus.core.domain.chat

data class RagAnswer(
    val text: String,
    val sources: List<CitedSource>,
)
