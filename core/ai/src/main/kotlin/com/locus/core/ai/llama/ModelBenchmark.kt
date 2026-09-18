package com.locus.core.ai.llama

import com.locus.core.domain.models.BenchmarkResult
import com.locus.core.domain.models.ModelMetaRepository
import javax.inject.Inject
import javax.inject.Singleton

private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val NANOS_PER_MILLI = 1_000_000L

@Singleton
class ModelBenchmark
    @Inject
    constructor(
        private val llamaRuntime: LlamaRuntime,
        private val metaRepository: ModelMetaRepository,
        private val deviceProvider: DeviceFingerprintProvider,
    ) {
        suspend fun run(
            modelId: String,
            path: String,
        ): BenchmarkResult {
            val loadResult = llamaRuntime.loadModel(path, ModelKind.CHAT)
            check(loadResult.isSuccess) {
                "Failed to load model for benchmark from path: $path: ${loadResult.exceptionOrNull()?.message}"
            }

            val cannedPrompt =
                "Explain the core benefits of offline-first software architectures in three concise sentences."
            val samplingParams = SamplingParams(maxTokens = 128, temperature = 0.0)

            val startNanos = System.nanoTime()
            var tokenCount = 0
            llamaRuntime.generateStream(cannedPrompt, samplingParams).collect { _ -> tokenCount++ }
            val elapsedNanos = System.nanoTime() - startNanos
            val elapsedSeconds = elapsedNanos.toDouble() / NANOS_PER_SECOND
            val durationMs = elapsedNanos / NANOS_PER_MILLI
            val tokensPerSecond =
                if (elapsedSeconds > 0.0 && tokenCount > 0) tokenCount / elapsedSeconds else 0.0

            val device = deviceProvider.getDeviceFingerprint()
            val benchmarkedAt = System.currentTimeMillis()
            metaRepository.saveBenchmarkResult(modelId, device, tokensPerSecond, benchmarkedAt)

            return BenchmarkResult(
                tokensPerSecond = tokensPerSecond,
                totalTokens = tokenCount,
                durationMs = durationMs,
            )
        }
    }
