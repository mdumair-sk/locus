package com.locus.core.data.di

import com.locus.core.data.time.AndroidDispatcherProvider
import com.locus.core.data.time.SystemClock
import com.locus.core.domain.time.Clock
import com.locus.core.domain.time.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: AndroidDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock
}
