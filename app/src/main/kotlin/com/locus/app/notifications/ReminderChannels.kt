package com.locus.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ReminderChannels {
    const val CHANNEL_REMINDERS = "reminders"

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            val remindersChannel =
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Reminders",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description =
                        "Notifications for scheduled note reminders and alarms"
                    enableVibration(true)
                }

            notificationManager.createNotificationChannel(remindersChannel)
        }
    }
}
