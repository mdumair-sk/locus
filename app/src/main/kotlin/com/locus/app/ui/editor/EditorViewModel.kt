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
            viewModelScope.launch(dispatchers.io) {
                val initialBody = runCatching { repo.readBody(id) }.getOrDefault("")
                _uiState.update { it.copy(body = initialBody) }
            }
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

        fun onBodyChange(newBody: String) {
            _uiState.update { it.copy(body = newBody) }
            val id = currentNoteId
            if (id.isNotEmpty()) {
                viewModelScope.launch(dispatchers.io) { repo.edit(id, newBody) }
            }
        }

        fun togglePreview() {
            _uiState.update { it.copy(isPreview = !it.isPreview) }
        }

        fun onDispose() {
            val id = currentNoteId
            if (id.isNotEmpty()) {
                flushScope.launch { repo.forceFlush(id, FlushTrigger.EDITOR_CLOSE) }
            }
        }

        fun onStop() {
            val id = currentNoteId
            if (id.isNotEmpty()) {
                flushScope.launch { repo.forceFlush(id, FlushTrigger.ON_STOP) }
            }
        }

        override fun onCleared() {
            super.onCleared()
            onDispose()
        }
    }
