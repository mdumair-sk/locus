package com.locus.core.domain.providers

enum class ProviderRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class ProviderMessage(
    val role: ProviderRole,
    val content: String,
    val name: String? = null,
    val toolCallId: String? = null,
)
