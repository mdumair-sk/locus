package com.locus.core.domain.reminders

import java.time.Instant
import java.time.ZoneOffset

object ReminderRecurrence {
    fun nextTrigger(
        previous: Instant,
        rule: RepeatRule,
    ): Instant? =
        when (rule) {
            RepeatRule.NONE -> null
            RepeatRule.DAILY -> previous.atZone(ZoneOffset.UTC).plusDays(1).toInstant()
            RepeatRule.WEEKLY -> previous.atZone(ZoneOffset.UTC).plusWeeks(1).toInstant()
            RepeatRule.MONTHLY -> previous.atZone(ZoneOffset.UTC).plusMonths(1).toInstant()
        }
}
