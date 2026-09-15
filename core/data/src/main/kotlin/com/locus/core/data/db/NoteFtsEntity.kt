package com.locus.core.data.db

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "note_fts")
@Fts4(contentEntity = NoteIndexEntity::class)
data class NoteFtsEntity(
    val title: String,
    val bodyPreview: String,
)
