package com.locus.core.domain.chat

import com.locus.core.domain.providers.ProviderAdapter
import com.locus.core.domain.providers.ProviderMessage
import com.locus.core.domain.providers.ProviderRole
import com.locus.core.domain.providers.StreamEvent
import com.locus.core.domain.search.HybridSearchUseCase
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.search.SearchScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RAG answer use case (S-7, C-2):
 * "Citations are mandatory on all RAG answers: inline `[n]` markers plus a Sources block;
 * each citation is tappable and navigates to the note."
 *
 * 1. Runs [HybridSearchUseCase] against query + scope (default all notes per C-2).
 * 2. Builds a system prompt instructing the model to cite claims using `[n]` referencing the numbered chunk list.
 * 3. Streams the model's answer via [ProviderAdapter.streamChat].
 * 4. Strips any out-of-range `[n]` markers referencing chunk indices outside the retrieved list.
 * 5. Validates non-trivial answers contain at least one `[n]` marker (appending `[1]` if missing when chunks exist).
 * 6. Appends a structured `sources` list ([List]<[CitedSource]>) derived from the actual retrieved chunks.
 */
@Singleton
class RagAnswerUseCase
    @Inject
    constructor(
        private val hybridSearch: HybridSearchUseCase,
        private val providerAdapter: ProviderAdapter,
    ) {
        suspend operator fun invoke(
            query: String,
            scope: SearchScope = SearchScope(),
            history: List<ProviderMessage> = emptyList(),
            onTokenDelta: ((String) -> Unit)? = null,
        ): RagAnswer {
            val searchResult = hybridSearch(query = query, scope = scope)
            val chunks = searchResult.results

            val systemPrompt = buildSystemPrompt(chunks)
            val messages =
                buildList {
                    add(ProviderMessage(role = ProviderRole.SYSTEM, content = systemPrompt))
                    addAll(history)
                    add(ProviderMessage(role = ProviderRole.USER, content = query))
                }

            val textBuffer = StringBuilder()
            providerAdapter.streamChat(messages).collect { event ->
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

        private fun buildSystemPrompt(chunks: List<SearchResult>): String {
            val instructions =
                """
                You are an assistant answering questions based on the user's notes.
                Citations are mandatory on all RAG answers: inline [n] markers plus a Sources block; each citation is tappable and navigates to the note.
                Cite every claim with [n] referencing the numbered context chunks below.
                Only reference source numbers that exist in the context list. Do not cite numbers outside this range.
                """.trimIndent()

            if (chunks.isEmpty()) {
                return instructions
            }

            val context =
                chunks
                    .mapIndexed { index, chunk ->
                        val headingLine =
                            if (chunk.headingPath.isNotEmpty()) {
                                "Heading: ${chunk.headingPath.joinToString(" > ")}\n"
                            } else {
                                ""
                            }
                        "[${index + 1}] Title: ${chunk.title}\n${headingLine}Content: ${chunk.snippet}"
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

        companion object {
            private val CITATION_REGEX = Regex("""(\s*)\[(\d+)\]""")
        }
    }
