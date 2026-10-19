package com.opencode.mobile.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class ToolCallDelta(
    val index: Int,
    val id: String? = null,
    val name: String? = null,
    val arguments: String? = null,
)

data class SseParseResult(
    val content: String? = null,
    val reasoning: String? = null,
    val toolCalls: List<ToolCallDelta> = emptyList(),
    val finished: Boolean = false,
    val error: String? = null,
)

object SseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(data: String): SseParseResult? {
        val payload = data.trim()
        if (payload.isEmpty()) return null
        if (payload == "[DONE]") return SseParseResult(finished = true)

        val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
        (root["error"] as? JsonObject)?.let { errorObject ->
            val message = (errorObject["message"] as? JsonPrimitive)?.contentOrNull
            return SseParseResult(error = message ?: "The model returned an error.")
        }

        val choices = root["choices"] as? JsonArray ?: return null
        val choice = choices.firstOrNull() as? JsonObject ?: return null
        val delta = choice["delta"] as? JsonObject
        val content = (delta?.get("content") as? JsonPrimitive)?.contentOrNull
        val reasoning = (delta?.get("reasoning_content") as? JsonPrimitive)?.contentOrNull
            ?: (delta?.get("reasoning") as? JsonPrimitive)?.contentOrNull
        val toolCalls = (delta?.get("tool_calls") as? JsonArray)?.mapNotNull { element ->
            val call = element as? JsonObject ?: return@mapNotNull null
            val function = call["function"] as? JsonObject
            ToolCallDelta(
                index = (call["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                id = (call["id"] as? JsonPrimitive)?.contentOrNull,
                name = (function?.get("name") as? JsonPrimitive)?.contentOrNull,
                arguments = (function?.get("arguments") as? JsonPrimitive)?.contentOrNull,
            )
        }.orEmpty()
        val finished = (choice["finish_reason"] as? JsonPrimitive)?.contentOrNull != null

        if (content.isNullOrEmpty() && reasoning.isNullOrEmpty() && toolCalls.isEmpty() && !finished) {
            return null
        }
        return SseParseResult(
            content = content?.takeIf { it.isNotEmpty() },
            reasoning = reasoning?.takeIf { it.isNotEmpty() },
            toolCalls = toolCalls,
            finished = finished,
        )
    }
}
