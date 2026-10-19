package com.opencode.mobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun `parses reasoning delta`() {
        val result = SseParser.parse(
            """{"id":"x","choices":[{"index":0,"delta":{"reasoning_content":"Let me think"},"finish_reason":null}]}""",
        )
        assertEquals("Let me think", result?.reasoning)
        assertNull(result?.content)
        assertFalse(result!!.finished)
    }

    @Test
    fun `parses content delta`() {
        val result = SseParser.parse(
            """{"id":"x","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}""",
        )
        assertEquals("Hello", result?.content)
        assertNull(result?.reasoning)
    }

    @Test
    fun `parses finish reason`() {
        val result = SseParser.parse(
            """{"id":"x","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
        )
        assertTrue(result!!.finished)
    }

    @Test
    fun `handles done marker`() {
        val result = SseParser.parse("[DONE]")
        assertTrue(result!!.finished)
    }

    @Test
    fun `parses api error payload`() {
        val result = SseParser.parse("""{"error":{"message":"Upstream unavailable"}}""")
        assertEquals("Upstream unavailable", result?.error)
    }

    @Test
    fun `ignores malformed payloads`() {
        assertNull(SseParser.parse("not json"))
        assertNull(SseParser.parse(""))
        assertNull(SseParser.parse("""{"choices":[]}"""))
    }

    @Test
    fun `ignores role-only first chunk`() {
        assertNull(
            SseParser.parse(
                """{"id":"x","choices":[{"index":0,"delta":{"role":"assistant"},"finish_reason":null}]}""",
            ),
        )
    }

    @Test
    fun `parses tool call deltas`() {
        val result = SseParser.parse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"kotlin\"}"}}]},"finish_reason":null}]}""",
        )
        assertEquals(1, result?.toolCalls?.size)
        assertEquals("call_1", result?.toolCalls?.first()?.id)
        assertEquals("web_search", result?.toolCalls?.first()?.name)
        assertEquals("""{"query":"kotlin"}""", result?.toolCalls?.first()?.arguments)
    }

    @Test
    fun `parses streamed tool argument fragments`() {
        val result = SseParser.parse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"que"}}]},"finish_reason":null}]}""",
        )
        assertEquals("""{"que""", result?.toolCalls?.first()?.arguments)
    }

    @Test
    fun `parses tool call finish reason`() {
        val result = SseParser.parse(
            """{"choices":[{"delta":{"role":"assistant"},"finish_reason":"tool_calls"}]}""",
        )
        assertTrue(result!!.finished)
    }

    @Test
    fun `supports alternate reasoning field`() {
        val result = SseParser.parse(
            """{"choices":[{"delta":{"reasoning":"step one"},"finish_reason":null}]}""",
        )
        assertEquals("step one", result?.reasoning)
    }
}
