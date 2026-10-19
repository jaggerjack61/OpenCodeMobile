package com.opencode.mobile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.opencode.mobile.AppContainer

class AppViewModelFactory(
    private val container: AppContainer,
    private val conversationId: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatListViewModel::class.java) ->
                ChatListViewModel(container.conversationStore, container.settingsStore) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) ->
                ChatViewModel(
                    conversationId = conversationId,
                    conversationStore = container.conversationStore,
                    settingsStore = container.settingsStore,
                    attachmentStore = container.attachmentStore,
                    chatClient = container.chatClient,
                    webSearchClient = container.webSearchClient,
                    shellTool = container.shellTool,
                ) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(
                    settingsStore = container.settingsStore,
                    chatClient = container.chatClient,
                ) as T
            modelClass.isAssignableFrom(UsageViewModel::class.java) ->
                UsageViewModel(
                    settingsStore = container.settingsStore,
                    usageClient = container.usageClient,
                ) as T
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
