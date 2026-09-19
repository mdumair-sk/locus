package com.locus.core.domain.usage

import java.time.Instant

/** P-5: Token usage event recorded per provider call. */
data class UsageEvent(
    val providerId: String,
    val modelId: String? = null,
    val inputTokens: Long,
    val outputTokens: Long,
    val timestamp: Instant = Instant.now(),
)
