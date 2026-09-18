package com.locus.core.domain.models

import kotlinx.coroutines.flow.Flow

interface ModelMetaRepository {
    fun observeModelMeta(
        modelId: String,
        device: String,
    ): Flow<ModelMeta?>

    fun observeAll(device: String): Flow<List<ModelMeta>>

    suspend fun getModelMeta(
        modelId: String,
        device: String,
    ): ModelMeta?

    suspend fun saveNotesAndRating(
        modelId: String,
        device: String,
        notes: String,
        rating: Int,
    )

    suspend fun saveBenchmarkResult(
        modelId: String,
        device: String,
        tokensPerSecond: Double,
        benchmarkedAt: Long,
    )

    suspend fun deleteByModelId(modelId: String)
}
