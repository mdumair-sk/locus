package com.locus.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.search.KeywordSearch
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.time.DispatcherProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
)

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val keywordSearch: KeywordSearch,
        private val noteRepository: NoteRepository,
        private val dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val queryFlow = MutableStateFlow("")
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                @OptIn(FlowPreview::class)
                queryFlow.debounce(DEBOUNCE_MILLIS).distinctUntilChanged().collectLatest { query ->
                    if (query.isBlank()) {
                        _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                    } else {
                        _uiState.update { it.copy(isSearching = true) }
                        try {
                            val results = withContext(dispatchers.io) { keywordSearch.search(query) }
                            _uiState.update { it.copy(results = results, isSearching = false) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            _uiState.update { it.copy(results = emptyList(), isSearching = false) }
                        }
                    }
                }
            }
        }

        fun onQueryChange(newQuery: String) {
            queryFlow.value = newQuery
            _uiState.update { it.copy(query = newQuery) }
            if (newQuery.isBlank()) {
                _uiState.update { it.copy(results = emptyList(), isSearching = false) }
            }
        }

        fun clearQuery() {
            onQueryChange("")
        }

        fun rescan() {
            viewModelScope.launch(dispatchers.io) {
                _uiState.update { it.copy(isSearching = true) }
                try {
                    noteRepository.rescan()
                    val currentQuery = queryFlow.value
                    if (currentQuery.isNotBlank()) {
                        val results = keywordSearch.search(currentQuery)
                        _uiState.update { it.copy(results = results) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Rescan failure is non-fatal to search UI
                } finally {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }

        private companion object {
            private const val DEBOUNCE_MILLIS = 300L
        }
    }
