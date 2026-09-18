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
 * M-2: Zero network when local models are selected.
 * Constructor dependency graph consists exclusively of [LlamaRuntime] with zero HTTP dependencies.
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
                runtime.generateStream(prompt).collect { token ->
                    emit(StreamEvent.TokenDelta(token))
                }
                emit(StreamEvent.Done(finishReason = "stop"))
            }.catch { e ->
                emit(StreamEvent.Error(message = e.message ?: "Local generation failed", cause = e))
            }
    }
