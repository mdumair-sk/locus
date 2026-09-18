package com.locus.core.domain.models

data class ModelMeta(
    val modelId: String,
    val device: String,
    val notes: String = "",
    val rating: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val benchmarkedAt: Long = 0L,
)
