package com.locus.app.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locus.core.domain.notes.Note
import com.locus.core.domain.notes.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashUiState(
    val trashedNotes: List<Note> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class TrashViewModel
    @Inject
    constructor(
        private val repo: NoteRepository,
    ) : ViewModel() {
        val uiState: StateFlow<TrashUiState> =
            repo
                .observeTrash()
                .map { notes -> TrashUiState(trashedNotes = notes, loading = false) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = TrashUiState(loading = true),
                )

        fun restoreNote(noteId: String) {
            viewModelScope.launch { repo.restoreNote(noteId) }
        }

        private companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
