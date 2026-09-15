package com.locus.core.domain.time

import java.time.Instant

fun interface Clock {
    fun now(): Instant
}
