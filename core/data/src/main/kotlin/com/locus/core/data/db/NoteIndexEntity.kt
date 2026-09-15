package com.locus.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locus.core.domain.notes.NoteType
import java.time.Instant

@Entity(tableName = "note_index")
data class NoteIndexEntity(
    @PrimaryKey
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
    val bodyPreview: String = "",
)
