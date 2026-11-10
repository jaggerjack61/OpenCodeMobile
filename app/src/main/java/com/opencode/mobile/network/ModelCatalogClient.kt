package com.opencode.mobile.network

import com.opencode.mobile.data.AppSettings
import com.opencode.mobile.data.GoModel
import com.opencode.mobile.data.MediaKind
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.data.ThinkingEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Fetches the model list published by the configured endpoint. */
class ModelCatalogClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(settings: AppSettings): Result<List<GoModel>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(settings.modelsEndpoint)
                .header("Accept", "application/json")
                .header("User-Agent", ChatClient.USER_AGENT)
                .apply {
                    if (settings.hasApiKey) header("Authorization", "Bearer ${settings.apiKey}")
                }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ChatApiException("Could not load the model list (HTTP ${response.code}).")
                }
                ModelListParser.parse(response.body.string())
                    ?: throw ChatApiException("The endpoint did not return an OpenCode model list.")
            }
        }
    }
}

internal object ModelListParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Go lists every model under its native API and translates the others, so models whose native
     * API is not chat completions are only kept when they are known to work through it.
     */
    private val translated = ModelCatalog.bundled.map { it.id }.toSet()

    /**
     * Returns the chat models in [body], ordered like the bundled catalog with new models last.
     * Returns null when the response is not an OpenCode catalog, so other OpenAI-compatible
     * endpoints keep the bundled list.
     */
    fun parse(body: String): List<GoModel>? {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        val data = root["data"] as? JsonArray ?: return null
        val entries = data.mapNotNull { it as? JsonObject }
        if (entries.none { it["api"] is JsonObject }) return null

        val models = entries.mapNotNull(::toModel)
        if (models.isEmpty()) return null
        val order = ModelCatalog.bundled.withIndex().associate { (index, model) -> model.id to index }
        return models
            .distinctBy { it.id }
            .withIndex()
            .sortedWith(compareBy({ order[it.value.id] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    private fun toModel(entry: JsonObject): GoModel? {
        val id = entry.string("id")?.takeIf { it.isNotBlank() } ?: return null
        entry.string("type")?.let { if (it != "chat") return null }
        val api = entry["api"] as? JsonObject ?: return null
        if ("chat-completions" !in api && id !in translated) return null

        val free = isFree(entry["cost"] as? JsonObject)
        val efforts = (entry.path("capabilities", "reasoning", "effort") as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .map(ThinkingEffort::from)
            .filter { it != ThinkingEffort.AUTO }
            .distinct()
        val inputs = (entry.path("modalities", "input") as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        // Entries are usually MIME types, but some models list a bare kind such as "image".
        val media = buildSet {
            if (inputs.any { it == "image" || it.startsWith("image/") }) add(MediaKind.IMAGE)
            if (inputs.any { it == "audio" || it == "audio/mpeg" || it == "audio/wav" }) add(MediaKind.AUDIO)
        }
        return GoModel(
            id = id,
            name = displayName(entry.string("name") ?: id, free),
            free = free,
            efforts = efforts,
            media = media,
        )
    }

    private fun isFree(cost: JsonObject?): Boolean {
        val input = (cost?.path("text", "input", "price") as? JsonPrimitive)?.doubleOrNull
        val output = (cost?.path("text", "output", "price") as? JsonPrimitive)?.doubleOrNull
        return input == 0.0 && output == 0.0
    }

    /** Free models are tagged in the UI, so "Beam Free" is shown as "Beam". */
    private fun displayName(name: String, free: Boolean): String {
        val trimmed = name.trim()
        if (!free) return trimmed
        return trimmed.removeSuffix(" Free").removeSuffix(" free").trim().ifBlank { trimmed }
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.path(vararg keys: String): JsonElement? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }
}
