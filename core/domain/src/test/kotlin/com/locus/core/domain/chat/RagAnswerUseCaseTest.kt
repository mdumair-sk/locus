package com.locus.core.domain.chat

import com.locus.core.domain.providers.ProviderAdapter
import com.locus.core.domain.providers.ProviderCapabilities
import com.locus.core.domain.providers.ProviderMessage
import com.locus.core.domain.providers.StreamEvent
import com.locus.core.domain.providers.ToolSchema
import com.locus.core.domain.search.ChunkMetadata
import com.locus.core.domain.search.ChunkRepository
import com.locus.core.domain.search.EmbeddedChunk
import com.locus.core.domain.search.EmbeddingGateway
import com.locus.core.domain.search.HybridSearchUseCase
import com.locus.core.domain.search.KeywordSearch
import com.locus.core.domain.search.RankedChunk
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.search.SearchScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RagAnswerUseCaseTest {
    private class FakeProviderAdapter(
        private val eventsProvider: (List<ProviderMessage>) -> List<StreamEvent>,
    ) : ProviderAdapter {
        override val capabilities: ProviderCapabilities =
            ProviderCapabilities(
                supportsNativeTools = false,
                contextLength = 8192,
                pricePerMillionInputTokens = null,
                pricePerMillionOutputTokens = null,
            )
        var lastRecordedMessages: List<ProviderMessage> = emptyList()

        override fun streamChat(
            messages: List<ProviderMessage>,
            tools: List<ToolSchema>,
        ): Flow<StreamEvent> {
            lastRecordedMessages = messages
            return flow {
                for (event in eventsProvider(messages)) {
                    emit(event)
                }
            }
        }
    }

    private class FakeKeywordSearch(
        private val results: List<SearchResult>,
    ) : KeywordSearch {
        override suspend fun search(
            query: String,
            scope: SearchScope,
        ): List<SearchResult> = results
    }

    private class UnavailableChunkRepository : ChunkRepository {
        override suspend fun isAvailable(): Boolean = false

        override suspend fun getMetadata(noteId: String): ChunkMetadata? = null

        override suspend fun replaceChunksForNote(
            noteId: String,
            chunks: List<EmbeddedChunk>,
        ) {
            error("Unused in RAG answer test")
        }

        override suspend fun search(
            queryVector: FloatArray,
            topK: Int,
            noteIds: Set<String>?,
        ): List<RankedChunk> = emptyList()

        override suspend fun deleteAll() {
            error("Unused in RAG answer test")
        }
    }

    private class DummyEmbeddingGateway : EmbeddingGateway {
        override suspend fun embed(text: String): FloatArray = floatArrayOf(0.0f)
    }

    private fun createUseCase(
        searchResults: List<SearchResult>,
        cannedEvents: List<StreamEvent>,
    ): Pair<RagAnswerUseCase, FakeProviderAdapter> {
        val hybridSearch =
            HybridSearchUseCase(
                keywordSearch = FakeKeywordSearch(searchResults),
                chunkRepository = UnavailableChunkRepository(),
                embeddingGateway = DummyEmbeddingGateway(),
            )
        val providerAdapter = FakeProviderAdapter { cannedEvents }
        val useCase =
            RagAnswerUseCase(
                hybridSearch = hybridSearch,
                providerAdapter = providerAdapter,
            )
        return Pair(useCase, providerAdapter)
    }

    private fun createCannedTokens(vararg tokens: String): List<StreamEvent> =
        tokens.map { StreamEvent.TokenDelta(it) } + listOf(StreamEvent.Done())

    @Test
    fun resultingRagAnswerSourcesAlwaysHasAtLeastOneEntryWhenSearchReturnsAtLeastOneChunk() =
        runTest {
            val chunk =
                SearchResult(
                    noteId = "note-geo-1",
                    title = "Geography of France",
                    snippet = "Paris is the capital and most populous city of France.",
                    headingPath = listOf("Overview"),
                )
            val (useCase, _) =
                createUseCase(
                    searchResults = listOf(chunk),
                    cannedEvents =
                        createCannedTokens("Paris is the capital of France [1]."),
                )

            val answer = useCase(query = "What is the capital of France?")

            assertTrue("Sources should have at least 1 entry", answer.sources.isNotEmpty())
            assertEquals(1, answer.sources.size)
            assertEquals("note-geo-1", answer.sources[0].noteId)
            assertEquals("Geography of France", answer.sources[0].noteTitle)
            assertEquals(listOf("Overview"), answer.sources[0].headingPath)
            assertTrue(answer.text.contains("[1]"))
        }

    @Test
    fun outOfRangeCitationInModelOutputIsStrippedFromText() =
        runTest {
            val chunk =
                SearchResult(
                    noteId = "note-math-1",
                    title = "Number Theory",
                    snippet = "Prime numbers have exactly two distinct positive divisors.",
                )
            val (useCase, _) =
                createUseCase(
                    searchResults = listOf(chunk),
                    cannedEvents =
                        createCannedTokens(
                            "Prime numbers have two divisors [1]",
                            " and magic numbers have infinite divisors [99].",
                        ),
                )

            val answer = useCase(query = "Explain primes")

            assertFalse("Out-of-range citation [99] must be stripped", answer.text.contains("[99]"))
            assertTrue("Valid citation [1] must remain", answer.text.contains("[1]"))
            assertEquals(1, answer.sources.size)
        }

    @Test
    fun outOfRangeCitationStrippedWhenOnlyOutOfRangeCitationProvidedAndMandatoryCitationEnsured() =
        runTest {
            val chunk =
                SearchResult(
                    noteId = "note-history-1",
                    title = "French Revolution",
                    snippet = "The Bastille was stormed on July 14, 1789.",
                )
            val (useCase, _) =
                createUseCase(
                    searchResults = listOf(chunk),
                    cannedEvents =
                        createCannedTokens(
                            "The Bastille was stormed in 1789 [42].",
                        ),
                )

            val answer = useCase(query = "When was the Bastille stormed?")

            assertFalse(
                "Out-of-range citation [42] must be stripped",
                answer.text.contains("[42]"),
            )
            assertTrue("Mandatory citation [1] must be appended", answer.text.contains("[1]"))
            assertTrue("Sources should have at least 1 entry", answer.sources.isNotEmpty())
            assertEquals("note-history-1", answer.sources[0].noteId)
        }

    @Test
    fun modelWithoutCitationsGetsMandatoryCitationMarkerWhenChunksExist() =
        runTest {
            val chunk =
                SearchResult(
                    noteId = "note-physics-1",
                    title = "Relativity",
                    snippet = "Speed of light is approximately 300,000 km/s.",
                )
            val (useCase, _) =
                createUseCase(
                    searchResults = listOf(chunk),
                    cannedEvents =
                        createCannedTokens(
                            "The speed of light is 300,000 km/s in a vacuum.",
                        ),
                )

            val answer = useCase(query = "Speed of light?")

            assertTrue("Non-trivial answer must contain citation marker", answer.text.contains("[1]"))
            assertTrue("Sources must have at least 1 entry", answer.sources.isNotEmpty())
            assertEquals("note-physics-1", answer.sources[0].noteId)
        }

    @Test
    fun searchReturningZeroChunksYieldsZeroSourcesAndStripsHallucinatedCitations() =
        runTest {
            val (useCase, _) =
                createUseCase(
                    searchResults = emptyList(),
                    cannedEvents = createCannedTokens("I found no notes about that [1]."),
                )

            val answer = useCase(query = "Unknown topic")

            assertTrue(
                "Sources must be empty when search returned zero chunks",
                answer.sources.isEmpty(),
            )
            assertFalse("Hallucinated citation [1] must be stripped", answer.text.contains("[1]"))
        }

    @Test
    fun multipleChunksAreAllPreservedInStructuredSources() =
        runTest {
            val chunks =
                listOf(
                    SearchResult(
                        noteId = "note-1",
                        title = "Architecture",
                        snippet = "Clean architecture separates concerns.",
                        headingPath = listOf("Design", "Clean"),
                    ),
                    SearchResult(
                        noteId = "note-2",
                        title = "Patterns",
                        snippet = "Dependency inversion decouples modules.",
                        headingPath = listOf("SOLID"),
                    ),
                )
            val (useCase, _) =
                createUseCase(
                    searchResults = chunks,
                    cannedEvents =
                        createCannedTokens(
                            "Architecture separates concerns [1] and decouples modules [2].",
                        ),
                )

            val answer = useCase(query = "Software design principles")

            assertEquals(2, answer.sources.size)
            assertEquals("note-1", answer.sources[0].noteId)
            assertEquals("Architecture", answer.sources[0].noteTitle)
            assertEquals(listOf("Design", "Clean"), answer.sources[0].headingPath)
            assertEquals("note-2", answer.sources[1].noteId)
            assertEquals("Patterns", answer.sources[1].noteTitle)
            assertEquals(listOf("SOLID"), answer.sources[1].headingPath)
        }

    @Test
    fun onTokenDeltaReceivesStreamedDeltas() =
        runTest {
            val chunk =
                SearchResult(
                    noteId = "note-stream-1",
                    title = "Streaming Guide",
                    snippet = "Tokens stream in real time.",
                )
            val (useCase, _) =
                createUseCase(
                    searchResults = listOf(chunk),
                    cannedEvents = createCannedTokens("Real ", "time ", "streaming [1]."),
                )

            val receivedDeltas = mutableListOf<String>()
            val answer =
                useCase(
                    query = "How does streaming work?",
                    onTokenDelta = { receivedDeltas.add(it) },
                )

            assertEquals(listOf("Real ", "time ", "streaming [1]."), receivedDeltas)
            assertTrue(answer.text.contains("[1]"))
        }
}
