package com.opencode.mobile.data

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun from(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

data class AppSettings(
    val apiKey: String = "",
    val modelId: String = ModelCatalog.defaultModelId,
    val baseUrl: String = DEFAULT_BASE_URL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val thinkingEffort: String = ThinkingEffort.AUTO.name,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val chatEndpoint: String get() = baseUrl.trimEnd('/') + "/chat/completions"
    val usageEndpoint: String
        get() {
            val normalized = baseUrl.trim().trimEnd('/')
            val goSegment = normalized.indexOf("/go/")
            if (goSegment >= 0) {
                return normalized.substring(0, goSegment + 3) + "/v1/usage"
            }
            if (normalized.endsWith("/go")) {
                return "$normalized/v1/usage"
            }
            return DEFAULT_USAGE_URL
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://opencode.ai/inference/go/openai/v1"
        const val DEFAULT_USAGE_URL = "https://opencode.ai/inference/go/v1/usage"
    }
}

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("opencode_settings", Context.MODE_PRIVATE)
    private val secureKeyStore = SecureKeyStore()

    fun load(): AppSettings {
        val encryptedKey = prefs.getString(KEY_API_KEY, null)
        val apiKey = encryptedKey?.let { secureKeyStore.decrypt(it) }.orEmpty()
        return AppSettings(
            apiKey = apiKey,
            modelId = prefs.getString(KEY_MODEL, ModelCatalog.defaultModelId).orEmpty()
                .ifBlank { ModelCatalog.defaultModelId },
            baseUrl = prefs.getString(KEY_BASE_URL, AppSettings.DEFAULT_BASE_URL).orEmpty()
                .ifBlank { AppSettings.DEFAULT_BASE_URL },
            themeMode = ThemeMode.from(prefs.getString(KEY_THEME, null)),
            thinkingEffort = ThinkingEffort.from(prefs.getString(KEY_THINKING, null)).name,
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit().apply {
            if (settings.apiKey.isBlank()) {
                remove(KEY_API_KEY)
            } else {
                putString(KEY_API_KEY, secureKeyStore.encrypt(settings.apiKey.trim()))
            }
            putString(KEY_MODEL, settings.modelId.trim())
            putString(KEY_BASE_URL, settings.baseUrl.trim())
            putString(KEY_THEME, settings.themeMode.name)
            putString(KEY_THINKING, settings.thinkingEffort)
        }.apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
        secureKeyStore.clearKey()
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model_id"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_THEME = "theme"
        private const val KEY_THINKING = "thinking_effort"
    }
}
