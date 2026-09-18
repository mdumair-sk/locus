package com.locus.core.ai.models

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.locus.core.ai.hf.HuggingFaceCatalogClient
import com.locus.core.ai.llama.DeviceFingerprintProvider
import com.locus.core.ai.llama.ModelBenchmark
import com.locus.core.ai.llama.ModelDownloadWorker
import com.locus.core.ai.llama.ModelDownloader
import com.locus.core.domain.models.BenchmarkResult
import com.locus.core.domain.models.DownloadStatus
import com.locus.core.domain.models.DownloadedModel
import com.locus.core.domain.models.ModelDownloadProgress
import com.locus.core.domain.models.ModelFileInfo
import com.locus.core.domain.models.ModelManagerRepository
import com.locus.core.domain.models.ModelMeta
import com.locus.core.domain.models.ModelMetaRepository
import com.locus.core.domain.models.ModelRepoSummary
import com.locus.core.domain.models.ModelStorageStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultModelManagerRepository
    @Inject
    constructor(
        private val hfClient: HuggingFaceCatalogClient,
        private val modelDownloader: ModelDownloader,
        private val workManager: WorkManager,
        private val modelBenchmark: ModelBenchmark,
        private val metaRepository: ModelMetaRepository,
        private val deviceProvider: DeviceFingerprintProvider,
    ) : ModelManagerRepository {
        override suspend fun searchRepos(query: String): Result<List<ModelRepoSummary>> =
            runCatching {
                hfClient.searchGgufRepos(query).map { summary ->
                    ModelRepoSummary(
                        id = summary.id,
                        description = summary.description,
                        downloads = summary.downloads,
                        likes = summary.likes,
                    )
                }
            }

        override suspend fun listQuantFiles(repoId: String): Result<List<ModelFileInfo>> =
            runCatching {
                hfClient.listFiles(repoId).map { fileInfo ->
                    ModelFileInfo(
                        name = fileInfo.name,
                        size = fileInfo.size,
                        sha256 = fileInfo.sha256,
                    )
                }
            }

        override suspend fun enqueueDownload(
            repoId: String,
            filename: String,
            sha256: String,
            expectedSize: Long,
        ): Result<String> =
            runCatching {
                val workId =
                    modelDownloader.enqueueDownloadWork(
                        repo = repoId,
                        filename = filename,
                        sha256 = sha256,
                        expectedSize = expectedSize,
                    )
                workId.toString()
            }

        override fun observeDownloads(): Flow<List<ModelDownloadProgress>> =
            workManager.getWorkInfosByTagFlow(ModelDownloader.TAG_MODEL_DOWNLOAD).map { workInfoList ->
                workInfoList.map { workInfo -> mapWorkInfoToDownloadProgress(workInfo) }
            }

        override suspend fun cancelDownload(workId: String) {
            runCatching { workManager.cancelWorkById(UUID.fromString(workId)) }
        }

        override suspend fun getDownloadedModels(): List<DownloadedModel> =
            modelDownloader.getDownloadedModels().map { file ->
                DownloadedModel(
                    filename = file.name,
                    sizeBytes = file.length(),
                    path = file.absolutePath,
                    lastModified = file.lastModified(),
                )
            }

        override suspend fun deleteModel(filename: String): Boolean {
            val deleted = modelDownloader.deleteModel(filename)
            if (deleted) {
                metaRepository.deleteByModelId(filename)
            }
            return deleted
        }

        override suspend fun getStorageStats(): ModelStorageStats = modelDownloader.getStorageStats()

        override fun observeAllModelMeta(): Flow<List<ModelMeta>> {
            val device = deviceProvider.getDeviceFingerprint()
            return metaRepository.observeAll(device)
        }

        override suspend fun saveModelNotesAndRating(
            modelId: String,
            notes: String,
            rating: Int,
        ) {
            metaRepository.saveNotesAndRating(
                modelId = modelId,
                device = deviceProvider.getDeviceFingerprint(),
                notes = notes,
                rating = rating,
            )
        }

        override suspend fun runBenchmark(
            modelId: String,
            path: String,
        ): Result<BenchmarkResult> = runCatching { modelBenchmark.run(modelId, path) }

        private fun mapWorkInfoToDownloadProgress(workInfo: WorkInfo): ModelDownloadProgress {
            val progressData = workInfo.progress
            val outputData = workInfo.outputData

            val filename =
                progressData.getString(ModelDownloadWorker.KEY_FILENAME)
                    ?: outputData.getString(ModelDownloadWorker.KEY_FILENAME)
                    ?: workInfo.tags
                        .firstOrNull {
                            it.startsWith("tag_model_") &&
                                it != ModelDownloader.TAG_MODEL_DOWNLOAD
                        }?.removePrefix("tag_model_")
                        .orEmpty()

            val bytesRead = progressData.getLong(ModelDownloadWorker.KEY_BYTES_READ, 0L)
            val totalBytes = progressData.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L)
            val progressPercentage = progressData.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)

            val (status, errorMessage) =
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED ->
                        DownloadStatus.PENDING to null
                    WorkInfo.State.RUNNING -> DownloadStatus.DOWNLOADING to null
                    WorkInfo.State.SUCCEEDED -> DownloadStatus.COMPLETED to null
                    WorkInfo.State.FAILED -> {
                        val error =
                            outputData.getString(ModelDownloadWorker.KEY_ERROR)
                                ?: "Download failed"
                        DownloadStatus.FAILED to error
                    }
                    WorkInfo.State.CANCELLED -> DownloadStatus.CANCELLED to null
                }

            return ModelDownloadProgress(
                workId = workInfo.id.toString(),
                filename = filename,
                bytesRead = bytesRead,
                totalBytes = totalBytes,
                progressPercentage =
                    if (status == DownloadStatus.COMPLETED) 100 else progressPercentage,
                status = status,
                errorMessage = errorMessage,
            )
        }
    }
