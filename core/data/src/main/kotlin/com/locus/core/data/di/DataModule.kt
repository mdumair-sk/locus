package com.locus.core.data.di

import com.locus.core.data.files.AndroidSafNoteFileSource
import com.locus.core.data.files.DataStoreTreeUriStore
import com.locus.core.data.files.SafNoteFileSource
import com.locus.core.data.files.SafNoteRepository
import com.locus.core.data.files.TreeUriStore
import com.locus.core.domain.notes.FrontmatterParser
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.notes.SnakeYamlCodec
import com.locus.core.domain.notes.YamlCodec
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: SafNoteRepository): NoteRepository

    @Binds
    @Singleton
    abstract fun bindYamlCodec(impl: SnakeYamlCodec): YamlCodec

    @Binds
    @Singleton
    abstract fun bindSafNoteFileSource(impl: AndroidSafNoteFileSource): SafNoteFileSource

    @Binds
    @Singleton
    abstract fun bindTreeUriStore(impl: DataStoreTreeUriStore): TreeUriStore

    companion object {
        @Provides
        @Singleton
        fun provideFrontmatterParser(yamlCodec: YamlCodec): FrontmatterParser = FrontmatterParser(yamlCodec)
    }
}
