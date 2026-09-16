package com.locus.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locus.app.navigation.LocusDestinations
import com.locus.core.domain.notes.FlushTrigger
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.notes.NoteType
import com.locus.core.domain.time.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class EditorUiState(
    val body: String = "",
    val title: String = "",
    val isPreview: Boolean = false,
    val type: NoteType = NoteType.NOTE,
)

@HiltViewModel
class EditorViewModel
    @Inject
    constructor(
        private val repo: NoteRepository,
        private val dispatchers: DispatcherProvider,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) : ViewModel() {
        private val flushScope = CoroutineScope(SupervisorJob() + dispatchers.io)
        private var currentNoteId: String =
            savedStateHandle.get<String>(LocusDestinations.NOTE_ID_ARG).orEmpty()
        private val editMutex = Mutex()
        private var isCustomTitle: Boolean = false
        private var observeNotesJob: Job? = null

        private val _uiState = MutableStateFlow(EditorUiState())
        val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

        init {
            if (currentNoteId.isNotEmpty()) {
                loadNote(currentNoteId)
            }
        }

        fun loadNote(id: String) {
            currentNoteId = id
            if (id.isEmpty() || id == "new") {
                isCustomTitle = false
                _uiState.update { it.copy(body = "", title = "") }
                return
            }
            isCustomTitle = true
            viewModelScope.launch(dispatchers.io) {
                val initialBody = runCatching { repo.readBody(id) }.getOrDefault("")
                _uiState.update { it.copy(body = initialBody) }
            }
            startObservingNote(id)
        }

        private fun startObservingNote(id: String) {
            observeNotesJob?.cancel()
            observeNotesJob =
                viewModelScope.launch(dispatchers.io) {
                    repo.observeAllNotes().collect { notes ->
                        val note = notes.find { it.id == id }
                        if (note != null) {
                            _uiState.update { it.copy(title = note.title, type = note.type) }
                        }
                    }
                }
        }

        fun onTitleChange(newTitle: String) {
            isCustomTitle = true
            _uiState.update { it.copy(title = newTitle) }
            viewModelScope.launch(dispatchers.io) {
                editMutex.withLock {
                    if (currentNoteId.isNotEmpty() && currentNoteId != "new") {
                        repo.setTitle(currentNoteId, newTitle)
                    } else if (newTitle.isNotBlank()) {
                        val noteType =
                            if (_uiState.value.body.contains("- [ ]") ||
                                _uiState.value.body.contains("- [x]")
                            ) {
                                NoteType.CHECKLIST
                            } else {
                                NoteType.NOTE
                            }
                        val created =
                            repo.createNote(
                                folderPath = "",
                                title = newTitle,
                                type = noteType,
                            )
                        currentNoteId = created.id
                        startObservingNote(created.id)
                        if (_uiState.value.body.isNotEmpty()) {
                            repo.edit(created.id, _uiState.value.body)
                        }
                    }
                }
            }
        }

        fun onBodyChange(newBody: String) {
            val previousTitle = _uiState.value.title
            val derivedTitle = if (!isCustomTitle) deriveTitle(newBody) else previousTitle
            _uiState.update { current -> current.copy(body = newBody, title = derivedTitle) }
            viewModelScope.launch(dispatchers.io) {
                editMutex.withLock {
                    if (currentNoteId == "new" || currentNoteId.isEmpty()) {
                        val titleToUse =
                            if (isCustomTitle && previousTitle.isNotBlank()) {
                                previousTitle
                            } else {
                                derivedTitle
                            }
                        val noteType =
                            if (newBody.contains("- [ ]") || newBody.contains("- [x]")) {
                                NoteType.CHECKLIST
                            } else {
                                NoteType.NOTE
                            }
                        val created =
                            repo.createNote(
                                folderPath = "",
                                title = titleToUse,
                                type = noteType,
                            )
                        currentNoteId = created.id
                        startObservingNote(created.id)
                        if (newBody.isNotEmpty()) {
                            repo.edit(created.id, newBody)
                        }
                    } else {
                        repo.edit(currentNoteId, newBody)
                        if (!isCustomTitle &&
                            derivedTitle != previousTitle &&
                            derivedTitle != "Untitled"
                        ) {
                            repo.setTitle(currentNoteId, derivedTitle)
                        }
                    }
                }
            }
        }

        fun togglePreview() {
            _uiState.update { it.copy(isPreview = !it.isPreview) }
        }

        fun deleteNote(onDeleted: () -> Unit = {}) {
            viewModelScope.launch {
                val id = currentNoteId
                if (id.isNotEmpty() && id != "new") {
                    repo.deleteNote(id)
                    onDeleted()
                }
            }
        }

        fun onDispose() {
            flushScope.launch {
                editMutex.withLock {
                    val id = currentNoteId
                    if (id.isNotEmpty() && id != "new") {
                        runCatching { repo.forceFlush(id, FlushTrigger.EDITOR_CLOSE) }
                    }
                }
            }
        }

        fun onStop() {
            flushScope.launch {
                editMutex.withLock {
                    val id = currentNoteId
                    if (id.isNotEmpty() && id != "new") {
                        runCatching { repo.forceFlush(id, FlushTrigger.ON_STOP) }
                    }
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            onDispose()
        }
    }
