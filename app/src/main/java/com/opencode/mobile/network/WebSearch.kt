package com.opencode.mobile.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class WebResult(
    val title: String,
    val url: String,
    val snippet: String,
)

object WebSearchParser {
    private val blockStart = Regex("""<div class="snippet [^"]*" data-pos="\d+" data-type="web"""")
    private val titleRegex = Regex("""<div class="title search-snippet-title[^"]*"[^>]*title="([^"]*)"""")
    private val titleTextRegex = Regex("""<div class="title search-snippet-title[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)
    private val linkRegex = Regex("""<a href="(https?://[^"]+)"""")
    private val snippetRegex = Regex("""<div class="generic-snippet[^"]*"[^>]*>(.*?)</div>""", RegexOption.DOT_MATCHES_ALL)

    fun parse(html: String, limit: Int = 6): List<WebResult> {
        val starts = blockStart.findAll(html).map { it.range.first }.toList()
        val results = mutableListOf<WebResult>()
        val seen = mutableSetOf<String>()
        for ((index, start) in starts.withIndex()) {
            val end = starts.getOrNull(index + 1) ?: (start + 8_000).coerceAtMost(html.length)
            val block = html.substring(start, end)
            val link = linkRegex.find(block)?.groupValues?.get(1) ?: continue
            if (!seen.add(link)) continue
            val title = titleRegex.find(block)?.groupValues?.get(1)
                ?: titleTextRegex.find(block)?.groupValues?.get(1)
                ?: continue
            val snippet = snippetRegex.find(block)?.groupValues?.get(1).orEmpty()
            results += WebResult(
                title = clean(title),
                url = link,
                snippet = clean(snippet).take(400),
            )
            if (results.size >= limit) break
        }
        return results
    }

    fun clean(value: String): String {
        return value
            .replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&hellip;", "…")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}

class WebSearchClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        val trimmed = query.trim().take(300)
        if (trimmed.isEmpty()) throw IOException("The search query was empty.")
        val results = runCatching { searchBrave(trimmed) }.getOrElse { emptyList() }
        if (results.isNotEmpty()) return@withContext format(trimmed, results)

        val fallback = runCatching { searchInstantAnswer(trimmed) }.getOrElse { emptyList() }
        if (fallback.isNotEmpty()) return@withContext format(trimmed, fallback)

        "No web results found for \"$trimmed\". Try a different query."
    }

    private fun searchBrave(query: String): List<WebResult> {
        val url = "https://search.brave.com".toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addQueryParameter("source", "web")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Web search failed (HTTP ${response.code}).")
            val body = response.body.string()
            return WebSearchParser.parse(body)
        }
    }

    private fun searchInstantAnswer(query: String): List<WebResult> {
        val url = "https://api.duckduckgo.com".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .addQueryParameter("no_html", "1")
            .addQueryParameter("skip_disambig", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val root = runCatching { json.parseToJsonElement(response.body.string()) as? JsonObject }
                .getOrNull() ?: return emptyList()
            val results = mutableListOf<WebResult>()
            val abstract = (root["Abstract"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val abstractUrl = (root["AbstractURL"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (abstract.isNotBlank() && abstractUrl.isNotBlank()) {
                results += WebResult("DuckDuckGo summary", abstractUrl, abstract)
            }
            (root["RelatedTopics"] as? JsonArray)?.forEach { element ->
                val topic = element as? JsonObject ?: return@forEach
                val text = (topic["Text"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                val url = (topic["FirstURL"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (text.isNotBlank() && url.isNotBlank()) {
                    results += WebResult(text.take(80), url, text)
                }
            }
            return results.take(5)
        }
    }

    private fun format(query: String, results: List<WebResult>): String {
        val builder = StringBuilder("Web results for \"$query\":\n")
        results.take(6).forEachIndexed { index, result ->
            builder.append(index + 1)
                .append(". ")
                .append(result.title)
                .append('\n')
                .append("   ")
                .append(result.url)
                .append('\n')
            if (result.snippet.isNotBlank()) {
                builder.append("   ").append(result.snippet).append('\n')
            }
        }
        return builder.toString().take(6_000)
    }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
