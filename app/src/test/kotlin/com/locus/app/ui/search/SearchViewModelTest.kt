package com.locus.app.ui.search

import com.locus.core.domain.notes.Note
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.notes.NoteType
import com.locus.core.domain.notes.RescanReport
import com.locus.core.domain.search.KeywordSearch
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.search.SearchScope
import com.locus.core.domain.time.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testDispatchers: TestDispatcherProvider
    private lateinit var fakeSearch: FakeKeywordSearch
    private lateinit var fakeRepo: FakeNoteRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testDispatchers = TestDispatcherProvider(testDispatcher)
        fakeSearch = FakeKeywordSearch()
        fakeRepo = FakeNoteRepository()
        viewModel = SearchViewModel(fakeSearch, fakeRepo, testDispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasEmptyQueryAndResults() {
        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun onQueryChange_updatesQueryImmediatelyAndSearchesAfterDebounce() =
        runTest {
            viewModel.onQueryChange("meeting")
            assertEquals("meeting", viewModel.uiState.value.query)

            // Before debounce elapses (at 200ms)
            testDispatcher.scheduler.advanceTimeBy(200)
            assertEquals(0, fakeSearch.searchCount)

            // After debounce elapses (at 300ms total)
            testDispatcher.scheduler.advanceTimeBy(150)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, fakeSearch.searchCount)
            assertEquals("meeting", fakeSearch.lastQuery)
            assertEquals(1, viewModel.uiState.value.results.size)
            assertEquals(
                "meeting-note",
                viewModel.uiState.value.results[0]
                    .noteId,
            )
            assertFalse(viewModel.uiState.value.isSearching)
        }

    @Test
    fun onQueryChange_debouncesRapidKeystrokes() =
        runTest {
            viewModel.onQueryChange("m")
            testDispatcher.scheduler.advanceTimeBy(100)
            viewModel.onQueryChange("me")
            testDispatcher.scheduler.advanceTimeBy(100)
            viewModel.onQueryChange("mee")
            testDispatcher.scheduler.advanceTimeBy(100)
            viewModel.onQueryChange("meet")

            // Only the final query should execute once debounce finishes
            testDispatcher.scheduler.advanceTimeBy(350)
            testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, fakeSearch.searchCount)
            assertEquals("meet", fakeSearch.lastQuery)
        }

    @Test
    fun onQueryChange_blankQueryClearsResultsImmediately() =
        runTest {
            // First run a valid search
            viewModel.onQueryChange("idea")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.results.size)

            // Now enter blank query
            viewModel.onQueryChange("")
            assertEquals("", viewModel.uiState.value.query)
            assertTrue(
                viewModel.uiState.value.results
                    .isEmpty(),
            )
            assertFalse(viewModel.uiState.value.isSearching)
        }

    @Test
    fun clearQuery_clearsQueryAndResults() =
        runTest {
            viewModel.onQueryChange("draft")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, viewModel.uiState.value.results.size)

            viewModel.clearQuery()
            assertEquals("", viewModel.uiState.value.query)
            assertTrue(
                viewModel.uiState.value.results
                    .isEmpty(),
            )
            assertFalse(viewModel.uiState.value.isSearching)
        }

    @Test
    fun rescan_delegatesToRepositoryAndRefreshesSearch() =
        runTest {
            viewModel.onQueryChange("project")
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, fakeSearch.searchCount)

            fakeSearch.resultsToReturn =
                listOf(
                    SearchResult("note-1", "Project A", "Snippet 1"),
                    SearchResult("note-2", "Project B", "Snippet 2"),
                )

            viewModel.rescan()
            testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(fakeRepo.rescanCalled)
            assertEquals(2, fakeSearch.searchCount)
            assertEquals(2, viewModel.uiState.value.results.size)
            assertFalse(viewModel.uiState.value.isSearching)
        }

    private class FakeKeywordSearch : KeywordSearch {
        var searchCount = 0
        var lastQuery: String? = null
        var resultsToReturn: List<SearchResult> =
            listOf(
                SearchResult(
                    noteId = "meeting-note",
                    title = "Meeting",
                    snippet = "Snippet",
                ),
            )

        override suspend fun search(
            query: String,
            scope: SearchScope,
        ): List<SearchResult> {
            searchCount++
            lastQuery = query
            return resultsToReturn
        }
    }

    private class FakeNoteRepository : NoteRepository {
        var rescanCalled = false

        override suspend fun rescan(): RescanReport {
            rescanCalled = true
            return RescanReport(added = 1, changed = 0, removed = 0)
        }

        override fun observeNotesInFolder(folderPath: String) = emptyFlow<List<Note>>()

        override fun observeAllNotes() = emptyFlow<List<Note>>()

        override suspend fun readBody(noteId: String): String = ""

        override suspend fun listFolders(): List<String> = emptyList()

        override suspend fun createFolder(
            parentPath: String,
            name: String,
        ) = Unit

        override suspend fun createNote(
            folderPath: String,
            title: String,
            type: NoteType,
        ): Note = throw NotImplementedError()

        override suspend fun edit(
            noteId: String,
            newBody: String,
        ) = Unit

        override suspend fun setPinned(
            noteId: String,
            pinned: Boolean,
        ) = Unit

        override suspend fun setColor(
            noteId: String,
            color: String?,
        ) = Unit
    }

    private class TestDispatcherProvider(
        private val dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val io: CoroutineDispatcher
            get() = dispatcher
        override val default: CoroutineDispatcher
            get() = dispatcher
        override val main: CoroutineDispatcher
            get() = dispatcher
        override val mainImmediate: CoroutineDispatcher
            get() = dispatcher
    }
}
