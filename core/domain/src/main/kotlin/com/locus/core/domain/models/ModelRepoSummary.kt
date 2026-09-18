package com.locus.core.domain.models

data class ModelRepoSummary(
    val id: String,
    val description: String = "",
    val downloads: Int = 0,
    val likes: Int = 0,
)
