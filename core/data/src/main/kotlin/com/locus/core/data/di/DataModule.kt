package com.locus.core.data.di

import android.content.Context
import androidx.room.Room
import com.locus.core.data.backup.BackupPreferencesStore
import com.locus.core.data.backup.SafImportExportRepository
import com.locus.core.data.db.LocusDatabase
import com.locus.core.data.db.NoteDao
import com.locus.core.data.files.AndroidSafNoteFileSource
import com.locus.core.data.files.DataStoreTreeUriStore
import com.locus.core.data.files.SafNoteFileSource
import com.locus.core.data.files.SafNoteRepository
import com.locus.core.data.files.TreeUriStore
import com.locus.core.data.reminders.AndroidAlarmScheduler
import com.locus.core.data.reminders.ReminderDao
import com.locus.core.data.search.RoomKeywordSearch
import com.locus.core.domain.backup.BackupSettingsRepository
import com.locus.core.domain.backup.ImportExportRepository
import com.locus.core.domain.notes.FrontmatterParser
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.notes.SnakeYamlCodec
import com.locus.core.domain.notes.YamlCodec
import com.locus.core.domain.reminders.AlarmScheduler
import com.locus.core.domain.search.KeywordSearch
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds @Singleton
    abstract fun bindNoteRepository(impl: SafNoteRepository): NoteRepository

    @Binds @Singleton
    abstract fun bindYamlCodec(impl: SnakeYamlCodec): YamlCodec

    @Binds
    @Singleton
    abstract fun bindSafNoteFileSource(impl: AndroidSafNoteFileSource): SafNoteFileSource

    @Binds @Singleton
    abstract fun bindTreeUriStore(impl: DataStoreTreeUriStore): TreeUriStore

    @Binds @Singleton
    abstract fun bindKeywordSearch(impl: RoomKeywordSearch): KeywordSearch

    @Binds
    @Singleton
    abstract fun bindBackupSettingsRepository(impl: BackupPreferencesStore): BackupSettingsRepository

    @Binds
    @Singleton
    abstract fun bindImportExportRepository(impl: SafImportExportRepository): ImportExportRepository

    @Binds @Singleton
    abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler

    companion object {
        @Provides
        @Singleton
        fun provideFrontmatterParser(yamlCodec: YamlCodec): FrontmatterParser = FrontmatterParser(yamlCodec)

        @Provides
        @Singleton
        fun provideLocusDatabase(
            @ApplicationContext context: Context,
        ): LocusDatabase =
            Room
                .databaseBuilder(
                    context,
                    LocusDatabase::class.java,
                    "locus.db",
                ).addMigrations(LocusDatabase.MIGRATION_1_2)
                .build()

        @Provides fun provideNoteDao(database: LocusDatabase): NoteDao = database.noteDao()

        @Provides
        fun provideReminderDao(database: LocusDatabase): ReminderDao = database.reminderDao()
    }
}
