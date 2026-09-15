package com.locus.core.data.time

import com.locus.core.domain.time.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDispatcherProvider
    @Inject
    constructor() : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
        override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
    }
