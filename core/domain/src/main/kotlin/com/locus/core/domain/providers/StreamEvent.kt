package com.locus.core.domain.providers

import com.locus.core.domain.usage.UsageEvent

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
        val usage: UsageEvent? = null,
    ) : StreamEvent

    data class Error(
        val message: String,
        val cause: Throwable? = null,
    ) : StreamEvent

    data class Usage(
        val usage: UsageEvent,
    ) : StreamEvent
}
