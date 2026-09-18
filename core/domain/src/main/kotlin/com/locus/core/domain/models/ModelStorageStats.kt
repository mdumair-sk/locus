package com.locus.core.domain.models

data class ModelStorageStats(
    val totalUsedBytes: Long,
    val freeBytes: Long,
    val totalDeviceBytes: Long,
)
