package com.locus.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        NoteIndexEntity::class,
        NoteFtsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LocusDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}
