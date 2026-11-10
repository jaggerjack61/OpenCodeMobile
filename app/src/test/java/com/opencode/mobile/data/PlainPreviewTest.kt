package com.opencode.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlainPreviewTest {
    @Test
    fun `strips emphasis and inline code`() {
        assertEquals(
            "Suspend function → a single value. Use Flow for streams.",
            plainPreview("**Suspend function** → a single value. Use `Flow` for *streams*."),
        )
    }

    @Test
    fun `strips headings lists and links`() {
        assertEquals(
            "Summary Room is official SQLDelight docs",
            plainPreview("## Summary\n- Room is [official](https://developer.android.com)\n- SQLDelight docs"),
        )
    }

    @Test
    fun `keeps snake case identifiers`() {
        assertEquals("call read_file_now", plainPreview("call read_file_now"))
    }

    @Test
    fun `truncates to ninety characters`() {
        assertEquals(90, plainPreview("a".repeat(200)).length)
    }

    @Test
    fun `table rules are dropped`() {
        val table = "Both work:\n\n| | Room | SQLDelight |\n|---|:---:|---|\n| Queries | DAO | SQL |"
        assertEquals("Both work: Room SQLDelight Queries DAO SQL", plainPreview(table))
        assertEquals("Above Below", plainPreview("Above\n\n---\n\nBelow"))
    }
}
