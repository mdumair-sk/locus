package com.locus.core.domain.chat

/**
 * Model tier indicator per SEC-2 / P-4. Distinguishes local on-device models from remote cloud
 * providers.
 */
typealias ModelTier = com.locus.core.domain.routing.ModelTier

/**
 * Metadata for the currently active AI model in Chat.
 *
 * @property name Human-readable model identifier (e.g. "gpt-4o", "llama-3.2-1b")
 * @property tier Whether the model runs locally on-device or via a cloud API
 * @property contextLength Optional context window length in tokens
 */
data class ActiveModelInfo(
    val name: String,
    val tier: ModelTier,
    val contextLength: Int? = null,
) {
    val isLocal: Boolean
        get() = tier == ModelTier.LOCAL
    val isCloud: Boolean
        get() = tier == ModelTier.CLOUD
}
