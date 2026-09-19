package com.locus.core.ai.di

import android.content.Context
import androidx.work.WorkManager
import com.locus.core.ai.BuildConfig
import com.locus.core.ai.embedding.EmbeddingRunner
import com.locus.core.ai.llama.DefaultDeviceFingerprintProvider
import com.locus.core.ai.llama.DeviceFingerprintProvider
import com.locus.core.ai.llama.LlamaRuntime
import com.locus.core.ai.llama.LocalLlamaChatModelClient
import com.locus.core.ai.llama.ModelDownloader
import com.locus.core.ai.models.DefaultModelManagerRepository
import com.locus.core.ai.models.DefaultModelRegistry
import com.locus.core.ai.providers.GeminiAdapter
import com.locus.core.ai.providers.OpenAiCompatibleAdapter
import com.locus.core.domain.chat.ActiveModelRepository
import com.locus.core.domain.chat.ChatModelClient
import com.locus.core.domain.chat.RagAnswerUseCase
import com.locus.core.domain.models.ModelManagerRepository
import com.locus.core.domain.models.ModelRegistry
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.providers.ProviderAdapter
import com.locus.core.domain.search.EmbeddingGateway
import com.locus.core.domain.search.HybridSearchUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    @Binds
    @Singleton
    @LocalChat
    abstract fun bindLocalChatModelClient(client: LocalLlamaChatModelClient): ChatModelClient

    @Binds
    @Singleton
    abstract fun bindModelManagerRepository(impl: DefaultModelManagerRepository): ModelManagerRepository

    @Binds
    @Singleton
    abstract fun bindDeviceFingerprintProvider(impl: DefaultDeviceFingerprintProvider): DeviceFingerprintProvider

    @Binds @Singleton
    abstract fun bindModelRegistry(impl: DefaultModelRegistry): ModelRegistry

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

        @Provides
        @Singleton
        fun provideWorkManager(
            @ApplicationContext context: Context,
        ): WorkManager = WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun provideProviderAdapter(client: OkHttpClient): ProviderAdapter =
            if (BuildConfig.DEV_API_KEY.isNotBlank()) {
                GeminiAdapter(
                    apiKey = BuildConfig.DEV_API_KEY,
                    model = BuildConfig.DEV_MODEL.ifBlank { "gemini-3.5-flash-lite" },
                    client = client,
                )
            } else {
                OpenAiCompatibleAdapter(
                    baseUrl = "https://api.openai.com/v1",
                    client = client,
                )
            }

        @Provides
        @Singleton
        @CloudChat
        fun provideCloudChatModelClient(providerAdapter: ProviderAdapter): ChatModelClient = providerAdapter

        @Provides
        @Singleton
        fun provideRagAnswerUseCase(
            hybridSearch: HybridSearchUseCase,
            providerAdapter: ProviderAdapter,
            noteRepository: NoteRepository,
            activeModelRepository: ActiveModelRepository,
            @LocalChat localChatClient: ChatModelClient,
        ): RagAnswerUseCase =
            RagAnswerUseCase(
                hybridSearch = hybridSearch,
                providerAdapter = providerAdapter,
                noteRepository = noteRepository,
                activeModelRepository = activeModelRepository,
                localChatClient = localChatClient,
            )
    }
}
