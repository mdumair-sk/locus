package com.locus.core.ai.catalog

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CatalogRefreshWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val catalogRepository: CatalogRepository,
    ) : CoroutineWorker(appContext, params) {
        companion object {
            private const val TAG = "CatalogRefreshWorker"
            private const val MAX_RETRY_ATTEMPTS = 3
        }

        override suspend fun doWork(): Result {
            Log.d(TAG, "Starting periodic catalog refresh")
            val result = catalogRepository.refreshFromRemote()
            return if (result.isSuccess) {
                Log.d(TAG, "Periodic catalog refresh succeeded")
                Result.success()
            } else {
                val error = result.exceptionOrNull()
                Log.w(TAG, "Periodic catalog refresh failed: ${error?.message}")
                if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        }
    }
