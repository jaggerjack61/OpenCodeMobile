package com.opencode.mobile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.ConversationStore
import com.opencode.mobile.data.ConversationSummary
import com.opencode.mobile.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatListUiState(
    val conversations: List<ConversationSummary> = emptyList(),
    val hasApiKey: Boolean = false,
    val loading: Boolean = true,
)

class ChatListViewModel(
    private val conversationStore: ConversationStore,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    private val hasApiKey = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)

    val state: StateFlow<ChatListUiState> = combine(
        conversationStore.summaries,
        hasApiKey,
        loading,
    ) { conversations, key, isLoading ->
        ChatListUiState(
            conversations = conversations,
            hasApiKey = key,
            loading = isLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatListUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            hasApiKey.value = settingsStore.load().hasApiKey
            conversationStore.refresh()
            loading.value = false
        }
    }

    fun delete(conversationId: String) {
        viewModelScope.launch { conversationStore.delete(conversationId) }
    }
}
