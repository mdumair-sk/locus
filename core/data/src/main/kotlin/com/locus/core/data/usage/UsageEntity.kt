package com.locus.core.data.usage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** P-5: Persistent record of token usage for a model provider invocation. */
@Entity(
    tableName = "token_usage",
    indices =
        [
            Index(value = ["providerId"]),
            Index(value = ["timestamp"]),
        ],
)
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val modelId: String? = null,
    val inputTokens: Long,
    val outputTokens: Long,
    val timestamp: Long,
)
