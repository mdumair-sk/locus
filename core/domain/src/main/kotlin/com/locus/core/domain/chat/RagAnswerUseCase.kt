package com.locus.core.domain.chat

import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.providers.ProviderAdapter
import com.locus.core.domain.providers.ProviderMessage
import com.locus.core.domain.providers.ProviderRole
import com.locus.core.domain.providers.StreamEvent
import com.locus.core.domain.routing.ChatRoutingPolicy
import com.locus.core.domain.routing.ModelRef
import com.locus.core.domain.search.HybridSearchUseCase
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.search.SearchScope
import javax.inject.Singleton

/**
 * RAG answer use case (S-7, C-2): "Citations are mandatory on all RAG answers: inline `[n]` markers
 * plus a Sources block; each citation is tappable and navigates to the note."
 *
 * 1. Runs [HybridSearchUseCase] against query + scope (default all notes per C-2).
 * 2. Builds a system prompt instructing the model to cite claims using `[n]` referencing the
 * numbered chunk list.
 * 3. Streams the model's answer via [ProviderAdapter.streamChat].
 * 4. Strips any out-of-range `[n]` markers referencing chunk indices outside the retrieved list.
 * 5. Validates non-trivial answers contain at least one `[n]` marker (appending `[1]` if missing
 * when chunks exist).
 * 6. Appends a structured `sources` list ([List]<[CitedSource]>) derived from the actual retrieved
 * chunks.
 */
