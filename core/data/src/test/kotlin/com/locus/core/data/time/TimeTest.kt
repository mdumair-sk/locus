package com.locus.core.data.time

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TimeTest {
    @Test
    fun androidDispatcherProvider_providesExpectedDispatchers() {
        val provider = AndroidDispatcherProvider()
        assertEquals(Dispatchers.IO, provider.io)
        assertEquals(Dispatchers.Default, provider.default)
        assertEquals(Dispatchers.Main, provider.main)
        assertEquals(Dispatchers.Main.immediate, provider.mainImmediate)
    }

    @Test
    fun systemClock_returnsCurrentInstant() {
        val clock = SystemClock()
        val before = Instant.now()
        val now = clock.now()
        val after = Instant.now()

        assertNotNull(now)
        assertTrue(!now.isBefore(before))
        assertTrue(!now.isAfter(after))
    }
}
