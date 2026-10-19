package com.opencode.mobile.network

import com.opencode.mobile.data.AppSettings
import com.opencode.mobile.data.GoUsage
import com.opencode.mobile.data.UsageWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class UsageApiException(message: String) : Exception(message)

class UsageClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetchUsage(settings: AppSettings): Result<GoUsage> = withContext(Dispatchers.IO) {
        try {
            Result.success(execute(settings))
        } catch (error: CancellationException) {
            throw error
        } catch (error: UsageApiException) {
            Result.failure(error)
        } catch (error: IOException) {
            Result.failure(UsageApiException(friendlyNetworkError(error)))
        } catch (error: Exception) {
            Result.failure(UsageApiException(error.message ?: "Could not load usage."))
        }
    }

    private fun execute(settings: AppSettings): GoUsage {
        if (!settings.hasApiKey) throw UsageApiException("Add your OpenCode API key in Settings.")
        val request = Request.Builder()
            .url(settings.usageEndpoint)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw UsageApiException(errorFor(response.code, body))
            }
            return parseUsage(body)
        }
    }

    private fun errorFor(code: Int, body: String): String {
        val apiMessage = runCatching {
            val element = Json.parseToJsonElement(body)
            val obj = element as? JsonObject ?: return@runCatching null
            val error = obj["error"] as? JsonObject
            (error?.get("message") as? JsonPrimitive)?.content
                ?: (obj["message"] as? JsonPrimitive)?.content
        }.getOrNull()

        return when (code) {
            401 -> "Invalid or expired API key. Check it in Settings."
            403 -> "This API key doesn't have an OpenCode Go subscription."
            404 -> "Usage isn't available from this endpoint."
            429 -> "Rate limited by OpenCode Go. Try again in a moment."
            in 500..599 -> "OpenCode Go is having trouble right now (HTTP $code)."
            else -> apiMessage ?: "Could not load usage (HTTP $code)."
        }
    }

    private fun friendlyNetworkError(error: IOException): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Unable to resolve host", ignoreCase = true) ->
                "No internet connection. Check your network and try again."
            message.contains("timeout", ignoreCase = true) ->
                "The usage request timed out. Try again."
            message.contains("SSL", ignoreCase = true) ||
                message.contains("certificate", ignoreCase = true) ->
                "Secure connection failed. Check the API base URL in Settings."
            else -> "Network error: ${message.ifBlank { "connection failed" }}"
        }
    }

    companion object {
        private const val USER_AGENT = "opencode-mobile/1.0.0 (Android)"
    }
}

@Serializable
private data class UsageResponse(val usage: UsagePayload? = null)

@Serializable
private data class UsagePayload(
    val rolling: UsageWindowPayload? = null,
    val weekly: UsageWindowPayload? = null,
    val monthly: UsageWindowPayload? = null,
)

@Serializable
private data class UsageWindowPayload(
    val status: String? = null,
    val percent: Double? = null,
    @SerialName("resetsAt") val resetsAt: String? = null,
)

private val usageJson = Json { ignoreUnknownKeys = true }

internal fun parseUsage(body: String): GoUsage {
    val usage = try {
        usageJson.decodeFromString<UsageResponse>(body).usage
    } catch (error: Exception) {
        throw UsageApiException("OpenCode Go returned an invalid usage response.")
    } ?: throw UsageApiException("OpenCode Go returned an invalid usage response.")

    val rolling = usage.rolling?.toWindow()
    val weekly = usage.weekly?.toWindow()
    val monthly = usage.monthly?.toWindow()
    if (rolling == null && weekly == null && monthly == null) {
        throw UsageApiException("OpenCode Go didn't report any usage limits.")
    }
    return GoUsage(rolling = rolling, weekly = weekly, monthly = monthly)
}

private fun UsageWindowPayload.toWindow(): UsageWindow? {
    val value = percent?.takeIf { it.isFinite() }?.toFloat()?.coerceIn(0f, 100f) ?: return null
    val reset = resetsAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    return UsageWindow(status = status, percent = value, resetsAt = reset)
}
