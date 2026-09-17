package com.locus.app.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.locus.core.data.backup.BackupWorker
import com.locus.core.domain.backup.BackupInterval
import java.util.concurrent.TimeUnit

object WorkScheduling {
    const val PERIODIC_BACKUP_WORK_NAME = "com.locus.app.backup.periodic"
    const val ON_DEMAND_BACKUP_WORK_NAME = "com.locus.app.backup.on_demand"

    fun schedulePeriodicBackup(
        context: Context,
        interval: BackupInterval,
    ): Operation {
        val workManager = WorkManager.getInstance(context)
        if (interval == BackupInterval.OFF) {
            return workManager.cancelUniqueWork(PERIODIC_BACKUP_WORK_NAME)
        }

        val constraints =
            Constraints
                .Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()

        val request =
            PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = interval.days,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            ).setConstraints(constraints)
                .build()

        return workManager.enqueueUniquePeriodicWork(
            PERIODIC_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun triggerOnDemandBackup(context: Context): Operation {
        val workManager = WorkManager.getInstance(context)
        val request =
            OneTimeWorkRequestBuilder<BackupWorker>()
                .build()

        return workManager.enqueueUniqueWork(
            ON_DEMAND_BACKUP_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
