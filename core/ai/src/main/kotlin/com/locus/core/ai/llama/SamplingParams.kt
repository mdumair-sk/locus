package com.locus.core.ai.llama

/**
 * Parameters governing token generation in [LlamaRuntime] (Prompt 41, M-1).
 */
data class SamplingParams(
    val temperature: Double = DEFAULT_TEMPERATURE,
    val topP: Double = DEFAULT_TOP_P,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
) {
    companion object {
        const val DEFAULT_TEMPERATURE = 0.7
        const val DEFAULT_TOP_P = 0.9
        const val DEFAULT_MAX_TOKENS = 1024
    }
}
