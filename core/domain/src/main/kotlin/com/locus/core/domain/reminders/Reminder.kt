package com.locus.core.domain.reminders

import java.time.Instant

enum class RepeatRule {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

data class Reminder(
    val id: String,
    val noteId: String,
    val checklistLineIndex: Int?,
    val label: String,
    val firstTrigger: Instant,
    val repeat: RepeatRule,
)
