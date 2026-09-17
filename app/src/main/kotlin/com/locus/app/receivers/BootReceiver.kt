package com.locus.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.locus.core.data.reminders.PermissionRevocationMonitor
import com.locus.core.data.reminders.ReminderDao
import com.locus.core.domain.reminders.AlarmScheduler
import com.locus.core.domain.reminders.Reminder
import com.locus.core.domain.time.DispatcherProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var reminderDao: ReminderDao

    @Inject lateinit var alarmScheduler: AlarmScheduler

    @Inject lateinit var permissionRevocationMonitor: PermissionRevocationMonitor

    @Inject lateinit var dispatchers: DispatcherProvider

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + dispatchers.io).launch {
            try {
                rearmAllReminders()
            } finally {
                pendingResult.finish()
            }
        }
    }

    @VisibleForTesting
    suspend fun rearmAllReminders() {
        // First, check if exact permission was revoked across boot/update
        permissionRevocationMonitor.checkAndDowngrade()

        // Re-arm all active reminders
        val active = reminderDao.getActiveReminders()
        for (entity in active) {
            val reminder =
                Reminder(
                    id = entity.id,
                    noteId = entity.noteId,
                    checklistLineIndex = entity.checklistLineIndex,
                    label = entity.label,
                    firstTrigger = entity.firstTrigger,
                    repeat = entity.repeat,
                )
            alarmScheduler.schedule(reminder, entity.scheduledTier)
        }
    }
}
