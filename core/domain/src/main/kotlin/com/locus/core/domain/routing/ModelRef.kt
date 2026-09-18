package com.locus.core.domain.routing

enum class ModelTier {
    LOCAL,
    CLOUD,
}

data class ModelRef(
    val id: String,
    val tier: ModelTier,
    val providerId: String?,
)
