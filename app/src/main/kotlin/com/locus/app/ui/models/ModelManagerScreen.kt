package com.locus.app.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.locus.app.R
import com.locus.core.domain.models.DownloadStatus
import com.locus.core.domain.models.DownloadedModel
import com.locus.core.domain.models.ModelDownloadProgress
import com.locus.core.domain.models.ModelFileInfo
import com.locus.core.domain.models.ModelMeta
import com.locus.core.domain.models.ModelRepoSummary
import com.locus.core.domain.models.ModelStorageStats
import java.util.Locale

private const val MAX_RATING_STARS = 5

private const val ONE_KB = 1024L
private const val ONE_MB = 1024L * 1024L
private const val ONE_GB = 1024L * 1024L * 1024L

private data class ModelManagerActions(
    val onDeleteModelClick: (DownloadedModel) -> Unit,
    val onCancelDownload: (String) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onSearch: () -> Unit,
    val onSelectRepo: (ModelRepoSummary) -> Unit,
    val onBackToRepos: () -> Unit,
    val onStartDownload: (ModelFileInfo) -> Unit,
    val onUpdateNotesAndRating: (String, String, Int) -> Unit,
    val onBenchmarkModel: (DownloadedModel) -> Unit,
)

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes >= ONE_GB -> String.format(Locale.US, "%.2f GB", bytes.toDouble() / ONE_GB)
        bytes >= ONE_MB -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / ONE_MB)
        bytes >= ONE_KB -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / ONE_KB)
        else -> "$bytes B"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManagerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var modelPendingDelete by remember { mutableStateOf<DownloadedModel?>(null) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUserMessage()
        }
    }

    val actions =
        remember(viewModel) {
            ModelManagerActions(
                onDeleteModelClick = { modelPendingDelete = it },
                onCancelDownload = viewModel::cancelDownload,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSearch = { viewModel.searchRepos() },
                onSelectRepo = viewModel::selectRepo,
                onBackToRepos = viewModel::clearSelectedRepo,
                onStartDownload = { file ->
                    viewModel.startDownload(uiState.selectedRepo?.id.orEmpty(), file)
                },
                onUpdateNotesAndRating = viewModel::updateModelNotesAndRating,
                onBenchmarkModel = viewModel::benchmarkModel,
            )
        }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { paddingValues ->
        ModelManagerContent(
            uiState = uiState,
            actions = actions,
            modifier = Modifier.padding(paddingValues),
        )

        modelPendingDelete?.let { model ->
            DeleteModelDialog(
                model = model,
                onConfirm = {
                    viewModel.deleteModel(model)
                    modelPendingDelete = null
                },
                onDismiss = { modelPendingDelete = null },
            )
        }
    }
}

@Composable
private fun ModelManagerContent(
    uiState: ModelManagerUiState,
    actions: ModelManagerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StorageStatsCard(
            stats = uiState.storageStats,
            downloadedCount = uiState.downloadedModels.size,
        )

        DownloadedModelsSection(
            uiState = uiState,
            actions = actions,
        )

        if (uiState.activeDownloads.isNotEmpty()) {
            ActiveDownloadsSection(
                downloads = uiState.activeDownloads,
                onCancelDownload = actions.onCancelDownload,
            )
        }

        BrowseHuggingFaceSection(
            uiState = uiState,
            actions = actions,
        )
    }
}

