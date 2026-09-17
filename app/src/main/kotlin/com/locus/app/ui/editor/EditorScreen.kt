package com.locus.app.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locus.app.R

@Composable
fun EditorScreen(
    noteId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, noteId) {
        viewModel.loadNote(noteId)
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.onStop()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onDispose()
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    var showHistorySheet by remember { mutableStateOf(false) }

    val actions =
        EditorActions(
            onNavigateBack = onNavigateBack,
            onTogglePreview = { viewModel.togglePreview() },
            onBodyChange = { viewModel.onBodyChange(it) },
            onTitleChange = { viewModel.onTitleChange(it) },
            onDeleteNote = { viewModel.deleteNote(onDeleted = onNavigateBack) },
            onOpenHistory = { showHistorySheet = true },
        )
    EditorContent(
        uiState = uiState,
        actions = actions,
        modifier = modifier,
    )

    if (showHistorySheet) {
        HistorySheet(
            noteId = noteId,
            onDismiss = { showHistorySheet = false },
            onRestored = {
                showHistorySheet = false
                viewModel.loadNote(noteId)
            },
        )
    }
}

private data class EditorActions(
    val onNavigateBack: () -> Unit,
    val onTogglePreview: () -> Unit,
    val onBodyChange: (String) -> Unit,
    val onTitleChange: (String) -> Unit,
    val onDeleteNote: () -> Unit,
    val onOpenHistory: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    uiState: EditorUiState,
    actions: EditorActions,
    modifier: Modifier = Modifier,
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.body)) }
    var titleFieldValue by remember { mutableStateOf(TextFieldValue(uiState.title)) }

    LaunchedEffect(uiState.body) {
        if (textFieldValue.text != uiState.body) {
            textFieldValue = textFieldValue.copy(text = uiState.body)
        }
    }

    LaunchedEffect(uiState.title) {
        if (titleFieldValue.text != uiState.title) {
            titleFieldValue = titleFieldValue.copy(text = uiState.title)
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                isPreview = uiState.isPreview,
                onNavigateBack = actions.onNavigateBack,
                onTogglePreview = actions.onTogglePreview,
                onDeleteNote = actions.onDeleteNote,
                onOpenHistory = actions.onOpenHistory,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            if (uiState.isPreview) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (uiState.title.isNotBlank()) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        HorizontalDivider()
                    }
                    MarkdownPreview(
                        body = uiState.body,
                        onCheckboxToggle = { lineIndex ->
                            val updated = toggleCheckboxAtLine(uiState.body, lineIndex)
                            textFieldValue = textFieldValue.copy(text = updated)
                            actions.onBodyChange(updated)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                SourceEditorColumn(
                    titleFieldValue = titleFieldValue,
                    onTitleChange = { newTitleValue ->
                        titleFieldValue = newTitleValue
                        actions.onTitleChange(newTitleValue.text)
                    },
                    textFieldValue = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        actions.onBodyChange(newValue.text)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorTopBar(
    isPreview: Boolean,
    onNavigateBack: () -> Unit,
    onTogglePreview: () -> Unit,
    onDeleteNote: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            TextButton(onClick = onTogglePreview) {
                Text(
                    text =
                        if (isPreview) {
                            stringResource(R.string.editor_edit)
                        } else {
                            stringResource(R.string.editor_preview)
                        },
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onDeleteNote) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_note),
                )
            }
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.editor_more_options),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.editor_version_history)) },
                        onClick = {
                            menuExpanded = false
                            onOpenHistory()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun SourceEditorColumn(
    titleFieldValue: TextFieldValue,
    onTitleChange: (TextFieldValue) -> Unit,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TitleInputField(
            titleFieldValue = titleFieldValue,
            onTitleChange = onTitleChange,
        )
        HorizontalDivider()
        FormattingToolbar(
            actions =
                EditorToolbarActions(
                    onBold = { onValueChange(applyBold(textFieldValue)) },
                    onItalic = { onValueChange(applyItalic(textFieldValue)) },
                    onHeading = { onValueChange(applyHeading(textFieldValue)) },
                    onList = { onValueChange(applyList(textFieldValue)) },
                    onCheckbox = { onValueChange(applyCheckbox(textFieldValue)) },
                ),
        )
        HorizontalDivider()
        SourceEditorField(
            textFieldValue = textFieldValue,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TitleInputField(
    titleFieldValue: TextFieldValue,
    onTitleChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (titleFieldValue.text.isEmpty()) {
            Text(
                text = stringResource(R.string.note_title_placeholder),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = titleFieldValue,
            onValueChange = onTitleChange,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class EditorToolbarActions(
    val onBold: () -> Unit,
    val onItalic: () -> Unit,
    val onHeading: () -> Unit,
    val onList: () -> Unit,
    val onCheckbox: () -> Unit,
)

@Composable
private fun FormattingToolbar(
    actions: EditorToolbarActions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        ToolbarAction(label = "B", fontWeight = FontWeight.Bold, onClick = actions.onBold)
        ToolbarAction(label = "I", fontStyle = FontStyle.Italic, onClick = actions.onItalic)
        ToolbarAction(label = "H", fontWeight = FontWeight.Bold, onClick = actions.onHeading)
        ToolbarAction(label = "•-", fontWeight = FontWeight.Bold, onClick = actions.onList)
        ToolbarAction(label = "[✓]", fontWeight = FontWeight.Bold, onClick = actions.onCheckbox)
    }
}

@Composable
private fun ToolbarAction(
    label: String,
    onClick: () -> Unit,
    fontWeight: FontWeight = FontWeight.Normal,
    fontStyle: FontStyle = FontStyle.Normal,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = label,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
        )
    }
}

@Composable
private fun SourceEditorField(
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            modifier =
                Modifier.fillMaxSize().pointerInput(textFieldValue.text) {
                    awaitEachGesture {
                        awaitFirstDown(pass = PointerEventPass.Initial)
                        val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        if (up != null) {
                            val layout = textLayoutResult
                            if (layout != null) {
                                val charIndex = layout.getOffsetForPosition(up.position)
                                val updated =
                                    toggleCheckboxAtCharIndex(
                                        textFieldValue.text,
                                        charIndex,
                                    )
                                if (updated != null) {
                                    up.consume()
                                    onValueChange(textFieldValue.copy(text = updated))
                                }
                            }
                        }
                    }
                },
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = MarkdownVisualTransformation(),
            onTextLayout = { result -> textLayoutResult = result },
            decorationBox = { innerTextField ->
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.editor_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.6f,
                            ),
                    )
                }
                innerTextField()
            },
        )
    }
}
