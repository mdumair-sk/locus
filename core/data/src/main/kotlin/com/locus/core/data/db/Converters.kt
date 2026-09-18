package com.locus.core.data.db

import androidx.room.TypeConverter
import com.locus.core.domain.chat.ChatRole
import com.locus.core.domain.notes.NoteType
import com.locus.core.domain.reminders.RepeatRule
import com.locus.core.domain.reminders.SchedulingTier
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter fun toTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter fun fromNoteType(value: String?): NoteType? = value?.let { NoteType.valueOf(it) }

    @TypeConverter fun toNoteType(noteType: NoteType?): String? = noteType?.name

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter fun toStringList(list: List<String>?): String = list?.joinToString(",") ?: ""

    @TypeConverter
    fun fromRepeatRule(value: String?): RepeatRule? = value?.let { RepeatRule.valueOf(it) }

    @TypeConverter fun toRepeatRule(rule: RepeatRule?): String? = rule?.name

    @TypeConverter
    fun fromSchedulingTier(value: String?): SchedulingTier? = value?.let { SchedulingTier.valueOf(it) }

    @TypeConverter fun toSchedulingTier(tier: SchedulingTier?): String? = tier?.name

    @TypeConverter fun fromChatRole(value: String?): ChatRole? = value?.let { ChatRole.valueOf(it) }

    @TypeConverter fun toChatRole(role: ChatRole?): String? = role?.name
}
