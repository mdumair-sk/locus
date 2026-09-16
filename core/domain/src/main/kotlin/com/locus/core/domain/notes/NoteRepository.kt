package com.locus.core.domain.notes

import kotlinx.coroutines.flow.Flow

data class RescanReport(
    val added: Int,
    val changed: Int,
    val removed: Int,
)

interface NoteRepository {
    fun observeNotesInFolder(folderPath: String): Flow<List<Note>>

    fun observeAllNotes(): Flow<List<Note>>

    suspend fun readBody(noteId: String): String

    suspend fun listFolders(): List<String>

    suspend fun createNote(
        folderPath: String,
        title: String,
        type: NoteType,
    ): Note

    suspend fun edit(
        noteId: String,
        newBody: String,
    )

    suspend fun setPinned(
        noteId: String,
        pinned: Boolean,
    )

    suspend fun setColor(
        noteId: String,
        color: String?,
    )

    suspend fun rescan(): RescanReport
}
