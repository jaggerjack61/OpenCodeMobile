package com.opencode.mobile.data

import android.content.Context
import com.opencode.mobile.network.ModelCatalogClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class CachedCatalog(
    val baseUrl: String,
    val fetchedAt: Long,
    val models: List<GoModel>,
)

/**
 * Keeps [ModelCatalog] in sync with the endpoint's live model list and caches the last result on
 * disk, so the picker is right at launch and still works offline.
 */
class ModelCatalogRepository(
    context: Context,
    private val settingsStore: SettingsStore,
    private val client: ModelCatalogClient,
) {
    private val file = File(context.filesDir, "model-catalog.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var syncedAt = 0L
    private var syncedBaseUrl: String? = null

    init {
        val cached = runCatching { json.decodeFromString<CachedCatalog>(file.readText()) }.getOrNull()
        if (cached != null && cached.baseUrl == settingsStore.baseUrl()) {
            ModelCatalog.update(cached.models)
            syncedAt = cached.fetchedAt
            syncedBaseUrl = cached.baseUrl
        }
    }

    /** Fetches the live catalog unless a recent copy for the current endpoint is already loaded. */
    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val settings = settingsStore.load()
            val now = System.currentTimeMillis()
            val sameEndpoint = syncedBaseUrl == settings.baseUrl
            if (!force && sameEndpoint && now - syncedAt in 0 until MAX_AGE_MS) return

            if (!sameEndpoint) ModelCatalog.reset()
            client.fetch(settings)
                .onSuccess { models ->
                    ModelCatalog.update(models)
                    syncedAt = now
                    syncedBaseUrl = settings.baseUrl
                    withContext(Dispatchers.IO) {
                        runCatching {
                            file.writeText(json.encodeToString(CachedCatalog(settings.baseUrl, now, models)))
                        }
                    }
                }
                .onFailure {
                    // Keep what is loaded and back off briefly instead of retrying on every call.
                    syncedAt = now - MAX_AGE_MS + RETRY_AFTER_MS
                    syncedBaseUrl = settings.baseUrl
                }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 30 * 60 * 1000L
        const val RETRY_AFTER_MS = 60 * 1000L
    }
}