@Composable
private fun StorageStatsCard(
    stats: ModelStorageStats,
    downloadedCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.models_storage_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text =
                            stringResource(
                                R.string.models_storage_used,
                                formatByteSize(stats.totalUsedBytes),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text =
                            "$downloadedCount local model${if (downloadedCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text =
                            stringResource(
                                R.string.models_storage_free,
                                formatByteSize(stats.freeBytes),
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "Total: ${formatByteSize(stats.totalDeviceBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedModelsSection(
    uiState: ModelManagerUiState,
    actions: ModelManagerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text =
                stringResource(R.string.models_downloaded_title) +
                    " (${uiState.downloadedModels.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (uiState.downloadedModels.isEmpty()) {
            Text(
                text = stringResource(R.string.models_downloaded_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            uiState.downloadedModels.forEach { model ->
                DownloadedModelRow(
                    model = model,
                    meta = uiState.modelMetaMap[model.filename],
                    isBenchmarking = uiState.benchmarkingModelId == model.filename,
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun DownloadedModelRow(
    model: DownloadedModel,
    meta: ModelMeta?,
    isBenchmarking: Boolean,
    actions: ModelManagerActions,
    modifier: Modifier = Modifier,
) {
    val currentRating = meta?.rating ?: 0
    val tokensPerSecond = meta?.tokensPerSecond ?: 0.0

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModelHeaderRow(
                model = model,
                onDelete = { actions.onDeleteModelClick(model) },
            )
            ModelRatingAndStatsRow(
                currentRating = currentRating,
                tokensPerSecond = tokensPerSecond,
                onSelectRating = { newRating ->
                    actions.onUpdateNotesAndRating(
                        model.filename,
                        meta?.notes.orEmpty(),
                        newRating,
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModelBenchmarkButton(
                    isBenchmarking = isBenchmarking,
                    onBenchmark = { actions.onBenchmarkModel(model) },
                )
            }
            ModelNotesField(
                savedNotes = meta?.notes.orEmpty(),
                onSaveNotes = { newNotes ->
                    actions.onUpdateNotesAndRating(model.filename, newNotes, currentRating)
                },
            )
        }
    }
}

@Composable
private fun ModelHeaderRow(
    model: DownloadedModel,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = model.filename,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatByteSize(model.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_note),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ModelRatingAndStatsRow(
    currentRating: Int,
    tokensPerSecond: Double,
    onSelectRating: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (star in 1..MAX_RATING_STARS) {
                IconButton(
                    onClick = {
                        val newRating = if (currentRating == star) 0 else star
                        onSelectRating(newRating)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.models_rating_label, star),
                        tint =
                            if (star <= currentRating) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.3f,
                                )
                            },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (tokensPerSecond > 0.0) {
            Text(
                text = stringResource(R.string.models_benchmark_result, tokensPerSecond),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ModelBenchmarkButton(
    isBenchmarking: Boolean,
    onBenchmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onBenchmark,
        enabled = !isBenchmarking,
        modifier = modifier,
    ) {
        if (isBenchmarking) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.models_benchmarking_button))
        } else {
            Text(stringResource(R.string.models_benchmark_button))
        }
    }
}

@Composable
private fun ModelNotesField(
    savedNotes: String,
    onSaveNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var notesText by remember(savedNotes) { mutableStateOf(savedNotes) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text(stringResource(R.string.models_notes_label)) },
            placeholder = { Text(stringResource(R.string.models_notes_placeholder)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Button(
            onClick = { onSaveNotes(notesText) },
            enabled = notesText != savedNotes,
        ) { Text(stringResource(R.string.models_save_notes)) }
    }
}

@Composable
private fun ActiveDownloadsSection(
    downloads: List<ModelDownloadProgress>,
    onCancelDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.models_downloads_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        downloads.forEach { download ->
            ActiveDownloadRow(download = download, onCancel = { onCancelDownload(download.workId) })
        }
    }
}

@Composable
private fun ActiveDownloadRow(
    download: ModelDownloadProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = download.filename.ifBlank { "Downloading model…" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.models_cancel_button))
                }
            }

            if (download.status == DownloadStatus.DOWNLOADING && download.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = {
                        (download.bytesRead.toFloat() / download.totalBytes.toFloat()).coerceIn(
                            0f,
                            1f,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (download.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val progressText =
                    if (download.totalBytes > 0L) {
                        "${formatByteSize(download.bytesRead)} / " +
                            "${formatByteSize(download.totalBytes)} (${download.progressPercentage}%)"
                    } else {
                        download.status.name
                    }
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                download.errorMessage?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseHuggingFaceSection(
    uiState: ModelManagerUiState,
    actions: ModelManagerActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.models_browse_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        if (uiState.selectedRepo == null) {
            RepoSearchBlock(uiState = uiState, actions = actions)
        } else {
            QuantPickerBlock(uiState = uiState, actions = actions)
        }
    }
}

@Composable
private fun RepoSearchBlock(
    uiState: ModelManagerUiState,
    actions: ModelManagerActions,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = actions.onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.models_search_placeholder)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = actions.onSearch) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.models_search_button),
            )
        }
    }

    if (uiState.isSearchingRepos) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }

    uiState.searchError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    uiState.searchResults.forEach { repo ->
        RepoSummaryCard(repo = repo, onClick = { actions.onSelectRepo(repo) })
    }
}

@Composable
private fun RepoSummaryCard(
    repo: ModelRepoSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = repo.id,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
            if (repo.description.isNotBlank()) {
                Text(
                    text = repo.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "⬇ ${repo.downloads}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "❤ ${repo.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuantPickerBlock(
    uiState: ModelManagerUiState,
    actions: ModelManagerActions,
) {
    val repo = uiState.selectedRepo ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = repo.id,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onBackToRepos) { Text("← Back") }
        }

        if (uiState.isLoadingQuants) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        uiState.quantsError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        uiState.quantFiles.forEach { file ->
            val isDownloaded =
                uiState.downloadedModels.any {
                    it.filename.equals(file.name, ignoreCase = true)
                }
            val isDownloading =
                uiState.activeDownloads.any {
                    it.filename.equals(file.name, ignoreCase = true) &&
                        it.status == DownloadStatus.DOWNLOADING
                }
            QuantFileRow(
                file = file,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onDownload = { actions.onStartDownload(file) },
            )
        }
    }
}

@Composable
private fun QuantFileRow(
    file: ModelFileInfo,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatByteSize(file.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isDownloaded -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.models_downloaded_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                isDownloading -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.models_downloading_button),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                else -> {
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.models_download_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteModelDialog(
    model: DownloadedModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.models_delete_confirm_title)) },
        text = { Text(stringResource(R.string.models_delete_confirm_msg, model.filename)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.models_delete_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.models_cancel_button))
            }
        },
    )
}
