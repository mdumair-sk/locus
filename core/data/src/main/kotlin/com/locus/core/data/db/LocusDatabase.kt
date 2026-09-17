package com.locus.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.locus.core.data.reminders.ReminderDao
import com.locus.core.data.reminders.ReminderEntity

@Database(
    entities =
        [
            NoteIndexEntity::class,
            NoteFtsEntity::class,
            ReminderEntity::class,
        ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LocusDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun reminderDao(): ReminderDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `reminders` (
                            `id` TEXT NOT NULL,
                            `noteId` TEXT NOT NULL,
                            `checklistLineIndex` INTEGER,
                            `label` TEXT NOT NULL,
                            `firstTrigger` INTEGER NOT NULL,
                            `repeat` TEXT NOT NULL,
                            `scheduledTier` TEXT NOT NULL,
                            `active` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
