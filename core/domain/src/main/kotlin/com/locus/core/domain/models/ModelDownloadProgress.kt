package com.locus.core.domain.models

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class ModelDownloadProgress(
    val workId: String,
    val filename: String,
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercentage: Int = 0,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val errorMessage: String? = null,
)
