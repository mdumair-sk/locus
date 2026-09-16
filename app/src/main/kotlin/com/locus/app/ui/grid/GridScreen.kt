package com.locus.app.ui.grid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.locus.app.R
import com.locus.app.theme.KeepNoteColorSwatches
import com.locus.app.theme.resolveNoteColor
import com.locus.core.domain.notes.Note

@Composable
fun GridScreen(
    onNavigateToEditor: (noteId: String) -> Unit,
    onNavigateToSearch: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GridViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    GridContent(
        uiState = uiState,
        onNavigateToEditor = onNavigateToEditor,
        onNavigateToSearch = onNavigateToSearch,
        onTogglePin = viewModel::setPinned,
        onSetColor = viewModel::setColor,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridContent(
    uiState: GridUiState,
    onNavigateToEditor: (noteId: String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onTogglePin: (noteId: String, pinned: Boolean) -> Unit,
    onSetColor: (noteId: String, color: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var noteForColorPicker by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.app_name))
                },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.nav_search),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToEditor("new") },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.new_note),
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            when {
                uiState.loading && uiState.notes.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.notes.isEmpty() -> {
                    EmptyNotesState(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    val pinnedNotes = remember(uiState.notes) { uiState.notes.filter { it.pinned } }
                    val otherNotes = remember(uiState.notes) { uiState.notes.filter { !it.pinned } }

                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                    ) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SectionHeader(title = stringResource(R.string.section_pinned))
                            }
                            items(pinnedNotes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = { onNavigateToEditor(note.id) },
                                    onLongClick = { noteForColorPicker = note },
                                    onTogglePin = { onTogglePin(note.id, !note.pinned) },
                                )
                            }
                            if (otherNotes.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    SectionHeader(title = stringResource(R.string.section_others))
                                }
                                items(otherNotes, key = { it.id }) { note ->
                                    NoteCard(
                                        note = note,
                                        onClick = { onNavigateToEditor(note.id) },
                                        onLongClick = { noteForColorPicker = note },
                                        onTogglePin = { onTogglePin(note.id, !note.pinned) },
                                    )
                                }
                            }
                        } else {
                            items(uiState.notes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = { onNavigateToEditor(note.id) },
                                    onLongClick = { noteForColorPicker = note },
                                    onTogglePin = { onTogglePin(note.id, !note.pinned) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    noteForColorPicker?.let { note ->
        ColorPickerDialog(
            currentColor = note.color,
            onColorSelected = { selectedColor ->
                onSetColor(note.id, selectedColor)
                noteForColorPicker = null
            },
            onDismissRequest = {
                noteForColorPicker = null
            },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = resolveNoteColor(note.color, isDark) ?: MaterialTheme.colorScheme.surfaceVariant
    val outlineColor =
        if (note.color != null) {
            containerColor
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, outlineColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = note.title.ifBlank { stringResource(R.string.untitled_note) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (note.pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin,
                            ),
                        contentDescription =
                            stringResource(
                                if (note.pinned) R.string.unpin_note else R.string.pin_note,
                            ),
                        modifier = Modifier.size(18.dp),
                        tint =
                            if (note.pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                    )
                }
            }

            if (note.folderPath.isNotBlank()) {
                Text(
                    text = note.folderPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (note.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    note.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    currentColor: String?,
    onColorSelected: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(R.string.color_picker_title))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Row 1: Default (none) + Coral (#F28B82) + Peach (#FBBC04)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ColorSwatchCircle(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        name = stringResource(R.string.color_default),
                        isSelected = currentColor == null,
                        onClick = { onColorSelected(null) },
                    )
                    KeepNoteColorSwatches.take(2).forEach { swatch ->
                        ColorSwatchCircle(
                            color = if (isDark) swatch.darkColor else swatch.lightColor,
                            name = swatch.name,
                            isSelected = swatch.hex.equals(currentColor, ignoreCase = true),
                            onClick = { onColorSelected(swatch.hex) },
                        )
                    }
                }

                // Row 2: Sand (#FFF475) + Mint (#CCFF90) + Sage (#A7FFEB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    KeepNoteColorSwatches.drop(2).take(3).forEach { swatch ->
                        ColorSwatchCircle(
                            color = if (isDark) swatch.darkColor else swatch.lightColor,
                            name = swatch.name,
                            isSelected = swatch.hex.equals(currentColor, ignoreCase = true),
                            onClick = { onColorSelected(swatch.hex) },
                        )
                    }
                }

                // Row 3: Fog (#CBF0F8) + Dusk (#D7AEFB) + Blossom (#FDCFE8)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    KeepNoteColorSwatches.drop(5).take(3).forEach { swatch ->
                        ColorSwatchCircle(
                            color = if (isDark) swatch.darkColor else swatch.lightColor,
                            name = swatch.name,
                            isSelected = swatch.hex.equals(currentColor, ignoreCase = true),
                            onClick = { onColorSelected(swatch.hex) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun ColorSwatchCircle(
    color: Color,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun EmptyNotesState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_notes_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.empty_notes_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
