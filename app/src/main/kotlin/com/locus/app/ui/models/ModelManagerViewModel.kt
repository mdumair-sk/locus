package com.locus.app.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locus.core.domain.models.DownloadStatus
import com.locus.core.domain.models.DownloadedModel
import com.locus.core.domain.models.ModelDownloadProgress
import com.locus.core.domain.models.ModelFileInfo
import com.locus.core.domain.models.ModelManagerRepository
import com.locus.core.domain.models.ModelMeta
import com.locus.core.domain.models.ModelRepoSummary
import com.locus.core.domain.models.ModelStorageStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelManagerUiState(
    val storageStats: ModelStorageStats = ModelStorageStats(0L, 0L, 0L),
    val downloadedModels: List<DownloadedModel> = emptyList(),
    val activeDownloads: List<ModelDownloadProgress> = emptyList(),
    val searchQuery: String = "qwen",
    val isSearchingRepos: Boolean = false,
    val searchResults: List<ModelRepoSummary> = emptyList(),
    val searchError: String? = null,
    val selectedRepo: ModelRepoSummary? = null,
    val isLoadingQuants: Boolean = false,
    val quantFiles: List<ModelFileInfo> = emptyList(),
    val quantsError: String? = null,
    val modelMetaMap: Map<String, ModelMeta> = emptyMap(),
    val benchmarkingModelId: String? = null,
    val userMessage: String? = null,
)

@HiltViewModel
class ModelManagerViewModel
    @Inject
    constructor(
        private val repository: ModelManagerRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ModelManagerUiState())
        val uiState: StateFlow<ModelManagerUiState> = _uiState.asStateFlow()
        private val dismissedDownloadWorkIds = MutableStateFlow<Set<String>>(emptySet())

        private var searchJob: Job? = null
        private var quantsJob: Job? = null

        init {
            refreshStorageAndModels()
            observeDownloads()
            observeModelMeta()
            searchRepos("qwen")
        }

        fun refreshStorageAndModels() {
            viewModelScope.launch {
                val stats = repository.getStorageStats()
                val models = repository.getDownloadedModels()
                _uiState.update {
                    it.copy(
                        storageStats = stats,
                        downloadedModels = models,
                    )
                }
            }
        }

        private fun observeDownloads() {
            viewModelScope.launch {
                combine(
                    repository.observeDownloads(),
                    dismissedDownloadWorkIds,
                ) { downloads, dismissedIds -> downloads.filter { it.workId !in dismissedIds } }
                    .collect { downloads ->
                        val hadCompleted = downloads.any { it.status == DownloadStatus.COMPLETED }
                        _uiState.update { it.copy(activeDownloads = downloads) }
                        if (hadCompleted) {
                            refreshStorageAndModels()
                        }
                    }
            }
        }

        private fun observeModelMeta() {
            viewModelScope.launch {
                repository.observeAllModelMeta().collect { metaList ->
                    _uiState.update { state ->
                        state.copy(modelMetaMap = metaList.associateBy { it.modelId })
                    }
                }
            }
        }

        fun onSearchQueryChange(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
        }

        fun searchRepos(queryOverride: String? = null) {
            val query = (queryOverride ?: _uiState.value.searchQuery).trim()
            searchJob?.cancel()
            searchJob =
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            isSearchingRepos = true,
                            searchError = null,
                        )
                    }
                    repository
                        .searchRepos(query)
                        .onSuccess { repos ->
                            _uiState.update {
                                it.copy(
                                    isSearchingRepos = false,
                                    searchResults = repos,
                                    searchError = null,
                                )
                            }
                        }.onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isSearchingRepos = false,
                                    searchError =
                                        error.message
                                            ?: "Failed to search repositories",
                                )
                            }
                        }
                }
        }

        fun selectRepo(repo: ModelRepoSummary) {
            quantsJob?.cancel()
            quantsJob =
                viewModelScope.launch {
                    _uiState.update {
                        it.copy(
                            selectedRepo = repo,
                            isLoadingQuants = true,
                            quantFiles = emptyList(),
                            quantsError = null,
                        )
                    }
                    repository
                        .listQuantFiles(repo.id)
                        .onSuccess { files ->
                            _uiState.update {
                                it.copy(
                                    isLoadingQuants = false,
                                    quantFiles = files,
                                    quantsError = null,
                                )
                            }
                        }.onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isLoadingQuants = false,
                                    quantsError =
                                        error.message
                                            ?: "Failed to list quant files",
                                )
                            }
                        }
                }
        }

        fun clearSelectedRepo() {
            quantsJob?.cancel()
            _uiState.update {
                it.copy(
                    selectedRepo = null,
                    isLoadingQuants = false,
                    quantFiles = emptyList(),
                    quantsError = null,
                )
            }
        }

        fun startDownload(
            repoId: String,
            file: ModelFileInfo,
        ) {
            viewModelScope.launch {
                repository
                    .enqueueDownload(
                        repoId = repoId,
                        filename = file.name,
                        sha256 = file.sha256,
                        expectedSize = file.size,
                    ).onSuccess {
                        _uiState.update { state ->
                            state.copy(userMessage = "Started downloading ${file.name}")
                        }
                    }.onFailure { error ->
                        _uiState.update { state ->
                            state.copy(userMessage = "Download failed to enqueue: ${error.message}")
                        }
                    }
            }
        }

        fun cancelDownload(workId: String) {
            viewModelScope.launch {
                repository.cancelDownload(workId)
                refreshStorageAndModels()
                _uiState.update { state -> state.copy(userMessage = "Download cancelled") }
            }
        }

        fun removeDownload(
            workId: String,
            filename: String,
        ) {
            dismissedDownloadWorkIds.update { it + workId }
            viewModelScope.launch {
                repository.removeDownload(workId, filename)
                refreshStorageAndModels()
                _uiState.update { state ->
                    val label = filename.ifBlank { "Download" }
                    state.copy(userMessage = "Removed $label from queue")
                }
            }
        }

        fun deleteModel(model: DownloadedModel) {
            viewModelScope.launch {
                val deleted = repository.deleteModel(model.filename)
                if (deleted) {
                    refreshStorageAndModels()
                    _uiState.update { state -> state.copy(userMessage = "Deleted ${model.filename}") }
                } else {
                    _uiState.update { state ->
                        state.copy(userMessage = "Failed to delete ${model.filename}")
                    }
                }
            }
        }

        fun updateModelNotesAndRating(
            modelId: String,
            notes: String,
            rating: Int,
        ) {
            viewModelScope.launch { repository.saveModelNotesAndRating(modelId, notes, rating) }
        }

        fun benchmarkModel(model: DownloadedModel) {
            if (_uiState.value.benchmarkingModelId != null) return
            _uiState.update { it.copy(benchmarkingModelId = model.filename) }
            viewModelScope.launch {
                val result = repository.runBenchmark(model.filename, model.path)
                _uiState.update { state ->
                    state.copy(
                        benchmarkingModelId = null,
                        userMessage =
                            result.fold(
                                onSuccess = { bench ->
                                    "Benchmark completed: ${String.format(
                                        java.util.Locale.US,
                                        "%.1f",
                                        bench.tokensPerSecond,
                                    )} tok/s"
                                },
                                onFailure = { e ->
                                    "Benchmark failed: ${e.message ?: "Unknown error"}"
                                },
                            ),
                    )
                }
            }
        }

        fun clearUserMessage() {
            _uiState.update { it.copy(userMessage = null) }
        }
    }
