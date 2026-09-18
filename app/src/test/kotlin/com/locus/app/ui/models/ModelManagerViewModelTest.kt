package com.locus.app.ui.models

import app.cash.turbine.test
import com.locus.core.domain.models.DownloadStatus
import com.locus.core.domain.models.DownloadedModel
import com.locus.core.domain.models.ModelDownloadProgress
import com.locus.core.domain.models.ModelFileInfo
import com.locus.core.domain.models.ModelManagerRepository
import com.locus.core.domain.models.ModelRepoSummary
import com.locus.core.domain.models.ModelStorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelManagerViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepo: FakeModelManagerRepository
    private lateinit var viewModel: ModelManagerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeModelManagerRepository()
        viewModel = ModelManagerViewModel(fakeRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_loadsStorageStatsDownloadedModelsAndDefaultSearch() =
        runTest {
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1024L, state.storageStats.totalUsedBytes)
            assertEquals(1, state.downloadedModels.size)
            assertEquals("qwen-1.gguf", state.downloadedModels[0].filename)
            assertEquals(1, state.searchResults.size)
            assertEquals("Qwen/Qwen2.5-0.5B-Instruct-GGUF", state.searchResults[0].id)
            assertFalse(state.isSearchingRepos)
        }

    @Test
    fun searchRepos_updatesSearchResults() =
        runTest {
            advanceUntilIdle()

            viewModel.onSearchQueryChange("llama")
            viewModel.searchRepos("llama")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(1, state.searchResults.size)
            assertEquals("meta-llama/Llama-3.2-1B-Instruct-GGUF", state.searchResults[0].id)
        }

    @Test
    fun selectRepo_loadsQuantFiles_andClearResets() =
        runTest {
            advanceUntilIdle()

            val repo = ModelRepoSummary(id = "Qwen/Qwen2.5-0.5B-Instruct-GGUF")
            viewModel.selectRepo(repo)
            advanceUntilIdle()

            val selectedState = viewModel.uiState.value
            assertEquals(repo, selectedState.selectedRepo)
            assertEquals(2, selectedState.quantFiles.size)
            assertEquals("qwen2.5-0.5b-q4_k_m.gguf", selectedState.quantFiles[0].name)

            viewModel.clearSelectedRepo()
            val clearedState = viewModel.uiState.value
            assertNull(clearedState.selectedRepo)
            assertTrue(clearedState.quantFiles.isEmpty())
        }

    @Test
    fun startDownload_enqueuesDownloadAndSetsUserMessage() =
        runTest {
            advanceUntilIdle()

            val file = ModelFileInfo(name = "test-model.gguf", size = 5000L, sha256 = "sha-xyz")
            viewModel.startDownload("test/repo", file)
            advanceUntilIdle()

            assertEquals("test-model.gguf", fakeRepo.lastEnqueuedFilename)
            assertEquals("Started downloading test-model.gguf", viewModel.uiState.value.userMessage)

            viewModel.clearUserMessage()
            assertNull(viewModel.uiState.value.userMessage)
        }

    @Test
    fun cancelDownload_cancelsAndSetsUserMessage() =
        runTest {
            advanceUntilIdle()

            viewModel.cancelDownload("work-123")
            advanceUntilIdle()

            assertEquals("work-123", fakeRepo.lastCancelledWorkId)
            assertEquals("Download cancelled", viewModel.uiState.value.userMessage)
        }

    @Test
    fun deleteModel_removesModelAndRefreshesState() =
        runTest {
            advanceUntilIdle()

            val modelToDelete = DownloadedModel("qwen-1.gguf", 1024L, "/models/qwen-1.gguf")
            viewModel.deleteModel(modelToDelete)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.downloadedModels
                    .isEmpty(),
            )
            assertEquals(0L, viewModel.uiState.value.storageStats.totalUsedBytes)
            assertEquals("Deleted qwen-1.gguf", viewModel.uiState.value.userMessage)
        }

    @Test
    fun observeDownloads_updatesActiveDownloadsFlow() =
        runTest {
            viewModel.uiState.test {
                val initial = awaitItem()
                assertTrue(initial.activeDownloads.isEmpty())

                fakeRepo.emitDownloads(
                    listOf(
                        ModelDownloadProgress(
                            workId = "w1",
                            filename = "active-download.gguf",
                            bytesRead = 200L,
                            totalBytes = 1000L,
                            progressPercentage = 20,
                            status = DownloadStatus.DOWNLOADING,
                        ),
                    ),
                )

                val updated = awaitItem()
                assertEquals(1, updated.activeDownloads.size)
                assertEquals("active-download.gguf", updated.activeDownloads[0].filename)
                assertEquals(20, updated.activeDownloads[0].progressPercentage)
            }
        }
}

private class FakeModelManagerRepository : ModelManagerRepository {
    private val downloadsFlow = MutableStateFlow<List<ModelDownloadProgress>>(emptyList())
    var lastEnqueuedFilename: String? = null
    var lastCancelledWorkId: String? = null
    private val localModels =
        mutableListOf(
            DownloadedModel(
                filename = "qwen-1.gguf",
                sizeBytes = 1024L,
                path = "/models/qwen-1.gguf",
            ),
        )

    fun emitDownloads(list: List<ModelDownloadProgress>) {
        downloadsFlow.value = list
    }

    override suspend fun searchRepos(query: String): Result<List<ModelRepoSummary>> =
        Result.success(
            if (query.contains("llama")) {
                listOf(
                    ModelRepoSummary(
                        id = "meta-llama/Llama-3.2-1B-Instruct-GGUF",
                        description = "Llama 3.2 1B",
                        downloads = 5000,
                        likes = 120,
                    ),
                )
            } else {
                listOf(
                    ModelRepoSummary(
                        id = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                        description = "Qwen 2.5 0.5B Instruct",
                        downloads = 10000,
                        likes = 350,
                    ),
                )
            },
        )

    override suspend fun listQuantFiles(repoId: String): Result<List<ModelFileInfo>> =
        Result.success(
            listOf(
                ModelFileInfo(
                    name = "qwen2.5-0.5b-q4_k_m.gguf",
                    size = 398_000_000L,
                    sha256 = "sha1",
                ),
                ModelFileInfo(
                    name = "qwen2.5-0.5b-q8_0.gguf",
                    size = 680_000_000L,
                    sha256 = "sha2",
                ),
            ),
        )

    override suspend fun enqueueDownload(
        repoId: String,
        filename: String,
        sha256: String,
        expectedSize: Long,
    ): Result<String> {
        lastEnqueuedFilename = filename
        return Result.success("work-id-123")
    }

    override fun observeDownloads(): Flow<List<ModelDownloadProgress>> = downloadsFlow

    override suspend fun cancelDownload(workId: String) {
        lastCancelledWorkId = workId
    }

    override suspend fun getDownloadedModels(): List<DownloadedModel> = localModels.toList()

    override suspend fun deleteModel(filename: String): Boolean = localModels.removeIf { it.filename == filename }

    override suspend fun getStorageStats(): ModelStorageStats =
        ModelStorageStats(
            totalUsedBytes = localModels.sumOf { it.sizeBytes },
            freeBytes = 50_000_000_000L,
            totalDeviceBytes = 128_000_000_000L,
        )
}
