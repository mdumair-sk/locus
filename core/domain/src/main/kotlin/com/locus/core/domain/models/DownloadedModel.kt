package com.locus.core.domain.models

data class DownloadedModel(
    val filename: String,
    val sizeBytes: Long,
    val path: String,
    val lastModified: Long = 0L,
)
