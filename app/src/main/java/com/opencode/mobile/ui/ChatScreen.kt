package com.opencode.mobile.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.rememberMarkdownState
import com.opencode.mobile.data.ChatAttachment
import com.opencode.mobile.data.ChatMessage
import com.opencode.mobile.data.MediaKind
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.data.ThinkingEffort
import com.opencode.mobile.ui.theme.OpenCodeMono
import com.opencode.mobile.vm.ChatEvent
import com.opencode.mobile.vm.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

private const val BOTTOM_ANCHOR = "bottom-anchor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    hasApiKey: Boolean,
    onBack: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val conversation = state.conversation
    val messages = conversation?.messages.orEmpty()
    val lastMessage = messages.lastOrNull()
    val visibleMessages = messages.filter { message ->
        !isHiddenToolCall(message) || (state.isStreaming && message == lastMessage)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var followBottom by remember { mutableStateOf(true) }
    var showModels by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmShell by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val modelId = conversation?.modelId ?: ModelCatalog.defaultModelId
    val mediaKinds = ModelCatalog.mediaKindsFor(modelId)
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris.map { it.toString() })
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.Message -> snackbarHostState.showSnackbar(event.text)
                ChatEvent.Deleted -> onDeleted()
            }
        }
    }

    // Stop following new output once the user scrolls up; resume when they return to the end.
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(listState.isScrollInProgress, listState.lastScrolledBackward, listState.canScrollForward)
        }.collect { (scrolling, backward, canScrollForward) ->
            if (scrolling && backward) followBottom = false
            if (!canScrollForward) followBottom = true
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty() && followBottom) {
            listState.scrollToItem(visibleMessages.size)
        }
    }

    // Markdown lays out asynchronously and streamed output keeps growing, so keep pinning the end
    // while streaming and briefly after the chat opens or a response finishes.
    LaunchedEffect(state.isLoading, state.isStreaming) {
        if (state.isLoading) return@LaunchedEffect
        val pin: suspend () -> Unit = {
            snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
                if (canScrollForward && followBottom && !listState.isScrollInProgress) {
                    listState.scrollToItem(listState.layoutInfo.totalItemsCount - 1)
                }
            }
        }
        if (state.isStreaming) pin() else withTimeoutOrNull(1_500) { pin() }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Text(
                            text = conversation?.title ?: "Chat",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Chat options")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                                shape = RoundedCornerShape(14.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Usage") },
                                    leadingIcon = { Icon(Icons.Outlined.DataUsage, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenUsage()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        onOpenSettings()
                                    },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete chat", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    },
                )
                if (listState.canScrollBackward) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        bottomBar = {
            val effort = ThinkingEffort.from(conversation?.thinkingEffort)
            val efforts = ModelCatalog.effortsFor(modelId)
            Composer(
                draft = state.draft,
                isStreaming = state.isStreaming,
                pendingAttachments = state.pendingAttachments,
                importingAttachments = state.importingAttachments,
                attachLabel = when {
                    mediaKinds.isEmpty() -> null
                    mediaKinds.size == 1 -> "Add ${mediaKinds.first().label.lowercase()}"
                    else -> "Add images or audio"
                },
                modelName = ModelCatalog.nameFor(modelId),
                effort = effort,
                effortChoices = if (efforts.isEmpty()) emptyList() else listOf(ThinkingEffort.AUTO) + efforts,
                webSearchEnabled = conversation?.webSearchEnabled ?: true,
                shellEnabled = conversation?.shellEnabled ?: false,
                onAttach = { pickMedia.launch(mediaKinds.flatMap { it.mimeTypes }.toTypedArray()) },
                onRemoveAttachment = viewModel::removePendingAttachment,
                onDraftChange = viewModel::setDraft,
                onOpenModels = { showModels = true },
                onEffortSelect = viewModel::selectThinkingEffort,
                onWebSearchToggle = viewModel::setWebSearchEnabled,
                onShellToggle = { enabled ->
                    if (enabled) confirmShell = true else viewModel.setShellEnabled(false)
                },
                onSend = {
                    keyboardController?.hide()
                    followBottom = true
                    viewModel.send()
                },
                onStop = viewModel::stop,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!hasApiKey) {
                ApiKeyCard(
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                messages.isEmpty() -> {
                    EmptyConversation(
                        modelName = ModelCatalog.nameFor(modelId),
                        onSuggestion = viewModel::setDraft,
                    )
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        ) {
                            itemsIndexed(visibleMessages, key = { _, message -> message.id }) { index, message ->
                                val previous = visibleMessages.getOrNull(index - 1)
                                val next = visibleMessages.getOrNull(index + 1)
                                val isLast = message == lastMessage
                                MessageItem(
                                    message = message,
                                    isStreaming = state.isStreaming && isLast,
                                    isLast = isLast,
                                    // Footer belongs to the end of an assistant turn, not to each step.
                                    showFooter = next == null || next.isUser,
                                    onRetry = viewModel::retry,
                                    modifier = Modifier.padding(top = itemGap(previous, message)),
                                )
                            }
                            item(key = BOTTOM_ANCHOR) { Spacer(Modifier.height(1.dp)) }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !followBottom && listState.canScrollForward,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 0.8f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shadowElevation = 2.dp,
                                onClick = {
                                    followBottom = true
                                    scope.launch { listState.animateScrollToItem(visibleMessages.size) }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Scroll to latest",
                                    modifier = Modifier
                                        .padding(7.dp)
                                        .size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showModels) {
        ModelSheet(
            currentModelId = modelId,
            onSelect = { selected ->
                viewModel.selectModel(selected)
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete chat?") },
            text = { Text("This conversation and all of its messages will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteConversation()
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmShell) {
        AlertDialog(
            onDismissRequest = { confirmShell = false },
            title = { Text("Enable shell access?") },
            text = {
                Text(
                    "The model will be able to run shell commands as this app inside a private " +
                        "workspace. Commands can create or delete files in that workspace and may " +
                        "access the network. Only enable this for models you trust.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setShellEnabled(true)
                        confirmShell = false
                    },
                ) { Text("Enable") }
            },
            dismissButton = {
                TextButton(onClick = { confirmShell = false }) { Text("Cancel") }
            },
        )
    }
}

/** An assistant message that only requested tools; its results are shown by the tool messages. */
private fun isHiddenToolCall(message: ChatMessage): Boolean =
    !message.isUser &&
        !message.isTool &&
        message.toolCalls.isNotEmpty() &&
        message.content.isBlank() &&
        message.reasoning.isBlank() &&
        message.error == null

private fun itemGap(previous: ChatMessage?, current: ChatMessage): Dp = when {
    previous == null -> 0.dp
    previous.isUser || current.isUser -> 24.dp
    else -> 6.dp
}

@Composable
fun AttachmentImage(attachment: ChatAttachment, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(File(attachment.localPath).absolutePath, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = attachment.fileName,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun EmptyConversation(modelName: String, onSuggestion: (String) -> Unit) {
    val suggestions = listOf(
        "Explain a tricky piece of code",
        "Write a Kotlin function for me",
        "Compare two technical approaches",
        "Help me debug an error",
    )
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AppMark(size = 48.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "What are we building?",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Chatting with $modelName. Conversations stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column {
                suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestion(suggestion) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Outlined.NorthWest,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: ChatMessage,
    isStreaming: Boolean,
    isLast: Boolean,
    showFooter: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            message.isTool -> ToolStep(message)
            message.isUser -> UserMessage(message)
            isHiddenToolCall(message) -> ToolRunningStep(message)
            else -> AssistantMessage(
                message = message,
                isStreaming = isStreaming,
                showFooter = showFooter,
                canRetry = isLast,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun UserMessage(message: ChatMessage) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (message.attachments.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                message.attachments.forEach { attachment ->
                    when (attachment.mediaKind) {
                        MediaKind.IMAGE -> AttachmentImage(
                            attachment = attachment,
                            modifier = Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                        else -> AudioAttachmentChip(attachment)
                    }
                }
            }
            if (message.content.isNotBlank()) Spacer(Modifier.height(6.dp))
        }
        if (message.content.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp),
                color = colors.surfaceContainerHigh,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = colors.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun AudioAttachmentChip(attachment: ChatAttachment) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = attachment.fileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
        }
    }
}

/** Collapsible row used for reasoning and tool steps inside an assistant turn. */
@Composable
private fun StepRow(
    icon: ImageVector,
    title: String,
    detail: String?,
    expanded: Boolean,
    active: Boolean,
    onToggle: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val pulse = if (active) {
        val transition = rememberInfiniteTransition(label = "step")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "stepAlpha",
        ).value
    } else {
        1f
    }
    val chevronRotation = if (expanded) 180f else 0f
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(15.dp)
                .alpha(pulse),
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.alpha(pulse),
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (onToggle != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevronRotation),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/** Indented, left-ruled body shown under an expanded step. */
@Composable
private fun StepBody(text: String) {
    val colors = MaterialTheme.colorScheme
    val rule = colors.outlineVariant
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        color = colors.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 9.dp, top = 2.dp, bottom = 6.dp)
            .drawBehind {
                drawLine(rule, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx())
            }
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp),
    )
}

@Composable
private fun ToolRunningStep(message: ChatMessage) {
    val call = message.toolCalls.firstOrNull()
    val (icon, label) = when (call?.name) {
        "web_search" -> Icons.Outlined.Public to "Searching the web"
        "shell" -> Icons.Outlined.Terminal to "Running a command"
        else -> Icons.Outlined.Terminal to "Using a tool"
    }
    StepRow(icon = icon, title = label, detail = null, expanded = false, active = true, onToggle = null)
}

@Composable
private fun ToolStep(message: ChatMessage) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val isSearch = message.toolName == "web_search"
    Column(modifier = Modifier.fillMaxWidth()) {
        StepRow(
            icon = if (isSearch) Icons.Outlined.Public else Icons.Outlined.Terminal,
            title = if (isSearch) "Searched" else "Ran",
            detail = message.toolDetail,
            expanded = expanded,
            active = false,
            onToggle = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            StepBody(message.content.ifBlank { "No output." })
        }
    }
}

@Composable
private fun ReasoningStep(messageId: String, reasoning: String, isStreaming: Boolean) {
    var expanded by rememberSaveable(messageId) { mutableStateOf(isStreaming) }
    LaunchedEffect(isStreaming) {
        if (!isStreaming) expanded = false
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        StepRow(
            icon = Icons.Outlined.Lightbulb,
            title = if (isStreaming) "Thinking" else "Thought",
            detail = null,
            expanded = expanded,
            active = isStreaming,
            onToggle = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            StepBody(reasoning.trim())
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    isStreaming: Boolean,
    showFooter: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (message.reasoning.isNotBlank()) {
            ReasoningStep(
                messageId = message.id,
                reasoning = message.reasoning,
                isStreaming = isStreaming && message.content.isBlank(),
            )
            if (message.content.isNotBlank()) Spacer(Modifier.height(6.dp))
        }

        if (message.content.isNotBlank()) {
            ChatMarkdown(message.content)
        } else if (isStreaming && message.error == null && message.reasoning.isBlank()) {
            ThinkingDots()
        }

        if (message.error != null) {
            Spacer(Modifier.height(8.dp))
            ErrorCard(error = message.error, canRetry = canRetry, onRetry = onRetry)
        }

        if (!isStreaming && showFooter && message.content.isNotBlank()) {
            MessageFooter(text = message.content, modelId = message.model)
        }
    }
}

@Composable
private fun ErrorCard(error: String, canRetry: Boolean, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.errorContainer,
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = colors.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Request failed",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onErrorContainer,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onErrorContainer,
                modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
            )
            if (canRetry) {
                TextButton(onClick = onRetry) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Try again", color = colors.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun MessageFooter(text: String, modelId: String?) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1_500)
            copied = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable {
                    clipboard.setText(AnnotatedString(text))
                    copied = true
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                contentDescription = if (copied) "Copied" else "Copy response",
                modifier = Modifier.size(15.dp),
                tint = colors.onSurfaceVariant,
            )
        }
        if (modelId != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = ModelCatalog.nameFor(modelId),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ChatMarkdown(content: String) {
    val type = MaterialTheme.typography
    val colors = MaterialTheme.colorScheme
    val body = type.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp)
    val heading = body.copy(fontWeight = FontWeight.SemiBold)
    val markdownState = rememberMarkdownState(content, retainState = true)
    Markdown(
        markdownState = markdownState,
        colors = markdownColor(
            text = colors.onSurface,
            codeBackground = colors.surfaceContainerHigh,
            inlineCodeBackground = colors.surfaceContainerHighest,
            dividerColor = colors.outlineVariant,
            tableBackground = colors.surfaceContainerLow,
        ),
        typography = markdownTypography(
            h1 = heading.copy(fontSize = 20.sp, lineHeight = 28.sp),
            h2 = heading.copy(fontSize = 18.sp, lineHeight = 26.sp),
            h3 = heading.copy(fontSize = 16.sp, lineHeight = 24.sp),
            h4 = heading.copy(fontSize = 15.sp),
            h5 = heading,
            h6 = heading.copy(color = colors.onSurfaceVariant),
            text = body,
            code = body.copy(fontFamily = OpenCodeMono, fontSize = 12.sp, lineHeight = 18.sp),
            inlineCode = body.copy(fontFamily = OpenCodeMono, fontSize = 13.sp),
            quote = body.copy(color = colors.onSurfaceVariant, fontStyle = FontStyle.Italic),
            paragraph = body,
            ordered = body,
            bullet = body,
            list = body,
            textLink = TextLinkStyles(
                style = SpanStyle(
                    color = colors.onSurface,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                ),
            ),
            table = body.copy(fontSize = 13.sp, lineHeight = 19.sp),
        ),
        components = wrappingTableComponents,
        modifier = Modifier.fillMaxWidth(),
    )
}

// The default table renders every cell on one ellipsized line; wrap cell text instead.
private val wrappingTableComponents = markdownComponents(
    table = { model ->
        MarkdownTable(
            content = model.content,
            node = model.node,
            style = model.typography.table,
            headerBlock = { content, header, tableWidth, style ->
                MarkdownTableHeader(
                    content = content,
                    header = header,
                    tableWidth = tableWidth,
                    style = style,
                    verticalAlignment = Alignment.Top,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            },
            rowBlock = { content, row, tableWidth, style ->
                MarkdownTableRow(
                    content = content,
                    header = row,
                    tableWidth = tableWidth,
                    style = style,
                    verticalAlignment = Alignment.Top,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            },
        )
    },
)

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 140),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        }
    }
}
