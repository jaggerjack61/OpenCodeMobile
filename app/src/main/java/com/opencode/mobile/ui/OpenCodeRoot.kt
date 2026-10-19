package com.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.opencode.mobile.AppContainer
import com.opencode.mobile.data.AppSettings
import com.opencode.mobile.vm.AppViewModelFactory
import com.opencode.mobile.vm.ChatListViewModel
import com.opencode.mobile.vm.ChatViewModel
import com.opencode.mobile.vm.SettingsViewModel
import com.opencode.mobile.vm.UsageViewModel
import java.util.UUID

sealed interface Screen {
    val key: String

    data object Chats : Screen {
        override val key = "chats"
    }

    data class Chat(val conversationId: String?, override val key: String) : Screen

    data object Settings : Screen {
        override val key = "settings"
    }

    data object Usage : Screen {
        override val key = "usage"
    }
}

@Composable
fun OpenCodeRoot(
    container: AppContainer,
    hasApiKey: Boolean,
    onSettingsSaved: (AppSettings) -> Unit,
) {
    val baseFactory = remember(container) { AppViewModelFactory(container) }
    val listViewModel: ChatListViewModel = viewModel(factory = baseFactory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = baseFactory)
    val stack = remember { mutableStateListOf<Screen>(Screen.Chats) }
    val current = stack.last()
    val listState by listViewModel.state.collectAsStateWithLifecycle()

    fun push(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    LaunchedEffect(current) {
        if (current is Screen.Chats) listViewModel.refresh()
    }

    when (val screen = current) {
        is Screen.Chats -> ChatListScreen(
            state = listState,
            onNewChat = { push(Screen.Chat(conversationId = null, key = UUID.randomUUID().toString())) },
            onOpenChat = { id -> push(Screen.Chat(conversationId = id, key = id)) },
            onDeleteChat = listViewModel::delete,
            onOpenUsage = { push(Screen.Usage) },
            onOpenSettings = { push(Screen.Settings) },
        )

        is Screen.Chat -> {
            val factory = remember(screen.key) {
                AppViewModelFactory(container, conversationId = screen.conversationId)
            }
            val chatViewModel: ChatViewModel = viewModel(
                key = "chat-${screen.key}",
                factory = factory,
            )
            ChatScreen(
                viewModel = chatViewModel,
                hasApiKey = hasApiKey,
                onBack = ::pop,
                onOpenUsage = { push(Screen.Usage) },
                onOpenSettings = { push(Screen.Settings) },
                onDeleted = {
                    pop()
                    listViewModel.refresh()
                },
            )
        }

        is Screen.Usage -> {
            val usageViewModel: UsageViewModel = viewModel(factory = baseFactory)
            UsageScreen(
                viewModel = usageViewModel,
                onBack = ::pop,
                onOpenSettings = { push(Screen.Settings) },
            )
        }

        is Screen.Settings -> SettingsScreen(
            viewModel = settingsViewModel,
            onBack = ::pop,
            onSaved = { settings ->
                onSettingsSaved(settings)
                pop()
            },
        )
    }
}
