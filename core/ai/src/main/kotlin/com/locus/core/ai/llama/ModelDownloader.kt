package com.locus.core.ai.llama

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
    ) {
        companion object {
            const val DEFAULT_HF_REPO = "ggml-org/embeddinggemma-300M-GGUF"
            const val DEFAULT_MODEL_FILENAME = "embeddinggemma-300M-Q8_0.gguf"
            private const val BUFFER_SIZE = 8192
        }

        fun getModelFile(filename: String = DEFAULT_MODEL_FILENAME): File {
            val dir = File(context.filesDir, "models")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return File(dir, filename)
        }

        suspend fun downloadModelIfMissing(
            repo: String = DEFAULT_HF_REPO,
            filename: String = DEFAULT_MODEL_FILENAME,
        ): File =
            withContext(Dispatchers.IO) {
                val targetFile = getModelFile(filename)
                if (targetFile.exists() && targetFile.length() > 0L) {
                    return@withContext targetFile
                }

                val expectedSha256 = fetchExpectedSha256(repo, filename)
                downloadAndVerify(repo, filename, targetFile, expectedSha256)
                targetFile
            }

        private fun fetchExpectedSha256(
            repo: String,
            filename: String,
        ): String {
            val treeUrl = "https://huggingface.co/api/models/$repo/tree/main"
            val request = Request.Builder().url(treeUrl).build()
            val response = okHttpClient.newCall(request).execute()
            check(response.isSuccessful) {
                "Failed to fetch model tree from $treeUrl: HTTP ${response.code}"
            }
            val bodyString = response.body?.string() ?: error("Empty response from $treeUrl")
            val jsonArray = JSONArray(bodyString)
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val path = item.optString("path")
                if (path.equals(filename, ignoreCase = true)) {
                    val lfs = item.optJSONObject("lfs")
                    val oid = lfs?.optString("oid") ?: item.optString("oid")
                    if (!oid.isNullOrBlank()) {
                        return oid
                    }
                }
            }
            error("Model $filename not found in HF tree listing at $treeUrl")
        }

        private fun downloadAndVerify(
            repo: String,
            filename: String,
            targetFile: File,
            expectedSha256: String,
        ) {
            val parentDir = targetFile.parentFile ?: context.filesDir
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            val tempFile = File(parentDir, "$filename.tmp")
            if (tempFile.exists()) {
                tempFile.delete()
            }

            var verified = false
            try {
                val downloadUrl = "https://huggingface.co/$repo/resolve/main/$filename"
                val actualSha256 = downloadToFileWithSha256(downloadUrl, tempFile)
                check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    "Checksum mismatch for $filename: expected $expectedSha256 but got $actualSha256"
                }
                promoteTempFile(tempFile, targetFile)
                verified = true
            } finally {
                if (!verified && tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

        private fun downloadToFileWithSha256(
            url: String,
            destination: File,
        ): String {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            check(response.isSuccessful) { "Failed to download model from $url: HTTP ${response.code}" }
            val responseBody = response.body ?: error("Empty response body from $url")

            val digest = MessageDigest.getInstance("SHA-256")
            responseBody.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun promoteTempFile(
            tempFile: File,
            targetFile: File,
        ) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        }
    }
