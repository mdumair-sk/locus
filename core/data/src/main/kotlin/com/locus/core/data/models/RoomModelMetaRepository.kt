package com.locus.core.data.models

import com.locus.core.domain.models.ModelMeta
import com.locus.core.domain.models.ModelMetaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomModelMetaRepository
    @Inject
    constructor(
        private val dao: ModelMetaDao,
    ) : ModelMetaRepository {
        override fun observeModelMeta(
            modelId: String,
            device: String,
        ): Flow<ModelMeta?> = dao.observe(modelId, device).map { it?.toDomain() }

        override fun observeAll(device: String): Flow<List<ModelMeta>> {
            val flow = dao.observeAll(device)
            return flow.map { list -> list.map { it.toDomain() } }
        }

        override suspend fun getModelMeta(
            modelId: String,
            device: String,
        ): ModelMeta? = dao.get(modelId, device)?.toDomain()

        override suspend fun saveNotesAndRating(
            modelId: String,
            device: String,
            notes: String,
            rating: Int,
        ) {
            dao.updateNotesAndRating(modelId, device, notes, rating)
        }

        override suspend fun saveBenchmarkResult(
            modelId: String,
            device: String,
            tokensPerSecond: Double,
            benchmarkedAt: Long,
        ) {
            dao.updateBenchmark(modelId, device, tokensPerSecond, benchmarkedAt)
        }

        override suspend fun deleteByModelId(modelId: String) {
            dao.deleteByModelId(modelId)
        }

        private fun ModelMetaEntity.toDomain() =
            ModelMeta(
                modelId = modelId,
                device = device,
                notes = notes,
                rating = rating,
                tokensPerSecond = tokensPerSecond,
                benchmarkedAt = benchmarkedAt,
            )
    }
