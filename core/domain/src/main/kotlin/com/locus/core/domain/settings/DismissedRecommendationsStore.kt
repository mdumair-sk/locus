package com.locus.core.domain.settings

import kotlinx.coroutines.flow.Flow

interface DismissedRecommendationsStore {
    val dismissedIds: Flow<Set<String>>

    suspend fun dismiss(entryId: String)
}
