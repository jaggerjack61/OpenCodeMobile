package com.opencode.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageEndpointTest {
    @Test
    fun derivesUsageEndpointFromDefaultBaseUrl() {
        val settings = AppSettings(baseUrl = AppSettings.DEFAULT_BASE_URL)

        assertEquals(
            "https://opencode.ai/inference/go/v1/usage",
            settings.usageEndpoint,
        )
    }

    @Test
    fun derivesUsageEndpointFromZenBaseUrl() {
        val settings = AppSettings(baseUrl = "https://opencode.ai/zen/go/v1")

        assertEquals(
            "https://opencode.ai/zen/go/v1/usage",
            settings.usageEndpoint,
        )
    }

    @Test
    fun fallsBackToDefaultUsageEndpointForOtherBaseUrls() {
        val settings = AppSettings(baseUrl = "https://example.com/openai/v1")

        assertEquals(AppSettings.DEFAULT_USAGE_URL, settings.usageEndpoint)
    }
}
