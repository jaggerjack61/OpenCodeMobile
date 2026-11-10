package com.opencode.mobile.ui

import java.time.Instant
import java.time.ZoneOffset
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

    @Test
    fun groupsChatsByCalendarDay() {
        val noon = Instant.parse("2026-09-10T12:00:00Z").toEpochMilli()
        val hour = 60 * 60_000L
        val utc = ZoneOffset.UTC
        assertEquals(DateBucket.TODAY, dateBucket(noon - 11 * hour, noon, utc))
        assertEquals(DateBucket.YESTERDAY, dateBucket(noon - 13 * hour, noon, utc))
        assertEquals(DateBucket.WEEK, dateBucket(noon - 7 * 24 * hour, noon, utc))
        assertEquals(DateBucket.OLDER, dateBucket(noon - 8 * 24 * hour, noon, utc))
        // A clock that moved backwards must not hide a chat.
        assertEquals(DateBucket.TODAY, dateBucket(noon + 48 * hour, noon, utc))
    }
}
