package com.opencode.mobile.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val now = Instant.parse("2026-09-01T00:00:00Z").toEpochMilli()

    @Test
    fun formatsResetCountdowns() {
        assertEquals("Resetting now", resetLabel(now, now))
        assertEquals("Resets in 45m", resetLabel(now + 45 * 60_000L, now))
        assertEquals("Resets in 2h 30m", resetLabel(now + 150 * 60_000L, now))
        assertEquals("Resets in 1d 3h", resetLabel(now + 27 * 60 * 60_000L, now))
    }

    @Test
    fun formatsSubMinuteReset() {
        assertEquals("Resets in <1m", resetLabel(now + 30_000L, now))
    }
}
