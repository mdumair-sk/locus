package com.locus.core.ai.llama

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaRuntimeTest {
    @Test
    fun embedWithoutLoadedModelReturnsFailure() =
        runTest {
            val runtime = LlamaRuntime()
            val result = runtime.embed("hello")
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }

    @Test
    fun loadModelWithNonExistentPathReturnsFailure() =
        runTest {
            val runtime = LlamaRuntime()
            val result = runtime.loadModel("/non/existent/model.gguf")
            assertTrue(result.isFailure)
        }

    @Test
    fun unloadWhenNotLoadedIsIdempotentAndSafe() =
        runTest {
            val runtime = LlamaRuntime()
            runtime.unload()
            val result = runtime.embed("test")
            assertTrue(result.isFailure)
        }
}
