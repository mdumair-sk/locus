package com.locus.core.domain.notes

import java.time.Instant

data class Note(
    val id: String,
    val title: String,
    val type: NoteType,
    val folderPath: String,
    val pinned: Boolean,
    val color: String?,
    val tags: List<String>,
    val created: Instant,
    val modified: Instant,
    val checksum: String,
)

enum class NoteType {
    NOTE,
    CHECKLIST,
}
