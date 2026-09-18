package com.locus.core.domain.providers

sealed interface StreamEvent {
    data class TokenDelta(
        val text: String,
    ) : StreamEvent

    data class ToolCallDelta(
        val index: Int = 0,
        val id: String? = null,
        val name: String? = null,
        val argumentsDelta: String? = null,
    ) : StreamEvent

    data class Done(
        val finishReason: String? = null,
    ) : StreamEvent

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : StreamEvent
}
