package com.locus.core.ai.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mutex-guarded single-model-loaded runtime bridging to native llama.cpp JNI shim. Establishes
 * runtime-ownership contract (M-1) serving embedding and future chat generation (Prompt 41).
 */
@Singleton
class LlamaRuntime
    @Inject
    constructor() {
        private val mutex = Mutex()
        private var isLoaded = false

        @Suppress("SwallowedException")
        companion object {
            init {
                try {
                    System.loadLibrary("locus_llama_jni")
                } catch (e: UnsatisfiedLinkError) {
                    // ponytail: graceful fallback for host PC JVM unit tests where Android .so is absent
                }
            }
        }

        suspend fun loadModel(path: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    runCatching {
                        val file = File(path)
                        check(file.exists() && file.isFile) { "Model file not found or invalid at path: $path" }
                        if (isLoaded) {
                            nativeUnload()
                            isLoaded = false
                        }
                        val success = nativeLoadModel(path)
                        check(success) { "Failed to load llama model from: $path" }
                        isLoaded = true
                    }
                }
            }

        suspend fun embed(text: String): Result<FloatArray> =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    runCatching {
                        check(isLoaded) { "No model is currently loaded in LlamaRuntime" }
                        nativeEmbed(text) ?: error("Native embedding generation returned null")
                    }
                }
            }

        suspend fun unload(): Unit =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    if (isLoaded) {
                        nativeUnload()
                        isLoaded = false
                    }
                }
            }

        private external fun nativeLoadModel(path: String): Boolean

        private external fun nativeEmbed(text: String): FloatArray?

        private external fun nativeUnload()
    }
