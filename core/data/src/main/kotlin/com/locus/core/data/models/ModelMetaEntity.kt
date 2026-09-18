package com.locus.core.data.models

import androidx.room.Entity

@Entity(
    tableName = "model_meta",
    primaryKeys = ["modelId", "device"],
)
data class ModelMetaEntity(
    val modelId: String,
    val device: String,
    val notes: String = "",
    val rating: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val benchmarkedAt: Long = 0L,
)
