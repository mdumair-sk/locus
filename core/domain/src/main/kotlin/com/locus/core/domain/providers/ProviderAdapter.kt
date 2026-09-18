package com.locus.core.domain.providers

import com.locus.core.domain.chat.ChatModelClient
import kotlinx.coroutines.flow.Flow

interface ProviderAdapter : ChatModelClient {
    val capabilities: ProviderCapabilities

    fun streamChat(
        messages: List<ProviderMessage>,
        tools: List<ToolSchema> = emptyList(),
    ): Flow<StreamEvent>

    override fun generate(
        prompt: String,
        tools: List<ToolSchema>,
    ): Flow<StreamEvent> =
        streamChat(
            messages = listOf(ProviderMessage(role = ProviderRole.USER, content = prompt)),
            tools = tools,
        )
}
