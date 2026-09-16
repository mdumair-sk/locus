package com.locus.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locus.core.domain.notes.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repo: NoteRepository,
    ) : ViewModel() {
        val rootUri: StateFlow<String?> =
            repo
                .observeRootUri()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = null,
                )

        fun setRootFolder(uriString: String) {
            viewModelScope.launch { repo.setRootUri(uriString) }
        }

        private companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
