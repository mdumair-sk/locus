package com.locus.core.data.usage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** P-5: DAO for recording and querying token usage records. */
@Dao
interface UsageDao {
    @Insert suspend fun insert(entity: UsageEntity): Long

    @Query(
        "SELECT * FROM token_usage WHERE timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp ASC",
    )
    fun observeUsageBetween(
        startTime: Long,
        endTime: Long,
    ): Flow<List<UsageEntity>>

    @Query(
        "SELECT * FROM token_usage WHERE timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp ASC",
    )
    suspend fun getUsageBetween(
        startTime: Long,
        endTime: Long,
    ): List<UsageEntity>

    @Query("SELECT * FROM token_usage ORDER BY timestamp ASC")
    fun observeAllUsage(): Flow<List<UsageEntity>>

    @Query("SELECT * FROM token_usage ORDER BY timestamp ASC")
    suspend fun getAllUsage(): List<UsageEntity>

    @Query("DELETE FROM token_usage")
    suspend fun clearAll()
}
