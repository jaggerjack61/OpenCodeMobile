package com.opencode.mobile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.AppSettings
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.data.SettingsStore
import com.opencode.mobile.data.ThemeMode
import com.opencode.mobile.data.ThinkingEffort
import com.opencode.mobile.network.ChatClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKey: String = "",
    val modelId: String = ModelCatalog.defaultModelId,
    val baseUrl: String = AppSettings.DEFAULT_BASE_URL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val thinkingEffort: ThinkingEffort = ThinkingEffort.AUTO,
    val testing: Boolean = false,
)

sealed interface SettingsEvent {
    data class Message(val text: String) : SettingsEvent
    data class Saved(val settings: AppSettings) : SettingsEvent
}

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val chatClient: ChatClient,
) : ViewModel() {
    private val loaded = settingsStore.load()
    private val _state = MutableStateFlow(
        SettingsUiState(
            apiKey = loaded.apiKey,
            modelId = loaded.modelId,
            baseUrl = loaded.baseUrl,
            themeMode = loaded.themeMode,
            thinkingEffort = ThinkingEffort.from(loaded.thinkingEffort),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setApiKey(value: String) {
        _state.update { it.copy(apiKey = value) }
    }

    fun setModelId(value: String) {
        _state.update {
            it.copy(
                modelId = value,
                thinkingEffort = ModelCatalog.resolveEffort(value, it.thinkingEffort),
            )
        }
    }

    fun setBaseUrl(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun setThemeMode(value: ThemeMode) {
        _state.update { it.copy(themeMode = value) }
    }

    fun setThinkingEffort(value: ThinkingEffort) {
        _state.update {
            it.copy(thinkingEffort = ModelCatalog.resolveEffort(it.modelId, value))
        }
    }

    fun resetBaseUrl() {
        _state.update { it.copy(baseUrl = AppSettings.DEFAULT_BASE_URL) }
    }

    fun save() {
        val current = _state.value
        val modelId = current.modelId.trim()
        val baseUrl = current.baseUrl.trim().trimEnd('/')
        when {
            modelId.isEmpty() -> {
                _events.tryEmit(SettingsEvent.Message("Pick a model before saving."))
                return
            }
            !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") -> {
                _events.tryEmit(SettingsEvent.Message("The API base URL must start with https:// or http://"))
                return
            }
            baseUrl.contains(' ') -> {
                _events.tryEmit(SettingsEvent.Message("The API base URL can't contain spaces."))
                return
            }
        }

        val settings = AppSettings(
            apiKey = current.apiKey.trim(),
            modelId = modelId,
            baseUrl = baseUrl,
            themeMode = current.themeMode,
            thinkingEffort = ModelCatalog.resolveEffort(modelId, current.thinkingEffort).name,
        )
        settingsStore.save(settings)
        _state.update { it.copy(modelId = modelId, baseUrl = baseUrl) }
        _events.tryEmit(SettingsEvent.Saved(settings))
    }

    fun testConnection() {
        val current = _state.value
        if (current.testing) return
        if (current.apiKey.isBlank()) {
            _events.tryEmit(SettingsEvent.Message("Enter your API key first."))
            return
        }
        val url = current.baseUrl.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _events.tryEmit(SettingsEvent.Message("The API base URL must start with https:// or http://"))
            return
        }
        val settings = AppSettings(
            apiKey = current.apiKey.trim(),
            modelId = current.modelId.trim(),
            baseUrl = url,
            themeMode = current.themeMode,
        )
        viewModelScope.launch {
            _state.update { it.copy(testing = true) }
            chatClient.testConnection(settings, settings.modelId)
                .onSuccess {
                    _events.emit(SettingsEvent.Message("Connected to ${ModelCatalog.nameFor(settings.modelId)}."))
                }
                .onFailure {
                    _events.emit(
                        SettingsEvent.Message(it.message ?: "Could not connect to OpenCode Go."),
                    )
                }
            _state.update { it.copy(testing = false) }
        }
    }
}
