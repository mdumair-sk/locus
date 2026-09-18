package com.locus.core.data.chat

import com.locus.core.domain.chat.ActiveModelInfo
import com.locus.core.domain.chat.ActiveModelRepository
import com.locus.core.domain.chat.ModelTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultActiveModelRepository
    @Inject
    constructor() : ActiveModelRepository {
        private val activeModelFlow =
            MutableStateFlow(
                ActiveModelInfo(
                    name = DEFAULT_MODEL_NAME,
                    tier = ModelTier.CLOUD,
                    contextLength = DEFAULT_CONTEXT_LENGTH,
                ),
            )

        override fun observeActiveModel(): Flow<ActiveModelInfo> = activeModelFlow.asStateFlow()

        override suspend fun setActiveModel(model: ActiveModelInfo) {
            activeModelFlow.value = model
        }

        override fun getActiveModel(): ActiveModelInfo = activeModelFlow.value

        companion object {
            const val DEFAULT_MODEL_NAME = "gpt-4o"
            const val DEFAULT_CONTEXT_LENGTH = 128_000
        }
    }
