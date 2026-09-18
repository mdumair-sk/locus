package com.locus.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.locus.core.data.reminders.ReminderDao
import com.locus.core.data.reminders.ReminderEntity
import com.locus.core.data.vector.ChunkDao
import com.locus.core.data.vector.ChunkEntity
import com.locus.core.data.vector.EmbeddingConverters

@Database(
    entities =
        [
            NoteIndexEntity::class,
            NoteFtsEntity::class,
            ReminderEntity::class,
            ChunkEntity::class,
        ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class, EmbeddingConverters::class)
abstract class LocusDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    abstract fun reminderDao(): ReminderDao

    abstract fun chunkDao(): ChunkDao

    companion object {
        private const val VERSION_1 = 1
        private const val VERSION_2 = 2
        private const val VERSION_3 = 3

        val MIGRATION_1_2 =
            object : Migration(VERSION_1, VERSION_2) {
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

        val MIGRATION_2_3 =
            object : Migration(VERSION_2, VERSION_3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `chunks` (
                            `chunkId` TEXT NOT NULL,
                            `noteId` TEXT NOT NULL,
                            `headingPathJson` TEXT NOT NULL,
                            `text` TEXT NOT NULL,
                            `embedding` BLOB NOT NULL,
                            `embeddingModelId` TEXT NOT NULL,
                            `sourceChecksum` TEXT NOT NULL,
                            PRIMARY KEY(`chunkId`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_chunks_noteId` ON `chunks` (`noteId`)",
                    )
                }
            }
    }
}
