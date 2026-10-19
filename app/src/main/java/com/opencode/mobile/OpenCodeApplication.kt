package com.opencode.mobile

import android.app.Application
import android.content.Context
import com.opencode.mobile.data.AttachmentStore
import com.opencode.mobile.data.ConversationStore
import com.opencode.mobile.data.SettingsStore
import com.opencode.mobile.network.ChatClient
import com.opencode.mobile.network.ShellTool
import com.opencode.mobile.network.UsageClient
import com.opencode.mobile.network.WebSearchClient

class AppContainer(context: Context) {
    val settingsStore = SettingsStore(context)
    val conversationStore = ConversationStore(context)
    val attachmentStore = AttachmentStore(context)
    val chatClient = ChatClient()
    val usageClient = UsageClient()
    val webSearchClient = WebSearchClient()
    val shellTool = ShellTool(context)
}

class OpenCodeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
