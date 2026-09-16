package com.locus.app.ui.grid

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

data class GridUiState(
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class GridViewModel
    @Inject
    constructor(
        private val repo: NoteRepository,
    ) : ViewModel() {
        val uiState: StateFlow<GridUiState> =
            repo
                .observeAllNotes()
                .map { notes -> GridUiState(notes = notes, loading = false) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = GridUiState(loading = true),
                )

        fun setPinned(
            noteId: String,
            pinned: Boolean,
        ) {
            viewModelScope.launch {
                repo.setPinned(noteId, pinned)
            }
        }

        fun setColor(
            noteId: String,
            color: String?,
        ) {
            viewModelScope.launch {
                repo.setColor(noteId, color)
            }
        }

        private companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
