package com.locus.core.domain.routing

import com.locus.core.domain.chat.RagAnswerUseCase
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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRoutingPolicyTest {
    @Test
    fun resolve_returnsUserConfiguredStrongCloudModel_whenConfigured() {
        val model =
            ModelRef(
                id = "claude-3-5-sonnet-latest",
                tier = ModelTier.CLOUD,
                providerId = "anthropic",
            )
        val policy = ChatRoutingPolicy(usersStrongCloudModel = model)

        val resolved = policy.resolve()

        assertEquals(model, resolved)
        assertEquals("claude-3-5-sonnet-latest", resolved.id)
        assertEquals(ModelTier.CLOUD, resolved.tier)
        assertEquals("anthropic", resolved.providerId)
    }

    @Test
    fun resolve_throwsNoConfiguredProviderException_whenModelIsNull() {
        val policy = ChatRoutingPolicy(usersStrongCloudModel = null)

        val exception = assertThrows(NoConfiguredProviderException::class.java) { policy.resolve() }

        assertTrue(
            "Exception message should surface 'choose a provider', but was: ${exception.message}",
            exception.message?.contains("choose a provider") == true,
        )
    }

    @Test
    fun modelRef_properties_holdExpectedValues() {
        val cloudModel =
            ModelRef(
                id = "gpt-4o",
                tier = ModelTier.CLOUD,
                providerId = "openai",
            )
        assertEquals("gpt-4o", cloudModel.id)
        assertEquals(ModelTier.CLOUD, cloudModel.tier)
        assertEquals("openai", cloudModel.providerId)

        val localModel =
            ModelRef(
                id = "qwen-2.5-7b",
                tier = ModelTier.LOCAL,
                providerId = null,
            )
        assertEquals("qwen-2.5-7b", localModel.id)
        assertEquals(ModelTier.LOCAL, localModel.tier)
        assertNull(localModel.providerId)
    }

    @Test
    fun ragAnswerUseCase_withRoutingPolicy_throwsWhenNoProviderConfigured() =
        runTest {
            val hybridSearch =
                HybridSearchUseCase(
                    keywordSearch = FakeKeywordSearch(emptyList()),
                    chunkRepository = UnavailableChunkRepository(),
                    embeddingGateway = DummyEmbeddingGateway(),
                )
            val fallbackAdapter = FakeProviderAdapter(listOf(StreamEvent.TokenDelta("fallback")))
            val routingPolicy = ChatRoutingPolicy(usersStrongCloudModel = null)

            val useCase =
                RagAnswerUseCase(
                    hybridSearch = hybridSearch,
                    providerAdapter = fallbackAdapter,
                    routingPolicy = routingPolicy,
                )

            var thrown: NoConfiguredProviderException? = null
            try {
                useCase(query = "Any notes on Kotlin?")
            } catch (e: NoConfiguredProviderException) {
                thrown = e
            }

            assertTrue("Expected NoConfiguredProviderException to be thrown", thrown != null)
            assertTrue(
                "Exception message should contain 'choose a provider', but was: ${thrown?.message}",
                thrown?.message?.contains("choose a provider") == true,
            )
        }

    @Test
    fun ragAnswerUseCase_withRoutingPolicy_streamsViaResolvedAdapter() =
        runTest {
            val searchResults =
                listOf(
                    SearchResult(
                        noteId = "note-1",
                        title = "Kotlin Guide",
                        snippet = "Kotlin has coroutines and sealed classes.",
                        score = 1.0,
                    ),
                )
            val hybridSearch =
                HybridSearchUseCase(
                    keywordSearch = FakeKeywordSearch(searchResults),
                    chunkRepository = UnavailableChunkRepository(),
                    embeddingGateway = DummyEmbeddingGateway(),
                )

            val defaultAdapter = FakeProviderAdapter(listOf(StreamEvent.TokenDelta("from default [1]")))
            val routedAdapter =
                FakeProviderAdapter(listOf(StreamEvent.TokenDelta("from routed cloud adapter [1]")))

            val cloudModel =
                ModelRef(
                    id = "gpt-4o",
                    tier = ModelTier.CLOUD,
                    providerId = "openai",
                )
            val routingPolicy = ChatRoutingPolicy(usersStrongCloudModel = cloudModel)

            val useCase =
                RagAnswerUseCase(
                    hybridSearch = hybridSearch,
                    providerAdapter = defaultAdapter,
                    routingPolicy = routingPolicy,
                    adapterResolver = { modelRef ->
                        if (modelRef == cloudModel) routedAdapter else defaultAdapter
                    },
                )

            val answer = useCase(query = "What does Kotlin have?")

            assertEquals("from routed cloud adapter [1]", answer.text)
            assertEquals(1, answer.sources.size)
            assertEquals("note-1", answer.sources.first().noteId)
        }

    private class FakeProviderAdapter(
        private val events: List<StreamEvent>,
    ) : ProviderAdapter {
        override val capabilities: ProviderCapabilities =
            ProviderCapabilities(
                supportsNativeTools = false,
                contextLength = 4096,
                pricePerMillionInputTokens = null,
                pricePerMillionOutputTokens = null,
            )

        override fun streamChat(
            messages: List<ProviderMessage>,
            tools: List<ToolSchema>,
        ): Flow<StreamEvent> = events.asFlow()
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
            error("Unused")
        }

        override suspend fun search(
            queryVector: FloatArray,
            topK: Int,
            noteIds: Set<String>?,
        ): List<RankedChunk> = emptyList()

        override suspend fun deleteAll() {
            error("Unused")
        }
    }

    private class DummyEmbeddingGateway : EmbeddingGateway {
        override suspend fun embed(text: String): FloatArray = floatArrayOf(0.0f)
    }
}
