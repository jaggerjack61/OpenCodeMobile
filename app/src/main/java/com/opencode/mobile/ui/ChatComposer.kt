package com.opencode.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.mobile.data.ChatAttachment
import com.opencode.mobile.data.MediaKind
import com.opencode.mobile.data.ThinkingEffort

@Composable
fun Composer(
    draft: String,
    isStreaming: Boolean,
    pendingAttachments: List<ChatAttachment>,
    importingAttachments: Boolean,
    attachLabel: String?,
    modelName: String,
    effort: ThinkingEffort,
    effortChoices: List<ThinkingEffort>,
    webSearchEnabled: Boolean,
    shellEnabled: Boolean,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onOpenModels: () -> Unit,
    onEffortSelect: (ThinkingEffort) -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onShellToggle: (Boolean) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val canSend = draft.isNotBlank() || pendingAttachments.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .navigationBarsPadding()
            .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column {
                if (pendingAttachments.isNotEmpty() || importingAttachments) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pendingAttachments.forEach { attachment ->
                            PendingAttachmentChip(
                                attachment = attachment,
                                onRemove = { onRemoveAttachment(attachment.id) },
                            )
                        }
                        if (importingAttachments) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Message OpenCode…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 22.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(colors.primary),
                        maxLines = 8,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ToolsMenuButton(
                            enabled = !isStreaming,
                            attachLabel = attachLabel,
                            attachEnabled = !importingAttachments,
                            webSearchEnabled = webSearchEnabled,
                            shellEnabled = shellEnabled,
                            onAttach = onAttach,
                            onWebSearchToggle = onWebSearchToggle,
                            onShellToggle = onShellToggle,
                        )
                        ComposerChip(
                            text = modelName,
                            enabled = !isStreaming,
                            onClick = onOpenModels,
                            trailing = Icons.Outlined.ExpandMore,
                            emphasized = true,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (effortChoices.size > 1) {
                            EffortButton(
                                effort = effort,
                                choices = effortChoices,
                                enabled = !isStreaming,
                                onSelect = onEffortSelect,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    SendButton(
                        isStreaming = isStreaming,
                        enabled = canSend,
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerChip(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val content = if (emphasized) colors.onSurface else colors.onSurfaceVariant
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp),
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailing, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
        }
    }
}

@Composable
private fun ToolsMenuButton(
    enabled: Boolean,
    attachLabel: String?,
    attachEnabled: Boolean,
    webSearchEnabled: Boolean,
    shellEnabled: Boolean,
    onAttach: () -> Unit,
    onWebSearchToggle: (Boolean) -> Unit,
    onShellToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Attachments and tools",
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant,
            )
            if (webSearchEnabled) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Public,
                    contentDescription = "Web search on",
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
            if (shellEnabled) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Terminal,
                    contentDescription = "Shell on",
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = colors.surfaceContainerHigh,
        ) {
            if (attachLabel != null) {
                DropdownMenuItem(
                    text = { Text(attachLabel) },
                    leadingIcon = { Icon(Icons.Outlined.AttachFile, contentDescription = null) },
                    enabled = attachEnabled,
                    onClick = {
                        open = false
                        onAttach()
                    },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = colors.outlineVariant,
                )
            }
            ToolMenuItem(
                icon = Icons.Outlined.Public,
                title = "Web search",
                checked = webSearchEnabled,
                onClick = {
                    open = false
                    onWebSearchToggle(!webSearchEnabled)
                },
            )
            ToolMenuItem(
                icon = Icons.Outlined.Terminal,
                title = "Shell",
                checked = shellEnabled,
                onClick = {
                    open = false
                    onShellToggle(!shellEnabled)
                },
            )
        }
    }
}

@Composable
private fun ToolMenuItem(icon: ImageVector, title: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = {
            Box(modifier = Modifier.size(18.dp)) {
                if (checked) Icon(Icons.Outlined.Check, contentDescription = "On", modifier = Modifier.fillMaxSize())
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun EffortButton(
    effort: ThinkingEffort,
    choices: List<ThinkingEffort>,
    enabled: Boolean,
    onSelect: (ThinkingEffort) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box {
        ComposerChip(
            text = effort.label,
            enabled = enabled,
            onClick = { open = true },
            leading = Icons.Outlined.Lightbulb,
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = colors.surfaceContainerHigh,
        ) {
            Text(
                text = "Thinking effort",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(choice.label)
                            if (choice == ThinkingEffort.AUTO) {
                                Text(
                                    text = "Model default",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        Box(modifier = Modifier.size(18.dp)) {
                            if (choice == effort) {
                                Icon(Icons.Outlined.Check, contentDescription = "Selected", modifier = Modifier.fillMaxSize())
                            }
                        }
                    },
                    onClick = {
                        open = false
                        onSelect(choice)
                    },
                )
            }
        }
    }
}

@Composable
private fun SendButton(
    isStreaming: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val active = isStreaming || enabled
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (active) colors.primary else colors.surfaceContainerHighest)
            .clickable(enabled = active) { if (isStreaming) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isStreaming) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
            contentDescription = if (isStreaming) "Stop generating" else "Send",
            modifier = Modifier.size(if (isStreaming) 16.dp else 18.dp),
            tint = if (active) colors.onPrimary else colors.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PendingAttachmentChip(attachment: ChatAttachment, onRemove: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when (attachment.mediaKind) {
        MediaKind.IMAGE -> {
            Box(modifier = Modifier.size(56.dp)) {
                AttachmentImage(
                    attachment = attachment,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                )
                RemoveBadge(
                    onRemove = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp),
                )
            }
        }
        else -> {
            Surface(shape = RoundedCornerShape(12.dp), color = colors.surfaceContainerHighest) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    RemoveBadge(onRemove = onRemove)
                }
            }
        }
    }
}

@Composable
private fun RemoveBadge(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(colors.inverseSurface.copy(alpha = 0.75f))
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Remove attachment",
            modifier = Modifier.size(13.dp),
            tint = colors.inverseOnSurface,
        )
    }
}
