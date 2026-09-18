package com.locus.core.ai.toolloop

import com.locus.core.ai.tools.ListFoldersTool
import com.locus.core.ai.tools.ReadNoteTool
import com.locus.core.ai.tools.SearchNotesTool
import com.locus.core.domain.providers.ProviderAdapter
import com.locus.core.domain.providers.ProviderMessage
import com.locus.core.domain.providers.ProviderRole
import com.locus.core.domain.providers.StreamEvent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool orchestrator (C-3, C-8): single entry point for tool invocation. Picks
 * [NativeFunctionCallingBridge] vs [JsonModeToolLoop] based on
 * [com.locus.core.domain.providers.ProviderCapabilities.supportsNativeTools].
 */
@Singleton
class ToolOrchestrator(
    private val tools: List<ToolExecutor>,
    private val defaultAdapter: ProviderAdapter? = null,
    private val maxIterations: Int = 8,
) {
    @Inject
    constructor(
        searchNotesTool: SearchNotesTool,
        readNoteTool: ReadNoteTool,
        listFoldersTool: ListFoldersTool,
    ) : this(listOf(searchNotesTool, readNoteTool, listFoldersTool))

    suspend fun run(
        userMessage: String,
        adapter: ProviderAdapter? = defaultAdapter,
    ): String {
        val activeAdapter =
            adapter
                ?: throw IllegalArgumentException(
                    "ProviderAdapter must be provided to ToolOrchestrator",
                )

        return if (activeAdapter.capabilities.supportsNativeTools) {
            NativeFunctionCallingBridge(activeAdapter, tools, maxIterations).run(userMessage)
        } else {
            val client = AdapterJsonModeClient(activeAdapter)
            JsonModeToolLoop(client, tools, maxIterations = maxIterations).run(userMessage)
        }
    }

    suspend fun runWithJsonClient(
        userMessage: String,
        client: JsonModeCompletionClient,
    ): String = JsonModeToolLoop(client, tools, maxIterations = maxIterations).run(userMessage)
}

internal class AdapterJsonModeClient(
    private val adapter: ProviderAdapter,
) : JsonModeCompletionClient {
    override suspend fun complete(
        systemPrompt: String,
        transcript: List<String>,
    ): String {
        val messages = mutableListOf<ProviderMessage>()
        messages += ProviderMessage(role = ProviderRole.SYSTEM, content = systemPrompt)

        for (entry in transcript) {
            when {
                entry.startsWith("USER: ") ->
                    messages +=
                        ProviderMessage(
                            role = ProviderRole.USER,
                            content = entry.removePrefix("USER: "),
                        )
                entry.startsWith("ASSISTANT: ") ->
                    messages +=
                        ProviderMessage(
                            role = ProviderRole.ASSISTANT,
                            content = entry.removePrefix("ASSISTANT: "),
                        )
                else -> messages += ProviderMessage(role = ProviderRole.USER, content = entry)
            }
        }

        val merged = mergeConsecutiveRoles(messages)
        val sb = StringBuilder()
        adapter.streamChat(merged).collect { event ->
            when (event) {
                is StreamEvent.TokenDelta -> sb.append(event.text)
                is StreamEvent.Error -> throw ToolLoopException("Provider error: ${event.message}")
                else -> {}
            }
        }
        return sb.toString()
    }

    private fun mergeConsecutiveRoles(messages: List<ProviderMessage>): List<ProviderMessage> {
        if (messages.isEmpty()) return emptyList()
        val result = mutableListOf<ProviderMessage>()
        var current = messages.first()
        for (i in 1 until messages.size) {
            val next = messages[i]
            current =
                if (current.role == next.role) {
                    current.copy(content = current.content + "\n\n" + next.content)
                } else {
                    result += current
                    next
                }
        }
        result += current
        return result
    }
}
