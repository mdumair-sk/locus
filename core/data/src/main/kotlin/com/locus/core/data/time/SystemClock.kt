package com.locus.core.data.time

import com.locus.core.domain.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemClock
    @Inject
    constructor() : Clock {
        override fun now(): Instant = Instant.now()
    }
