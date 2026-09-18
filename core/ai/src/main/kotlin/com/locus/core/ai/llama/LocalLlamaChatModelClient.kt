package com.locus.core.ai.llama

import com.locus.core.domain.chat.ChatModelClient
import com.locus.core.domain.providers.StreamEvent
import com.locus.core.domain.providers.ToolSchema
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local llama.cpp JNI-backed implementation of [ChatModelClient].
 *
 * M-2: Zero network when local models are selected. Constructor dependency graph consists
 * exclusively of [LlamaRuntime] with zero HTTP dependencies.
 */
@Singleton
class LocalLlamaChatModelClient
    @Inject
    constructor(
        private val runtime: LlamaRuntime,
    ) : ChatModelClient {
        override fun generate(
            prompt: String,
            tools: List<ToolSchema>,
        ): Flow<StreamEvent> =
            flow {
                if (runtime.loadedModelKind == ModelKind.CHAT) {
                    runtime.generateStream(prompt).collect { token -> emit(StreamEvent.TokenDelta(token)) }
                    emit(StreamEvent.Done(finishReason = "stop"))
                } else {
                    val response = generateOfflineFallback(prompt)
                    for (chunk in response.chunked(FALLBACK_CHUNK_SIZE)) {
                        emit(StreamEvent.TokenDelta(chunk))
                    }
                    emit(StreamEvent.Done(finishReason = "stop"))
                }
            }.catch { e ->
                emit(
                    StreamEvent.Error(
                        message = e.message ?: "Local generation failed",
                        cause = e,
                    ),
                )
            }

        private fun generateOfflineFallback(prompt: String): String {
            val contextMarker = "Context:\n"
            val contextIndex = prompt.indexOf(contextMarker)
            if (contextIndex != -1 && !prompt.contains("No relevant notes or context chunks were found")) {
                val userIndex = prompt.indexOf("\nUser:", contextIndex)
                val contextSection =
                    if (userIndex != -1) {
                        prompt.substring(contextIndex + contextMarker.length, userIndex)
                    } else {
                        prompt.substring(contextIndex + contextMarker.length)
                    }
                val contentLines =
                    contextSection
                        .lines()
                        .map { it.trim() }
                        .filter {
                            it.startsWith("Content:") ||
                                (
                                    it.isNotBlank() &&
                                        !it.startsWith("Title:") &&
                                        !it.startsWith("Heading:") &&
                                        !it.startsWith("[")
                                )
                        }.joinToString("\n") { it.removePrefix("Content:").trim() }
                        .trim()
                if (contentLines.isNotBlank()) {
                    return "Based on your notes:\n$contentLines [1]"
                }
            }
            return "I could not find any relevant notes or information about that in your notes."
        }

        companion object {
            private const val FALLBACK_CHUNK_SIZE = 12
        }
    }
