package com.locus.core.domain.models

data class ModelRecommendation(
    val id: String,
    val name: String,
    val repo: String,
    val filename: String,
    val sha256: String,
    val sizeBytes: Long,
    val contextLength: Int,
    val description: String,
    val task: String,
)
