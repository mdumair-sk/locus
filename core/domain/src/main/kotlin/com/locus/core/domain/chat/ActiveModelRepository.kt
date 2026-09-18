package com.locus.core.domain.chat

import kotlinx.coroutines.flow.Flow

/** Repository providing the currently active model selection for Chat and RAG. */
interface ActiveModelRepository {
    fun observeActiveModel(): Flow<ActiveModelInfo>

    suspend fun setActiveModel(model: ActiveModelInfo)

    fun getActiveModel(): ActiveModelInfo
}
