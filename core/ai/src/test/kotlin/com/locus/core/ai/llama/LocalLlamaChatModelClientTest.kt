package com.locus.core.ai.llama

import com.locus.core.domain.chat.ChatModelClient
import com.locus.core.domain.providers.StreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLlamaChatModelClientTest {
    private class FakeLlamaRuntime(
        private val tokenFlow: Flow<String>,
    ) : LlamaRuntime() {
        override fun generateStream(
            prompt: String,
            samplingParams: SamplingParams,
        ): Flow<String> = tokenFlow
    }

    @Suppress("USELESS_IS_CHECK")
    @Test
    fun implementsChatModelClient() {
        val runtime = LlamaRuntime()
        val client = LocalLlamaChatModelClient(runtime)
        assertTrue(client is ChatModelClient)
    }

    @Test
    fun generate_emitsTokenDeltasAndDoneEvent() =
        runTest {
            val tokens = listOf("Hello", ", ", "offline", " world!")
            val runtime = FakeLlamaRuntime(flowOf(*tokens.toTypedArray()))
            val client = LocalLlamaChatModelClient(runtime)

            val events = client.generate("test prompt").toList()

            assertEquals(5, events.size)
            assertEquals(StreamEvent.TokenDelta("Hello"), events[0])
            assertEquals(StreamEvent.TokenDelta(", "), events[1])
            assertEquals(StreamEvent.TokenDelta("offline"), events[2])
            assertEquals(StreamEvent.TokenDelta(" world!"), events[3])
            assertEquals(StreamEvent.Done("stop"), events[4])
        }

    @Test
    fun generate_whenRuntimeThrows_catchesAndEmitsError() =
        runTest {
            val runtime =
                FakeLlamaRuntime(
                    flow {
                        emit("partial")
                        error("Llama JNI crashed")
                    },
                )
            val client = LocalLlamaChatModelClient(runtime)

            val events = client.generate("test prompt").toList()

            assertEquals(2, events.size)
            assertEquals(StreamEvent.TokenDelta("partial"), events[0])
            assertTrue(events[1] is StreamEvent.Error)
            assertEquals("Llama JNI crashed", (events[1] as StreamEvent.Error).message)
        }
}
