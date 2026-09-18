package com.locus.core.ai.llama

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class ModelDownloadWorkerTest {
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
    fun doWork_missingInput_returnsFailure() =
        runTest {
            val downloader = ModelDownloader(context, OkHttpClient())
            val worker =
                TestListenableWorkerBuilder<ModelDownloadWorker>(context)
                    .setWorkerFactory(
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker? {
                                if (workerClassName == ModelDownloadWorker::class.java.name
                                ) {
                                    return ModelDownloadWorker(
                                        appContext,
                                        workerParameters,
                                        downloader,
                                    )
                                }
                                return null
                            }
                        },
                    ).setInputData(Data.EMPTY)
                    .build()

            val result = worker.doWork()
            assertTrue(result is ListenableWorker.Result.Failure)
        }

    @Test
    fun doWork_validInput_downloadsAndReturnsSuccess() =
        runTest {
            val filename = "worker-test.gguf"
            val content = "valid gguf binary content from worker"
            val sha256 =
                MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString(
                    "",
                ) { "%02x".format(it) }

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
                                    content.toResponseBody(
                                        "application/octet-stream".toMediaType(),
                                    ),
                                ).build()
                        },
                    ).build()

            val downloader = ModelDownloader(context, mockClient)
            val inputData =
                Data
                    .Builder()
                    .putString(ModelDownloadWorker.KEY_REPO, "test-repo/model")
                    .putString(ModelDownloadWorker.KEY_FILENAME, filename)
                    .putString(ModelDownloadWorker.KEY_SHA256, sha256)
                    .putLong(ModelDownloadWorker.KEY_EXPECTED_SIZE, content.length.toLong())
                    .build()

            val worker =
                TestListenableWorkerBuilder<ModelDownloadWorker>(context)
                    .setWorkerFactory(
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker? {
                                if (workerClassName == ModelDownloadWorker::class.java.name
                                ) {
                                    return ModelDownloadWorker(
                                        appContext,
                                        workerParameters,
                                        downloader,
                                    )
                                }
                                return null
                            }
                        },
                    ).setInputData(inputData)
                    .build()

            val result = worker.doWork()
            assertTrue(result is ListenableWorker.Result.Success)

            val targetFile = downloader.getModelFile(filename)
            assertTrue(targetFile.exists())
            assertEquals(content, targetFile.readText())
        }

    @Test
    fun doWork_downloadFails_returnsFailure() =
        runTest {
            val filename = "worker-fail-test.gguf"
            val mockClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            Response
                                .Builder()
                                .request(chain.request())
                                .protocol(Protocol.HTTP_1_1)
                                .code(500)
                                .message("Server Error")
                                .body(
                                    "Server Error".toResponseBody(
                                        "text/plain".toMediaType(),
                                    ),
                                ).build()
                        },
                    ).build()

            val downloader = ModelDownloader(context, mockClient)
            val inputData =
                Data
                    .Builder()
                    .putString(ModelDownloadWorker.KEY_REPO, "test-repo/model")
                    .putString(ModelDownloadWorker.KEY_FILENAME, filename)
                    .putString(ModelDownloadWorker.KEY_SHA256, "dummy-sha")
                    .build()

            val worker =
                TestListenableWorkerBuilder<ModelDownloadWorker>(context)
                    .setWorkerFactory(
                        object : WorkerFactory() {
                            override fun createWorker(
                                appContext: Context,
                                workerClassName: String,
                                workerParameters: WorkerParameters,
                            ): ListenableWorker? {
                                if (workerClassName == ModelDownloadWorker::class.java.name
                                ) {
                                    return ModelDownloadWorker(
                                        appContext,
                                        workerParameters,
                                        downloader,
                                    )
                                }
                                return null
                            }
                        },
                    ).setInputData(inputData)
                    .build()

            val result = worker.doWork()
            assertTrue(result is ListenableWorker.Result.Failure)
        }
}
