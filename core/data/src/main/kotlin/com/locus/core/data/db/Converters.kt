package com.locus.core.data.db

import androidx.room.TypeConverter
import com.locus.core.domain.notes.NoteType
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromNoteType(value: String?): NoteType? = value?.let { NoteType.valueOf(it) }

    @TypeConverter
    fun toNoteType(noteType: NoteType?): String? = noteType?.name

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String = list?.joinToString(",") ?: ""
}
