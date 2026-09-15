package com.locus.core.domain.search

data class Chunk(
    val noteId: String,
    val noteTitle: String,
    val headingPath: List<String>,
    val text: String,
    val index: Int,
)
