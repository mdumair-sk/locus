package com.locus.core.domain.chat

import com.locus.core.domain.providers.StreamEvent
import com.locus.core.domain.providers.ToolSchema
import kotlinx.coroutines.flow.Flow

/**
 * Shared domain abstraction for streaming text/chat generation across local and cloud models.
 *
 * M-2: Zero network when local models are selected.
 */
interface ChatModelClient {
    fun generate(
        prompt: String,
        tools: List<ToolSchema> = emptyList(),
    ): Flow<StreamEvent>
}
