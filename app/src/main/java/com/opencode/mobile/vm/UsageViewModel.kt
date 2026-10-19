package com.opencode.mobile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.GoUsage
import com.opencode.mobile.data.SettingsStore
import com.opencode.mobile.network.UsageClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsageUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val usage: GoUsage? = null,
    val updatedAt: Long? = null,
    val hasApiKey: Boolean = true,
) {
    val hasUsage: Boolean get() = usage != null
}

class UsageViewModel(
    private val settingsStore: SettingsStore,
    private val usageClient: UsageClient,
) : ViewModel() {
    private val _state = MutableStateFlow(UsageUiState())
    val state: StateFlow<UsageUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val settings = settingsStore.load()
            if (!settings.hasApiKey) {
                _state.update {
                    it.copy(hasApiKey = false, loading = false, refreshing = false, error = null)
                }
                return@launch
            }
            _state.update {
                it.copy(
                    hasApiKey = true,
                    loading = !it.hasUsage,
                    refreshing = it.hasUsage,
                    error = null,
                )
            }
            usageClient.fetchUsage(settings)
                .onSuccess { usage ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            usage = usage,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = error.message ?: "Could not load usage.",
                        )
                    }
                }
        }
    }
}
