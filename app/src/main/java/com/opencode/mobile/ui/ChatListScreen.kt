package com.opencode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.mobile.data.ConversationSummary
import com.opencode.mobile.data.GoModel
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.vm.ChatListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    state: ChatListUiState,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val models by ModelCatalog.modelsFlow.collectAsStateWithLifecycle()
    val modelsById = remember(models) { models.associateBy { it.id } }
    val sections = remember(state.conversations) {
        val now = System.currentTimeMillis()
        state.conversations.groupBy { dateBucket(it.updatedAt, now) }.toSortedMap().toList()
    }
    val isEmpty = !state.loading && state.conversations.isEmpty()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppMark(size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Wordmark()
                            Spacer(Modifier.width(8.dp))
                            Tag("Go")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenUsage) {
                            Icon(Icons.Outlined.DataUsage, contentDescription = "Usage")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                        }
                    },
                )
                if (listState.canScrollBackward) HorizontalDivider(color = colors.outlineVariant)
            }
        },
        floatingActionButton = {
            if (!isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onNewChat,
                    shape = OcShapes.card,
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 2.dp,
                        focusedElevation = 2.dp,
                        hoveredElevation = 3.dp,
                    ),
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("New chat", style = MaterialTheme.typography.labelLarge) },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading && state.conversations.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                state.conversations.isEmpty() -> {
                    EmptyChats(
                        hasApiKey = state.hasApiKey,
                        onNewChat = onNewChat,
                        onOpenSettings = onOpenSettings,
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 104.dp),
                    ) {
                        if (!state.hasApiKey) {
                            item(key = "api-key") {
                                ApiKeyCard(
                                    onOpenSettings = onOpenSettings,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        sections.forEach { (bucket, conversations) ->
                            item(key = "section-${bucket.name}", contentType = "section") {
                                SectionLabel(
                                    text = bucket.label,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
                                )
                            }
                            itemsIndexed(
                                items = conversations,
                                key = { _, conversation -> conversation.id },
                                contentType = { _, _ -> "conversation" },
                            ) { index, conversation ->
                                Column {
                                    ConversationRow(
                                        conversation = conversation,
                                        model = modelsById[conversation.modelId],
                                        onClick = { onOpenChat(conversation.id) },
                                        onDelete = { pendingDelete = conversation },
                                    )
                                    if (index < conversations.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = colors.outlineVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chat?") },
            text = {
                Text("\"${conversation.title}\" and its messages will be permanently deleted from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteChat(conversation.id)
                        pendingDelete = null
                    },
                ) { Text("Delete", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationSummary,
    model: GoModel?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = conversation.preview,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model?.name ?: conversation.modelId,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model?.free == true) {
                    Spacer(Modifier.width(6.dp))
                    Tag("Free")
                }
                Text(
                    text = "  ·  ${relativeTime(conversation.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Chat options",
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = OcShapes.card,
                containerColor = colors.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text("Delete chat", color = colors.error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = colors.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyChats(
    hasApiKey: Boolean,
    onNewChat: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        AppMark(size = 56.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "What are we building?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasApiKey) {
                "Chat with GLM, Kimi, DeepSeek, Qwen and more through OpenCode Go, free models included."
            } else {
                "Add your OpenCode Go API key to chat with GLM, Kimi, DeepSeek, Qwen and more, " +
                    "free models included."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        if (hasApiKey) {
            PrimaryButton(text = "New chat", onClick = onNewChat, icon = Icons.Outlined.Add)
        } else {
            PrimaryButton(text = "Add API key", onClick = onOpenSettings)
        }
    }
}
