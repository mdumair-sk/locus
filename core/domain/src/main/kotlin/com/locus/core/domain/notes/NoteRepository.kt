package com.locus.core.domain.notes

import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeNotesInFolder(folderPath: String): Flow<List<Note>>

    fun observeAllNotes(): Flow<List<Note>>

    suspend fun readBody(noteId: String): String

    suspend fun listFolders(): List<String>
}
