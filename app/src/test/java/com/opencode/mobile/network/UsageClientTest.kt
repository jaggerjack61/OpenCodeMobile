package com.opencode.mobile.network

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class UsageClientTest {
    @Test
    fun parsesAllWindows() {
        val body = """
            {
              "usage": {
                "rolling": { "status": "ok", "percent": 12.5, "resetsAt": "2026-09-01T00:00:00Z" },
                "weekly": { "status": "ok", "percent": 78, "resetsAt": "2026-09-03T12:00:00Z" },
                "monthly": { "status": "ok", "percent": 94, "resetsAt": "2026-10-01T00:00:00Z" }
              }
            }
        """.trimIndent()

        val usage = parseUsage(body)

        assertEquals(12.5f, usage.rolling?.percent)
        assertEquals(78f, usage.weekly?.percent)
        assertEquals(94f, usage.monthly?.percent)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(), usage.rolling?.resetsAt)
        assertEquals("ok", usage.monthly?.status)
    }

    @Test
    fun clampsPercentAndIgnoresUnknownFields() {
        val body = """
            {
              "extra": true,
              "usage": {
                "rolling": { "status": "ok", "percent": 150, "resetsAt": "2026-09-01T00:00:00Z", "other": 1 },
                "weekly": { "status": "ok", "percent": -5, "resetsAt": "2026-09-01T00:00:00Z" }
              }
            }
        """.trimIndent()

        val usage = parseUsage(body)

        assertEquals(100f, usage.rolling?.percent)
        assertEquals(0f, usage.weekly?.percent)
        assertNull(usage.monthly)
    }

    @Test
    fun dropsInvalidResetTimestamp() {
        val body = """{"usage":{"rolling":{"percent":12,"resetsAt":"nonsense"}}}"""

        val usage = parseUsage(body)

        assertEquals(12f, usage.rolling?.percent)
        assertNull(usage.rolling?.resetsAt)
    }

    @Test
    fun rejectsResponseWithoutWindows() {
        assertThrows(UsageApiException::class.java) {
            parseUsage("""{"usage":{}}""")
        }
    }

    @Test
    fun rejectsInvalidJson() {
        assertThrows(UsageApiException::class.java) {
            parseUsage("not json")
        }
    }
}
