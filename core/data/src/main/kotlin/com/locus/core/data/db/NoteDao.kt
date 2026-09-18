package com.locus.core.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Upsert suspend fun upsert(entity: NoteIndexEntity)

    @Query("SELECT * FROM note_index WHERE id = :id")
    suspend fun getById(id: String): NoteIndexEntity?

    @Query("DELETE FROM note_index WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM note_index")
    fun observeAll(): Flow<List<NoteIndexEntity>>

    @Query("SELECT * FROM note_index WHERE folderPath = :path")
    fun observeByFolder(path: String): Flow<List<NoteIndexEntity>>

    @Query(
        """
        SELECT note_index.*
        FROM note_index
        JOIN note_fts ON note_index.rowid = note_fts.rowid
        WHERE note_fts MATCH :query
        """,
    )
    suspend fun ftsSearch(query: String): List<NoteIndexEntity>

    @RawQuery suspend fun ftsSearchScoped(query: SupportSQLiteQuery): List<NoteIndexEntity>
}
