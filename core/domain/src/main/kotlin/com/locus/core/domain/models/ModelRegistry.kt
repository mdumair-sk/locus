package com.locus.core.domain.models

import kotlinx.coroutines.flow.Flow

interface ModelRegistry {
    fun observeModels(): Flow<List<RegistryEntry>>

    suspend fun getModels(): List<RegistryEntry>
}
