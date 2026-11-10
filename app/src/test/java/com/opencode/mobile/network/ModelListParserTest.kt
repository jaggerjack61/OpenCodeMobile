package com.opencode.mobile.network

import com.opencode.mobile.data.MediaKind
import com.opencode.mobile.data.ThinkingEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelListParserTest {
    private fun entry(
        id: String,
        name: String,
        api: String = "chat-completions",
        price: String = "1.4",
        efforts: String? = null,
        inputs: String = "\"text\"",
        type: String = "chat",
    ): String {
        val reasoning = if (efforts == null) "{\"supported\":true}" else "{\"supported\":true,\"effort\":[$efforts]}"
        return """
            {"id":"$id","name":"$name","type":"$type",
             "modalities":{"input":[$inputs],"output":["text"]},
             "capabilities":{"reasoning":$reasoning},
             "cost":{"text":{"input":{"price":$price},"output":{"price":$price}}},
             "api":{"$api":{"base_url":"https://opencode.ai/inference/go/openai/v1"}}}
        """.trimIndent()
    }

    private fun catalog(vararg entries: String) = """{"object":"list","data":[${entries.joinToString(",")}]}"""

    @Test
    fun `free models are flagged and lose the Free suffix`() {
        val models = ModelListParser.parse(
            catalog(
                entry("beam-free", "Beam Free", price = "0", efforts = "\"low\",\"medium\",\"high\",\"xhigh\",\"max\""),
                entry("glm-5.3", "GLM-5.3", efforts = "\"low\",\"high\",\"max\""),
            ),
        )
        assertNotNull(models)
        val beam = models!!.first { it.id == "beam-free" }
        assertTrue(beam.free)
        assertEquals("Beam", beam.name)
        assertEquals(
            listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH, ThinkingEffort.XHIGH, ThinkingEffort.MAX),
            beam.efforts,
        )
        val glm = models.first { it.id == "glm-5.3" }
        assertFalse(glm.free)
        assertEquals("GLM-5.3", glm.name)
    }

    @Test
    fun `new models are added after the bundled order`() {
        val models = ModelListParser.parse(
            catalog(
                entry("brand-new-free", "Brand New Free", price = "0"),
                entry("hy3", "Hy3"),
                entry("glm-5.3", "GLM-5.3"),
            ),
        )
        assertEquals(listOf("glm-5.3", "hy3", "brand-new-free"), models!!.map { it.id })
    }

    @Test
    fun `models on other protocols are skipped unless known to be translated`() {
        val models = ModelListParser.parse(
            catalog(
                entry("claude-haiku-5-5", "Claude Haiku 5.5", api = "messages"),
                entry("gemini-4-argon", "Gemini 4 Argon", api = "generate-content"),
                entry("qwen3.8-flash", "Qwen3.8 Flash", api = "messages"),
                entry("glm-5.3", "GLM-5.3"),
            ),
        )
        assertEquals(listOf("glm-5.3", "qwen3.8-flash"), models!!.map { it.id })
    }

    @Test
    fun `media kinds come from input modalities`() {
        val models = ModelListParser.parse(
            catalog(
                entry("mimo-v2.6-flash", "MiMo-V2.6-Flash", inputs = "\"text\",\"image/png\",\"audio/mpeg\",\"video/mp4\""),
                entry("kimi-k3", "Kimi K3", inputs = "\"text\",\"image/jpeg\"", efforts = "\"max\""),
                entry("glm-5.3", "GLM-5.3"),
            ),
        )!!
        assertEquals(setOf(MediaKind.IMAGE, MediaKind.AUDIO), models.first { it.id == "mimo-v2.6-flash" }.media)
        assertEquals(setOf(MediaKind.IMAGE), models.first { it.id == "kimi-k3" }.media)
        assertTrue(models.first { it.id == "glm-5.3" }.media.isEmpty())
        assertEquals(listOf(ThinkingEffort.MAX), models.first { it.id == "kimi-k3" }.efforts)
    }

    @Test
    fun `bare media kinds are understood`() {
        val models = ModelListParser.parse(
            catalog(entry("longcat-2.5-free", "LongCat 2.5 Free", price = "0", inputs = "\"text\",\"image\"")),
        )!!
        assertEquals(setOf(MediaKind.IMAGE), models.single().media)
    }

    @Test
    fun `plain OpenAI model lists are ignored`() {
        assertNull(ModelListParser.parse("""{"object":"list","data":[{"id":"gpt-4o","object":"model"}]}"""))
        assertNull(ModelListParser.parse("not json"))
        assertNull(ModelListParser.parse("""{"data":[]}"""))
    }

    @Test
    fun `non chat models are skipped`() {
        val models = ModelListParser.parse(
            catalog(entry("embed-1", "Embed", type = "embedding"), entry("glm-5.3", "GLM-5.3")),
        )
        assertEquals(listOf("glm-5.3"), models!!.map { it.id })
    }

    @Test
    fun `live catalog snapshot parses`() {
        val body = javaClass.getResource("/go-models.json")!!.readText()
        val models = ModelListParser.parse(body)!!
        assertTrue(models.size >= 15)
        assertEquals(
            listOf("beam-free", "longcat-2.5-free", "iceman-contributor-free"),
            models.filter { it.free }.map { it.id },
        )
        assertEquals("glm-5.3", models.first().id)
        assertTrue(models.none { it.id == "claude-haiku-5-5" })
    }
}
