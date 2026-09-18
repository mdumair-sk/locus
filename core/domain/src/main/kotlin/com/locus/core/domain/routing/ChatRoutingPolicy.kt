package com.locus.core.domain.routing

/**
 * Domain error thrown when no provider/model is configured and no local fallback exists. Surfaced
 * by the UI as "choose a provider".
 */
class NoConfiguredProviderException(
    message: String = "choose a provider",
) : IllegalStateException(message)

typealias NoProviderConfiguredException = NoConfiguredProviderException

/**
 * Partial P-4 routing policy for Chat / RAG Q&A in Phase 1.
 *
 * Resolves the user's configured strong cloud model. If unconfigured, throws
 * [NoConfiguredProviderException] ("choose a provider") because no local fallback leg exists until
 * Phase 2 (Prompt 52).
 */
class ChatRoutingPolicy(
    private val usersStrongCloudModel: ModelRef?,
) {
    fun resolve(): ModelRef = usersStrongCloudModel ?: throw NoConfiguredProviderException("choose a provider")
}
