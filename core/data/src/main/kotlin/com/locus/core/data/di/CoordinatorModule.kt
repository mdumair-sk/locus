package com.locus.core.data.di

import com.locus.core.data.files.SafNoteFileWriter
import com.locus.core.data.index.RoomIndexUpdateQueue
import com.locus.core.domain.notes.IndexUpdateQueue
import com.locus.core.domain.notes.NoteFileWriter
import com.locus.core.domain.notes.NoteFlushCoordinator
import com.locus.core.domain.time.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class CoordinatorModule {
    @Binds
    @Singleton
    abstract fun bindNoteFileWriter(impl: SafNoteFileWriter): NoteFileWriter

    @Binds
    @Singleton
    abstract fun bindIndexUpdateQueue(impl: RoomIndexUpdateQueue): IndexUpdateQueue

    companion object {
        @Provides
        @Singleton
        fun provideDispatcherProvider(): DispatcherProvider =
            object : DispatcherProvider {
                override val io: CoroutineDispatcher = Dispatchers.IO
                override val default: CoroutineDispatcher = Dispatchers.Default
                override val main: CoroutineDispatcher = Dispatchers.Main
                override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
            }

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(dispatchers: DispatcherProvider): CoroutineScope =
            CoroutineScope(
                SupervisorJob() + dispatchers.default,
            )

        @Provides
        @Singleton
        fun provideNoteFlushCoordinator(
            fileWriter: NoteFileWriter,
            indexQueue: IndexUpdateQueue,
            dispatchers: DispatcherProvider,
            @ApplicationScope scope: CoroutineScope,
        ): NoteFlushCoordinator =
            NoteFlushCoordinator(
                fileWriter = fileWriter,
                indexQueue = indexQueue,
                dispatchers = dispatchers,
                scope = scope,
            )
    }
}
