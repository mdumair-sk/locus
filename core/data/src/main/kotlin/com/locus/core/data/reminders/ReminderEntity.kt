package com.locus.core.data.reminders

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.locus.core.domain.reminders.RepeatRule
import com.locus.core.domain.reminders.SchedulingTier
import java.time.Instant

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val checklistLineIndex: Int?,
    val label: String,
    val firstTrigger: Instant,
    val repeat: RepeatRule,
    val scheduledTier: SchedulingTier,
    val active: Boolean = true,
)
