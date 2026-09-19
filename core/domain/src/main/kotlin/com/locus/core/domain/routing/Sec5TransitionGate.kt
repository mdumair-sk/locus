package com.locus.core.domain.routing

sealed interface RouteDecision {
    data class Direct(
        val model: ModelRef,
    ) : RouteDecision

    data class RequiresCloudTransitionConfirmation(
        val fromModel: ModelRef,
        val toModel: ModelRef,
        val providerId: String,
    ) : RouteDecision
}

/**
 * SEC-5 gate: the ONLY function permitted to authorize sending note content to a cloud provider
 * when the active model for the conversation was local.
 */
class Sec5TransitionGate {
    fun evaluate(
        currentModel: ModelRef,
        target: ModelRef,
    ): RouteDecision =
        if (currentModel.tier == ModelTier.LOCAL && target.tier == ModelTier.CLOUD) {
            RouteDecision.RequiresCloudTransitionConfirmation(
                fromModel = currentModel,
                toModel = target,
                providerId =
                    requireNotNull(target.providerId) {
                        "cloud ModelRef must declare providerId"
                    },
            )
        } else {
            RouteDecision.Direct(target)
        }
}
