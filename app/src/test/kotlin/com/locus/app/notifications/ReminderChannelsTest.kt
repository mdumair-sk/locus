package com.locus.app.notifications

import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ReminderChannelsTest {
    @Test
    fun createAllRegistersRemindersChannel() {
        val context = RuntimeEnvironment.getApplication()
        ReminderChannels.createAll(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(ReminderChannels.CHANNEL_REMINDERS)

        assertNotNull(channel)
        assertEquals("Reminders", channel.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }
}
