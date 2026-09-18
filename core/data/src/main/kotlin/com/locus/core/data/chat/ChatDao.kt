package com.locus.core.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY modifiedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_sessions SET modifiedAt = :modifiedAt WHERE id = :sessionId")
    suspend fun updateSessionModifiedAt(
        sessionId: String,
        modifiedAt: Instant,
    )

    @Transaction
    suspend fun appendMessageAndUpdateSession(
        message: ChatMessageEntity,
        modifiedAt: Instant,
    ) {
        insertMessage(message)
        updateSessionModifiedAt(message.sessionId, modifiedAt)
    }

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
