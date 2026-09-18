package com.locus.core.domain.models

data class BenchmarkResult(
    val tokensPerSecond: Double,
    val totalTokens: Int = 128,
    val durationMs: Long = 0L,
)
