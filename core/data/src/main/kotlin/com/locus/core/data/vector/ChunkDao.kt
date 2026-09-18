package com.locus.core.data.vector

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ChunkDao {
    @Query("SELECT EXISTS(SELECT 1 FROM chunks)")
    suspend fun hasChunks(): Boolean

    @Query("SELECT sourceChecksum, embeddingModelId FROM chunks WHERE noteId = :noteId LIMIT 1")
    suspend fun getMetadata(noteId: String): ChunkMetadataTuple?

    @Query("SELECT chunkId, noteId, embedding FROM chunks")
    suspend fun getAllEmbeddingRows(): List<ChunkEmbeddingTuple>

    @Query("SELECT chunkId, noteId, embedding FROM chunks WHERE noteId IN (:noteIds)")
    suspend fun getEmbeddingRowsForNotes(noteIds: List<String>): List<ChunkEmbeddingTuple>

    @Query("SELECT * FROM chunks WHERE noteId = :noteId")
    suspend fun getChunksByNoteId(noteId: String): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE chunkId = :chunkId LIMIT 1")
    suspend fun getChunkById(chunkId: String): ChunkEntity?

    @Query("SELECT * FROM chunks WHERE chunkId IN (:chunkIds)")
    suspend fun getChunksByIds(chunkIds: List<String>): List<ChunkEntity>

    @Query("DELETE FROM chunks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("DELETE FROM chunks")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Transaction
    suspend fun replaceChunksForNote(
        noteId: String,
        chunks: List<ChunkEntity>,
    ) {
        deleteByNoteId(noteId)
        if (chunks.isNotEmpty()) {
            insertAll(chunks)
        }
    }
}
