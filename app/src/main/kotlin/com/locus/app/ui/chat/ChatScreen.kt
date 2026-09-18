package com.locus.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.locus.app.R
import com.locus.core.domain.chat.ChatMessage
import com.locus.core.domain.chat.ChatRole
import com.locus.core.domain.chat.CitedSource
import kotlinx.coroutines.launch

private const val CORNER_RADIUS = 16
private const val SMALL_CORNER_RADIUS = 4

@Suppress("LongMethod")
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSessionSheet by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noteCreatedTemplate = stringResource(R.string.chat_pinned_success)
    val openLabel = stringResource(R.string.chat_view_note)

    val activeSessionName =
        uiState.sessions.firstOrNull { it.id == uiState.activeSessionId }?.name
            ?: stringResource(R.string.chat_title)

    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ChatTopBar(
                title = activeSessionName,
                activeModel = uiState.activeModel,
                actions =
                    ChatTopBarActions(
                        onNavigateBack = onNavigateBack,
                        onOpenSessions = { showSessionSheet = true },
                        onNewSession = { viewModel.createNewSession() },
                        onModelClick = { showModelPicker = true },
                    ),
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = inputText,
                onInputTextChange = { inputText = it },
                onSend = {
                    val messageToSend = inputText
                    inputText = ""
                    viewModel.sendMessage(messageToSend)
                },
                isStreaming = uiState.streamingText != null,
            )
        },
    ) { innerPadding ->
        MessageList(
            messages = uiState.messages,
            streamingText = uiState.streamingText,
            onSourceClick = onNavigateToEditor,
            onPinAsNote = { message ->
                viewModel.pinAsNote(message) { createdNote ->
                    scope.launch {
                        val result =
                            snackbarHostState.showSnackbar(
                                message =
                                    String.format(
                                        noteCreatedTemplate,
                                        createdNote.title,
                                    ),
                                actionLabel = openLabel,
                            )
                        if (result == SnackbarResult.ActionPerformed) {
                            onNavigateToEditor(createdNote.id)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    if (showSessionSheet) {
        SessionListSheet(
            sessions = uiState.sessions,
            activeSessionId = uiState.activeSessionId,
            actions =
                SessionListSheetActions(
                    onSelectSession = { viewModel.selectSession(it) },
                    onCreateNewSession = { viewModel.createNewSession() },
                    onDismiss = { showSessionSheet = false },
                ),
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            currentModel = uiState.activeModel,
            onSelectModel = { model ->
                viewModel.selectActiveModel(model)
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false },
        )
    }
}

private data class ChatTopBarActions(
    val onNavigateBack: () -> Unit,
    val onOpenSessions: () -> Unit,
    val onNewSession: () -> Unit,
    val onModelClick: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    activeModel: com.locus.core.domain.chat.ActiveModelInfo,
    actions: ChatTopBarActions,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(2.dp))
                ActiveModelIndicator(
                    activeModel = activeModel,
                    onClick = actions.onModelClick,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = actions.onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        },
        actions = {
            IconButton(onClick = actions.onOpenSessions) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.chat_sessions),
                )
            }
            IconButton(onClick = actions.onNewSession) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.chat_new_session),
                )
            }
        },
    )
}

@Composable
private fun ModelPickerDialog(
    currentModel: com.locus.core.domain.chat.ActiveModelInfo,
    onSelectModel: (com.locus.core.domain.chat.ActiveModelInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val localModel =
        remember {
            com.locus.core.domain.chat.ActiveModelInfo(
                name = "Qwen3-4B",
                tier = com.locus.core.domain.chat.ModelTier.LOCAL,
                contextLength = 32_768,
            )
        }
    val cloudModel =
        remember {
            com.locus.core.domain.chat.ActiveModelInfo(
                name = "gemini-3.5-flash-lite",
                tier = com.locus.core.domain.chat.ModelTier.CLOUD,
                contextLength = 128_000,
            )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.active_model_desc, "Model", "Selection"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ModelOptionItem(
                    model = localModel,
                    badge = "Local • Offline (Zero Network)",
                    isSelected = currentModel.isLocal,
                    onClick = { onSelectModel(localModel) },
                )
                HorizontalDivider()
                ModelOptionItem(
                    model = cloudModel,
                    badge = "Cloud • Gemini API",
                    isSelected = currentModel.isCloud,
                    onClick = { onSelectModel(cloudModel) },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ModelOptionItem(
    model: com.locus.core.domain.chat.ActiveModelInfo,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color =
            if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isSelected) {
                Text(
                    text = "Active",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    streamingText: String?,
    onSourceClick: (String) -> Unit,
    onPinAsNote: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, streamingText) {
        val totalCount = messages.size + if (streamingText != null) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    if (messages.isEmpty() && streamingText == null) {
        EmptyChatPlaceholder(modifier = modifier)
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.padding(horizontal = 8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                when (message.role) {
                    ChatRole.USER -> UserMessageBubble(message = message)
                    ChatRole.ASSISTANT ->
                        AssistantMessageBubble(
                            message = message,
                            onSourceClick = onSourceClick,
                            onPinAsNote = { onPinAsNote(message) },
                        )
                    ChatRole.SYSTEM -> {}
                }
            }
            if (streamingText != null) {
                item(key = "streaming_bubble") { StreamingMessageBubble(text = streamingText) }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape =
                RoundedCornerShape(
                    topStart = CORNER_RADIUS.dp,
                    topEnd = SMALL_CORNER_RADIUS.dp,
                    bottomStart = CORNER_RADIUS.dp,
                    bottomEnd = CORNER_RADIUS.dp,
                ),
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: ChatMessage,
    onSourceClick: (String) -> Unit,
    onPinAsNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape =
                RoundedCornerShape(
                    topStart = SMALL_CORNER_RADIUS.dp,
                    topEnd = CORNER_RADIUS.dp,
                    bottomStart = CORNER_RADIUS.dp,
                    bottomEnd = CORNER_RADIUS.dp,
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.citations.isNotEmpty()) {
                    CitationsSection(
                        citations = message.citations,
                        onSourceClick = onSourceClick,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onPinAsNote) {
                        Icon(
                            painter = painterResource(R.drawable.ic_pin),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.chat_pin_as_note),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CitationsSection(
    citations: List<CitedSource>,
    onSourceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = stringResource(R.string.chat_sources),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        citations.forEach { source ->
            val title = source.noteTitle.ifBlank { stringResource(R.string.note_title_placeholder) }
            Text(
                text = "• $title",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier =
                    Modifier
                        .clickable { onSourceClick(source.noteId) }
                        .padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun StreamingMessageBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape =
                RoundedCornerShape(
                    topStart = SMALL_CORNER_RADIUS.dp,
                    topEnd = CORNER_RADIUS.dp,
                    bottomStart = CORNER_RADIUS.dp,
                    bottomEnd = CORNER_RADIUS.dp,
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (text.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = inputText.isNotBlank() && !isStreaming,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_send),
                    contentDescription = stringResource(R.string.chat_send),
                    tint =
                        if (inputText.isNotBlank() && !isStreaming) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                )
            }
        }
    }
}

@Composable
private fun EmptyChatPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_chat),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.chat_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