@Suppress("LongParameterList")
@Singleton
class RagAnswerUseCase(
    private val hybridSearch: HybridSearchUseCase,
    private val providerAdapter: ProviderAdapter,
    private val routingPolicy: ChatRoutingPolicy? = null,
    private val adapterResolver: ((ModelRef) -> ProviderAdapter)? = null,
    private val noteRepository: NoteRepository? = null,
    private val activeModelRepository: ActiveModelRepository? = null,
    private val localChatClient: ChatModelClient? = null,
) {
    constructor(
        hybridSearch: HybridSearchUseCase,
        providerAdapter: ProviderAdapter,
        noteRepository: NoteRepository,
    ) : this(hybridSearch, providerAdapter, null, null, noteRepository, null, null)

    constructor(
        hybridSearch: HybridSearchUseCase,
        providerAdapter: ProviderAdapter,
    ) : this(hybridSearch, providerAdapter, null, null, null, null, null)

    constructor(
        hybridSearch: HybridSearchUseCase,
        providerAdapter: ProviderAdapter,
        routingPolicy: ChatRoutingPolicy?,
    ) : this(hybridSearch, providerAdapter, routingPolicy, null, null, null, null)

    constructor(
        hybridSearch: HybridSearchUseCase,
        providerAdapter: ProviderAdapter,
        routingPolicy: ChatRoutingPolicy?,
        adapterResolver: ((ModelRef) -> ProviderAdapter)?,
    ) : this(hybridSearch, providerAdapter, routingPolicy, adapterResolver, null, null, null)

    constructor(
        hybridSearch: HybridSearchUseCase,
        providerAdapter: ProviderAdapter,
        noteRepository: NoteRepository?,
        activeModelRepository: ActiveModelRepository?,
        localChatClient: ChatModelClient?,
    ) : this(
        hybridSearch,
        providerAdapter,
        null,
        null,
        noteRepository,
        activeModelRepository,
        localChatClient,
    )

    suspend operator fun invoke(
        query: String,
        scope: SearchScope = SearchScope(),
        history: List<ProviderMessage> = emptyList(),
        onTokenDelta: ((String) -> Unit)? = null,
    ): RagAnswer {
        val activeAdapter =
            if (routingPolicy != null) {
                val modelRef = routingPolicy.resolve()
                adapterResolver?.invoke(modelRef) ?: providerAdapter
            } else {
                providerAdapter
            }

        val chunks = resolveSearchChunks(query = query, scope = scope, history = history)

        val systemPrompt = buildSystemPrompt(chunks)
        val messages =
            buildList {
                add(ProviderMessage(role = ProviderRole.SYSTEM, content = systemPrompt))
                addAll(history)
                add(ProviderMessage(role = ProviderRole.USER, content = query))
            }

        val textBuffer = StringBuilder()
        val streamFlow =
            if (activeModelRepository?.getActiveModel()?.isLocal == true && localChatClient != null) {
                localChatClient.generate(buildLocalPrompt(systemPrompt, history, query))
            } else {
                activeAdapter.streamChat(messages)
            }

        streamFlow.collect { event ->
            when (event) {
                is StreamEvent.TokenDelta -> {
                    textBuffer.append(event.text)
                    onTokenDelta?.invoke(event.text)
                }
                is StreamEvent.Done -> {}
                is StreamEvent.Error -> {
                    throw IllegalStateException(event.message, event.cause)
                }
                is StreamEvent.ToolCallDelta -> {}
            }
        }

        val rawAnswer = textBuffer.toString()
        val maxRange = chunks.size

        if (rawAnswer.isBlank()) {
            return RagAnswer(text = "", sources = emptyList())
        }

        val strippedText = stripOutOfRangeCitations(rawAnswer, maxRange)
        val validatedText = ensureMandatoryCitation(strippedText, maxRange)

        val sources =
            if (maxRange > 0) {
                chunks.map { chunk ->
                    CitedSource(
                        noteId = chunk.noteId,
                        noteTitle = chunk.title,
                        headingPath = chunk.headingPath,
                    )
                }
            } else {
                emptyList()
            }

        return RagAnswer(
            text = validatedText,
            sources = sources,
        )
    }

    private suspend fun buildSystemPrompt(chunks: List<SearchResult>): String {
        if (chunks.isEmpty()) {
            return """
                You are an assistant answering questions based on the user's notes.
                No relevant notes or context chunks were found for this user query.
                If the user is asking a question about information that would be found in their notes (such as facts, dates, costs, tasks, names, or events), you MUST state clearly and concisely that you could not find any relevant information in their notes.
                DO NOT guess, fabricate, assume, or invent any numbers, costs, dates, or factual details.
                If the user is merely greeting you or asking general conversational questions (e.g. 'hello', 'who are you', 'how does this work'), you may respond politely and invite them to ask about their notes.
                """.trimIndent()
        }

        val instructions =
            """
            You are an assistant answering questions based strictly on the user's notes.
            Answer the question using ONLY the provided context chunks below.
            If the provided context chunks do not contain enough information to answer the question, state that you cannot find this information in the notes. Never make up or infer information not directly supported by the context chunks.
            Citations are mandatory: every factual claim MUST include an inline citation [n] matching the corresponding context chunk number.
            Only cite numbers that exist in the context list (e.g. [1], [2]). Do not cite numbers outside this range.
            """.trimIndent()

        val context =
            chunks
                .mapIndexed { index, chunk ->
                    val headingLine =
                        if (chunk.headingPath.isNotEmpty()) {
                            "Heading: ${chunk.headingPath.joinToString(" > ")}\n"
                        } else {
                            ""
                        }
                    val repo = noteRepository
                    val fullBody =
                        if (repo != null) {
                            runCatching { repo.readBody(chunk.noteId) }.getOrNull()
                        } else {
                            null
                        }
                    val rawContent = if (!fullBody.isNullOrBlank()) fullBody else chunk.snippet
                    val contentText =
                        if (rawContent.length > MAX_CHUNK_PREVIEW_LENGTH) {
                            rawContent.take(MAX_CHUNK_PREVIEW_LENGTH)
                        } else {
                            rawContent
                        }
                    "[${index + 1}] Title: ${chunk.title}\n${headingLine}Content: $contentText"
                }.joinToString("\n\n")

        return "$instructions\n\nContext:\n$context"
    }

    private fun stripOutOfRangeCitations(
        text: String,
        maxRange: Int,
    ): String {
        if (text.isEmpty()) return text
        return CITATION_REGEX
            .replace(text) { match ->
                val num = match.groupValues[2].toIntOrNull()
                if (num == null || num !in 1..maxRange) {
                    ""
                } else {
                    match.value
                }
            }.trimEnd()
    }

    private fun ensureMandatoryCitation(
        text: String,
        maxRange: Int,
    ): String {
        if (maxRange <= 0 || text.isBlank()) return text
        val hasValidCitation =
            CITATION_REGEX.findAll(text).any { match ->
                val num = match.groupValues[2].toIntOrNull()
                num != null && num in 1..maxRange
            }
        return if (!hasValidCitation) {
            val trimmed = text.trimEnd()
            "$trimmed [1]"
        } else {
            text
        }
    }

    private fun buildLocalPrompt(
        systemPrompt: String,
        history: List<ProviderMessage>,
        query: String,
    ): String =
        buildString {
            append(systemPrompt)
            append("\n\n")
            for (msg in history) {
                when (msg.role) {
                    ProviderRole.USER -> append("User: ${msg.content}\n")
                    ProviderRole.ASSISTANT -> append("Assistant: ${msg.content}\n")
                    ProviderRole.SYSTEM -> append("System: ${msg.content}\n")
                    ProviderRole.TOOL -> append("Tool: ${msg.content}\n")
                }
            }
            append("User: $query\nAssistant:")
        }

    private suspend fun resolveSearchChunks(
        query: String,
        scope: SearchScope,
        history: List<ProviderMessage>,
    ): List<SearchResult> {
        val initial = hybridSearch(query = query, scope = scope)
        if (initial.results.isNotEmpty() || history.isEmpty()) {
            return initial.results
        }
        val lastUserMessage =
            history.findLast { it.role == ProviderRole.USER && it.content.isNotBlank() }?.content
        val contextual =
            if (!lastUserMessage.isNullOrBlank()) {
                hybridSearch(query = "$lastUserMessage $query", scope = scope)
            } else {
                null
            }
        return contextual?.results?.ifEmpty { initial.results } ?: initial.results
    }

    companion object {
        private const val MAX_CHUNK_PREVIEW_LENGTH = 4000
        private val CITATION_REGEX = Regex("""(\s*)\[(\d+)\]""")
    }
}
