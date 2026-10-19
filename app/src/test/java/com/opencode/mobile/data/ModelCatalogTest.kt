package com.opencode.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun `deepseek exposes low high and max`() {
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.HIGH, ThinkingEffort.MAX),
            ModelCatalog.effortsFor("deepseek-v4.1-flash"),
        )
    }

    @Test
    fun `kimi k3 only exposes max`() {
        assertEquals(listOf(ThinkingEffort.MAX), ModelCatalog.effortsFor("kimi-k3"))
    }

    @Test
    fun `minimax has no adjustable effort`() {
        assertTrue(ModelCatalog.effortsFor("minimax-m3").isEmpty())
    }

    @Test
    fun `hy models expose none`() {
        assertEquals(
            listOf(ThinkingEffort.NONE, ThinkingEffort.LOW, ThinkingEffort.HIGH),
            ModelCatalog.effortsFor("hy3"),
        )
    }

    @Test
    fun `unsupported effort falls back to auto`() {
        assertEquals(
            ThinkingEffort.AUTO,
            ModelCatalog.resolveEffort("kimi-k3", ThinkingEffort.HIGH),
        )
        assertEquals(
            ThinkingEffort.MAX,
            ModelCatalog.resolveEffort("kimi-k3", ThinkingEffort.MAX),
        )
    }

    @Test
    fun `custom models allow all explicit efforts`() {
        assertEquals(ThinkingEffort.XHIGH, ModelCatalog.resolveEffort("custom-model", ThinkingEffort.XHIGH))
        assertTrue(ModelCatalog.effortsFor("custom-model").contains(ThinkingEffort.NONE))
    }

    @Test
    fun `media capabilities follow the model`() {
        assertTrue(ModelCatalog.mediaKindsFor("deepseek-v4.1-flash").contains(MediaKind.IMAGE))
        assertTrue(ModelCatalog.mediaKindsFor("glm-5.3").isEmpty())
        assertTrue(ModelCatalog.mediaKindsFor("mimo-v2.6-flash").contains(MediaKind.AUDIO))
        assertTrue(ModelCatalog.mediaKindsFor("custom-model").contains(MediaKind.IMAGE))
    }

    @Test
    fun `effort parsing accepts api values`() {
        assertEquals(ThinkingEffort.XHIGH, ThinkingEffort.from("xhigh"))
        assertEquals(ThinkingEffort.HIGH, ThinkingEffort.from(ThinkingEffort.HIGH.name))
        assertEquals(ThinkingEffort.AUTO, ThinkingEffort.from(null))
    }
}
