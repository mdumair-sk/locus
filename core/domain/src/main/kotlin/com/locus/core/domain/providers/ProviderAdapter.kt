package com.locus.core.domain.providers

import kotlinx.coroutines.flow.Flow

interface ProviderAdapter {
    val capabilities: ProviderCapabilities

    fun streamChat(
        messages: List<ProviderMessage>,
        tools: List<ToolSchema> = emptyList(),
    ): Flow<StreamEvent>
}
