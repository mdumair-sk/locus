package com.locus.core.ai.di

import com.locus.core.ai.embedding.EmbeddingRunner
import com.locus.core.ai.llama.LlamaRuntime
import com.locus.core.ai.llama.ModelDownloader
import com.locus.core.domain.search.EmbeddingGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class EmbeddingRunnerGatewayAdapter
    @Inject
    constructor(
        private val runner: EmbeddingRunner,
        private val modelDownloader: ModelDownloader,
        private val runtime: LlamaRuntime,
    ) : EmbeddingGateway {
        private val initMutex = Mutex()

        @Volatile private var isInitialized = false

        override suspend fun embed(text: String): FloatArray {
            ensureModelLoaded()
            return runner.embed(text)
        }

        private suspend fun ensureModelLoaded() {
            if (!isInitialized) {
                initMutex.withLock {
                    if (!isInitialized) {
                        val modelFile = modelDownloader.downloadModelIfMissing()
                        val loadResult = runtime.loadModel(modelFile.absolutePath)
                        check(loadResult.isSuccess) {
                            "Failed to load embedding model from ${modelFile.absolutePath}: " +
                                loadResult.exceptionOrNull()?.message
                        }
                        isInitialized = true
                    }
                }
            }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    @Singleton
    internal abstract fun bindEmbeddingGateway(adapter: EmbeddingRunnerGatewayAdapter): EmbeddingGateway

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()
    }
}
