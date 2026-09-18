package com.locus.core.domain.settings

import kotlinx.coroutines.flow.Flow

interface AgentSettingsStore {
    val bulkCap: Flow<Int>

    suspend fun setBulkCap(value: Int)

    companion object {
        const val DEFAULT_BULK_CAP = 50
    }
}
