package com.opencode.mobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMessageTest {
    @Test
    fun `free tier gate explains that the model is limited to OpenCode`() {
        val body = """{"type":"error","error":{"type":"FreeUsageLimitError","message":"Free usage limit reached."}}"""
        assertTrue(apiErrorMessage(429, body).contains("inside its own app"))
        val legacy = """{"error":{"type":"FreeTierError","message":"OpenCode's free tier can only be used from within OpenCode"}}"""
        assertTrue(apiErrorMessage(403, legacy).contains("inside its own app"))
    }

    @Test
    fun `server message is preferred when present`() {
        val body = """{"error":{"type":"authentication_error","message":"Model access is disabled"}}"""
        assertEquals("Model access is disabled", apiErrorMessage(403, body))
        assertEquals("Slow down", apiErrorMessage(429, """{"error":"Slow down"}"""))
        assertEquals("Nope", apiErrorMessage(400, """{"message":"Nope"}"""))
    }

    @Test
    fun `unauthorized always points at the API key`() {
        val body = """{"error":{"type":"authentication_error","message":"Go requires a credential"}}"""
        assertEquals("Invalid or expired API key. Check it in Settings.", apiErrorMessage(401, body))
    }

    @Test
    fun `falls back to status based messages`() {
        assertEquals("That model is unavailable. Pick a different model.", apiErrorMessage(404, ""))
        assertEquals("Rate limited by OpenCode Go. Try again in a moment.", apiErrorMessage(429, "<html>"))
        assertEquals("OpenCode Go is having trouble right now (HTTP 503).", apiErrorMessage(503, """{"error":{"message":"x"}}"""))
        assertEquals("Request failed (HTTP 418).", apiErrorMessage(418, "{}"))
    }

    @Test
    fun `long server messages are shortened`() {
        val message = apiErrorMessage(400, """{"error":{"message":"${"a".repeat(400)}"}}""")
        assertEquals(280, message.length)
        assertTrue(message.endsWith("…"))
    }
}
