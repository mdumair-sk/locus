package com.locus.core.ai.llama

import android.content.Context
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class ModelDownloaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        val modelsDir = File(context.filesDir, "models")
        if (modelsDir.exists()) {
            modelsDir.deleteRecursively()
        }
    }

    @Test
    fun downloadModelIfMissing_whenFileAlreadyExists_returnsExistingFileWithoutNetworkCalls() =
        runTest {
            val modelsDir = File(context.filesDir, "models")
            modelsDir.mkdirs()
            val existingFile = File(modelsDir, ModelDownloader.DEFAULT_MODEL_FILENAME)
            existingFile.writeText("pre-existing model bytes")

            val failingClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor {
                            error(
                                "Network call attempted when model file already exists!",
                            )
                        },
                    ).build()

            val downloader = ModelDownloader(context, failingClient)
            val resultFile = downloader.downloadModelIfMissing()

            assertEquals(existingFile.absolutePath, resultFile.absolutePath)
            assertTrue(resultFile.exists())
            assertEquals("pre-existing model bytes", resultFile.readText())
        }

    @Test
    fun downloadModelIfMissing_downloadsAndVerifiesChecksumSuccessfully() =
        runTest {
            val modelContent = "simulated gguf model binary payload"
            val digest = MessageDigest.getInstance("SHA-256")
            val sha256 = digest.digest(modelContent.toByteArray()).joinToString("") { "%02x".format(it) }

            val treeJson =
                """
                [
                  {
                    "type": "file",
                    "path": "${ModelDownloader.DEFAULT_MODEL_FILENAME}",
                    "lfs": {
                      "oid": "$sha256",
                      "size": ${modelContent.length}
                    }
                  }
                ]
                """.trimIndent()

            val mockClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val url = chain.request().url.toString()
                            if (url.contains("/tree/")) {
                                Response
                                    .Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(
                                        treeJson.toResponseBody(
                                            "application/json".toMediaType(),
                                        ),
                                    ).build()
                            } else {
                                Response
                                    .Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(
                                        modelContent.toResponseBody(
                                            "application/octet-stream".toMediaType(),
                                        ),
                                    ).build()
                            }
                        },
                    ).build()

            val downloader = ModelDownloader(context, mockClient)
            val downloadedFile = downloader.downloadModelIfMissing()

            assertTrue(downloadedFile.exists())
            assertEquals(modelContent, downloadedFile.readText())
        }

    @Test
    fun downloadModelIfMissing_checksumMismatch_throwsExceptionAndDeletesTempFile() =
        runTest {
            val modelContent = "actual downloaded content"
            val fakeSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

            val treeJson =
                """
                [
                  {
                    "type": "file",
                    "path": "${ModelDownloader.DEFAULT_MODEL_FILENAME}",
                    "lfs": {
                      "oid": "$fakeSha256",
                      "size": ${modelContent.length}
                    }
                  }
                ]
                """.trimIndent()

            val mockClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val url = chain.request().url.toString()
                            if (url.contains("/tree/")) {
                                Response
                                    .Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(
                                        treeJson.toResponseBody(
                                            "application/json".toMediaType(),
                                        ),
                                    ).build()
                            } else {
                                Response
                                    .Builder()
                                    .request(chain.request())
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(200)
                                    .message("OK")
                                    .body(
                                        modelContent.toResponseBody(
                                            "application/octet-stream".toMediaType(),
                                        ),
                                    ).build()
                            }
                        },
                    ).build()

            val downloader = ModelDownloader(context, mockClient)
            val result = runCatching { downloader.downloadModelIfMissing() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            val targetFile = downloader.getModelFile()
            assertFalse(targetFile.exists())
            val tempFile = File(targetFile.parentFile, "${ModelDownloader.DEFAULT_MODEL_FILENAME}.tmp")
            assertFalse(tempFile.exists())
        }

    @Test
    fun downloadModelIfMissing_missingInTreeListing_throwsException() =
        runTest {
            val treeJson = "[]"
            val mockClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            Response
                                .Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .body(
                                    treeJson.toResponseBody(
                                        "application/json".toMediaType(),
                                    ),
                                ).build()
                        },
                    ).build()

            val downloader = ModelDownloader(context, mockClient)
            val result = runCatching { downloader.downloadModelIfMissing() }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }
}
