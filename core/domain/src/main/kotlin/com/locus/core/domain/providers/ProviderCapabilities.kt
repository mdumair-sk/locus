package com.locus.core.domain.providers

data class ProviderCapabilities(
    val supportsNativeTools: Boolean,
    val contextLength: Int,
    val pricePerMillionInputTokens: Double?,
    val pricePerMillionOutputTokens: Double?,
)
