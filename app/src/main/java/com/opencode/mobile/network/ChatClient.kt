package com.opencode.mobile.network

import android.util.Base64
import com.opencode.mobile.data.AppSettings
import com.opencode.mobile.data.ChatAttachment
import com.opencode.mobile.data.ChatMessage
import com.opencode.mobile.data.MediaKind
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.data.ThinkingEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class ToolCall(val delta: ToolCallDelta) : StreamEvent
    data class Failure(val message: String) : StreamEvent
    data object Finished : StreamEvent
}

class ChatApiException(message: String) : Exception(message)

class ChatClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val clientSessionId = UUID.randomUUID().toString()

    fun stream(
        settings: AppSettings,
        modelId: String,
        sessionId: String,
        messages: List<ChatMessage>,
        thinkingEffort: ThinkingEffort = ThinkingEffort.AUTO,
        webSearchEnabled: Boolean = false,
        shellEnabled: Boolean = false,
        systemHint: String? = null,
    ): Flow<StreamEvent> = callbackFlow {
        val payload = withContext(Dispatchers.IO) {
            buildPayload(
                modelId = modelId,
                thinkingEffort = thinkingEffort,
                messages = messages,
                webSearchEnabled = webSearchEnabled,
                shellEnabled = shellEnabled,
                systemHint = systemHint,
            )
        }

        val request = Request.Builder()
            .url(settings.chatEndpoint)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Accept", "text/event-stream")
            .header("User-Agent", USER_AGENT)
            .header("x-opencode-session", sessionId)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        val call = client.newCall(request)
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!call.isCanceled()) {
                        trySend(StreamEvent.Failure(friendlyNetworkError(e)))
                    }
                    close()
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            val body = runCatching { resp.body.string() }.getOrNull().orEmpty()
                            trySend(StreamEvent.Failure(errorFor(resp.code, body)))
                            close()
                            return
                        }
                        val source = resp.body.source()
                        try {
                            while (true) {
                                val line = source.readUtf8Line() ?: break
                                if (!line.startsWith("data:")) continue
                                val data = line.removePrefix("data:").trim()
                                val parsed = SseParser.parse(data) ?: continue
                                parsed.error?.let {
                                    trySend(StreamEvent.Failure(it))
                                    break
                                }
                                parsed.reasoning?.let { trySend(StreamEvent.Reasoning(it)) }
                                parsed.content?.let { trySend(StreamEvent.Content(it)) }
                                parsed.toolCalls.forEach { trySend(StreamEvent.ToolCall(it)) }
                                if (parsed.finished) {
                                    trySend(StreamEvent.Finished)
                                    break
                                }
                            }
                        } catch (e: IOException) {
                            if (!call.isCanceled()) {
                                trySend(StreamEvent.Failure(friendlyNetworkError(e)))
                            }
                        } finally {
                            close()
                        }
                    }
                }
            },
        )

        awaitClose { call.cancel() }
    }.buffer(Channel.UNLIMITED)

    suspend fun testConnection(settings: AppSettings, modelId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = buildJsonObject {
                    put("model", modelId)
                    put("stream", false)
                    putJsonArray("messages") {
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", "ping")
                            },
                        )
                    }
                    put("max_tokens", 16)
                }
                val request = Request.Builder()
                    .url(settings.chatEndpoint)
                    .header("Authorization", "Bearer ${settings.apiKey}")
                    .header("User-Agent", USER_AGENT)
                    .header("x-opencode-session", clientSessionId)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = runCatching { response.body.string() }.getOrNull().orEmpty()
                        throw ChatApiException(errorFor(response.code, body))
                    }
                }
            }
        }
    }

    private suspend fun buildPayload(
        modelId: String,
        thinkingEffort: ThinkingEffort,
        messages: List<ChatMessage>,
        webSearchEnabled: Boolean,
        shellEnabled: Boolean,
        systemHint: String?,
    ): JsonObject {
        val allowedMedia = ModelCatalog.mediaKindsFor(modelId)
        val history = trimmedHistory(messages).mapNotNull { messageToJson(it, allowedMedia) }
        val system = systemHint ?: if (webSearchEnabled || shellEnabled) toolGuidance() else null
        return buildJsonObject {
            put("model", modelId)
            put("stream", true)
            thinkingEffort.apiValue?.let { put("reasoning_effort", it) }
            if (webSearchEnabled || shellEnabled) {
                putJsonArray("tools") {
                    if (webSearchEnabled) add(webSearchTool())
                    if (shellEnabled) add(shellTool())
                }
                put("tool_choice", "auto")
            }
            putJsonArray("messages") {
                system?.let {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", it)
                        },
                    )
                }
                addAll(history)
            }
        }
    }

    private fun toolGuidance(): String {
        return "You are OpenCode Mobile, a helpful assistant. Current date: ${LocalDate.now()}. " +
            "Use web_search when current or verifiable information is needed. Make at most two " +
            "concise searches per reply, avoid near-duplicate queries, and cite source URLs. " +
            "Use shell only when the user asks to run commands or inspect files; commands run in " +
            "an app-private workspace. After tool results arrive, answer the user instead of " +
            "searching again unless something essential is still missing."
    }

    private fun messageToJson(message: ChatMessage, allowedMedia: Set<MediaKind>): JsonObject? {
        if (message.role == ChatMessage.ROLE_TOOL) {
            if (message.toolCallId.isNullOrBlank()) return null
            return buildJsonObject {
                put("role", ChatMessage.ROLE_TOOL)
                put("tool_call_id", message.toolCallId)
                put("content", message.content)
            }
        }

        if (message.role == ChatMessage.ROLE_ASSISTANT && message.toolCalls.isNotEmpty()) {
            return buildJsonObject {
                put("role", ChatMessage.ROLE_ASSISTANT)
                put("content", message.content)
                putJsonArray("tool_calls") {
                    message.toolCalls.forEach { call ->
                        add(
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.arguments)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (message.attachments.isEmpty()) {
            if (message.content.isBlank()) return null
            return buildJsonObject {
                put("role", message.role)
                put("content", message.content)
            }
        }

        val parts = buildJsonArray {
            if (message.content.isNotBlank()) {
                add(
                    buildJsonObject {
                        put("type", "text")
                        put("text", message.content)
                    },
                )
            }
            message.attachments.forEach { attachment ->
                attachmentPart(attachment, allowedMedia)?.let { add(it) }
            }
        }
        if (parts.isEmpty()) return null
        return buildJsonObject {
            put("role", message.role)
            put("content", parts)
        }
    }

    private fun attachmentPart(
        attachment: ChatAttachment,
        allowedMedia: Set<MediaKind>,
    ): JsonElement? {
        val mediaKind = attachment.mediaKind ?: return null
        if (mediaKind !in allowedMedia) return null
        val file = File(attachment.localPath)
        if (!file.exists()) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return when (mediaKind) {
            MediaKind.IMAGE -> buildJsonObject {
                put("type", "image_url")
                put(
                    "image_url",
                    buildJsonObject {
                        put("url", "data:${attachment.mimeType};base64,$encoded")
                    },
                )
            }
            MediaKind.AUDIO -> {
                val format = when {
                    attachment.mimeType.contains("wav") -> "wav"
                    attachment.mimeType.contains("mpeg") || attachment.mimeType.contains("mp3") -> "mp3"
                    else -> return null
                }
                buildJsonObject {
                    put("type", "input_audio")
                    put(
                        "input_audio",
                        buildJsonObject {
                            put("data", encoded)
                            put("format", format)
                        },
                    )
                }
            }
        }
    }

    private fun trimmedHistory(messages: List<ChatMessage>): List<ChatMessage> {
        val valid = messages.filter { message ->
            message.error == null && (
                message.isTool ||
                    message.content.isNotBlank() ||
                    message.attachments.isNotEmpty() ||
                    message.toolCalls.isNotEmpty()
                )
        }
        var total = 0
        val kept = ArrayDeque<ChatMessage>()
        for (message in valid.asReversed()) {
            val weight = message.content.length + message.attachments.sumOf { it.sizeBytes / 4 }.toInt()
            if (kept.isNotEmpty() && total + weight > MAX_HISTORY_CHARS) break
            total += weight
            kept.addFirst(message)
        }
        var result = kept.toList()
        while (result.isNotEmpty() && (result.first().isTool || result.first().toolCalls.isNotEmpty())) {
            result = result.drop(1)
        }

        val toolResultIds = result.filter { it.isTool }.mapNotNull { it.toolCallId }.toSet()
        return result.filterNot { message ->
            message.toolCalls.isNotEmpty() && message.toolCalls.any { it.id !in toolResultIds }
        }
    }

    private fun webSearchTool(): JsonObject = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", "web_search")
                put(
                    "description",
                    "Search the public web for current information. Use for news, documentation, " +
                        "versions, or facts you are unsure about. Returns titles, URLs and snippets.",
                )
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "query",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", "The search query.")
                                    },
                                )
                            },
                        )
                        putJsonArray("required") { add("query") }
                    },
                )
            },
        )
    }

    private fun shellTool(): JsonObject = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", "shell")
                put(
                    "description",
                    "Run a non-interactive shell command on the user's Android device in an " +
                        "app-private workspace. /system/bin/sh is available, but bash, git, python " +
                        "and curl may not be installed. Use for inspecting or creating small files " +
                        "and running scripts. Output is captured and truncated.",
                )
                put(
                    "parameters",
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            buildJsonObject {
                                put(
                                    "command",
                                    buildJsonObject {
                                        put("type", "string")
                                        put("description", "The shell command to run.")
                                    },
                                )
                            },
                        )
                        putJsonArray("required") { add("command") }
                    },
                )
            },
        )
    }

    private fun errorFor(code: Int, body: String): String {
        val apiMessage = runCatching {
            val element = kotlinx.serialization.json.Json.parseToJsonElement(body)
            val obj = element as? JsonObject ?: return@runCatching null
            val error = obj["error"] as? JsonObject
            (error?.get("message") as? JsonPrimitive)?.content
                ?: (obj["message"] as? JsonPrimitive)?.content
        }.getOrNull()

        return when (code) {
            401, 403 -> "Invalid or expired API key. Check it in Settings."
            402 -> "OpenCode Go needs credits. Add credits in the OpenCode Console."
            404 -> "That model is unavailable. Pick a different model."
            429 -> "Rate limited by OpenCode Go. Try again in a moment."
            in 500..599 -> "OpenCode Go is having trouble right now (HTTP $code)."
            else -> apiMessage ?: "Request failed (HTTP $code)."
        }
    }

    private fun friendlyNetworkError(error: IOException): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "No internet connection. Check your network and try again."
            message.contains("timeout", ignoreCase = true) ->
                "The connection timed out. Try again."
            message.contains("SSL", ignoreCase = true) ||
                message.contains("certificate", ignoreCase = true) ->
                "Secure connection failed. Check the API base URL in Settings."
            else -> "Network error: ${message.ifBlank { "connection failed" }}"
        }
    }

    companion object {
        private const val USER_AGENT = "opencode-mobile/1.0.0 (Android)"
        private const val MAX_HISTORY_CHARS = 240_000
    }
}
