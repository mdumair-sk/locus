package com.locus.core.domain.models

import kotlinx.coroutines.flow.Flow

interface ModelManagerRepository {
    suspend fun searchRepos(query: String): Result<List<ModelRepoSummary>>

    suspend fun listQuantFiles(repoId: String): Result<List<ModelFileInfo>>

    suspend fun enqueueDownload(
        repoId: String,
        filename: String,
        sha256: String = "",
        expectedSize: Long = 0L,
    ): Result<String>

    fun observeDownloads(): Flow<List<ModelDownloadProgress>>

    suspend fun cancelDownload(workId: String)

    suspend fun getDownloadedModels(): List<DownloadedModel>

    suspend fun deleteModel(filename: String): Boolean

    suspend fun getStorageStats(): ModelStorageStats
}
