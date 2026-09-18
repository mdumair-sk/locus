package com.locus.core.domain.search

data class SearchResult(
    val noteId: String,
    val title: String,
    val snippet: String,
    val score: Double = 1.0,
    val headingPath: List<String> = emptyList(),
)
