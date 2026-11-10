package com.opencode.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.mobile.data.GoModel
import com.opencode.mobile.data.MediaKind
import com.opencode.mobile.data.ModelCatalog

private enum class ModelFilter(val label: String, val icon: ImageVector? = null) {
    ALL("All"),
    FREE("Free"),
    IMAGES("Images", Icons.Outlined.Image),
    THINKING("Thinking", Icons.Outlined.Lightbulb);

    fun matches(model: GoModel): Boolean = when (this) {
        ALL -> true
        FREE -> model.free
        IMAGES -> MediaKind.IMAGE in model.media
        THINKING -> model.efforts.isNotEmpty()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSheet(
    currentModelId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onOpened: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ModelFilter.ALL) }
    val trimmed = query.trim()
    val models by ModelCatalog.modelsFlow.collectAsStateWithLifecycle()
    val filtered = remember(models, trimmed, filter) {
        models.filter { model ->
            filter.matches(model) && (
                trimmed.isEmpty() ||
                    model.name.contains(trimmed, ignoreCase = true) ||
                    model.id.contains(trimmed, ignoreCase = true)
                )
        }
    }
    val freeCount = remember(models) { models.count { it.free } }
    val currentIsCustom = models.none { it.id == currentModelId }
    val offerCustom = trimmed.isNotEmpty() &&
        trimmed != currentModelId &&
        models.none { it.id.equals(trimmed, ignoreCase = true) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (models.indexOfFirst { it.id == currentModelId } - 2).coerceAtLeast(0),
    )

    LaunchedEffect(Unit) { onOpened() }

    // Start at the current model, then return to the top whenever the search or filter changes.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(trimmed, filter) {
        if (settled) listState.scrollToItem(0) else settled = true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = colors.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(colors.outline.copy(alpha = 0.6f), RoundedCornerShape(50)),
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.88f)) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "Select model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (freeCount > 0) "${models.size} models · $freeCount free" else "${models.size} models",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSubmit = { if (offerCustom) onSelect(trimmed) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModelFilter.entries.forEach { option ->
                    ChoiceChip(
                        text = option.label,
                        selected = filter == option,
                        onClick = { filter = option },
                        icon = option.icon,
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (currentIsCustom && trimmed.isEmpty() && filter == ModelFilter.ALL) {
                    item(key = "current-custom") {
                        ModelRow(
                            name = currentModelId,
                            detail = "Custom model ID",
                            selected = true,
                            onClick = { onSelect(currentModelId) },
                        )
                    }
                }
                items(filtered, key = { it.id }) { model ->
                    ModelRow(
                        model = model,
                        selected = model.id == currentModelId,
                        onClick = { onSelect(model.id) },
                    )
                }
                if (offerCustom) {
                    item(key = "use-custom") {
                        CustomModelRow(modelId = trimmed, onClick = { onSelect(trimmed) })
                    }
                }
                val hint = when {
                    filtered.isEmpty() && trimmed.isEmpty() -> "No models match this filter right now."
                    filtered.isEmpty() && !offerCustom -> "No models match “$trimmed”."
                    filter == ModelFilter.FREE && trimmed.isEmpty() ->
                        "Free models are available for a limited time and change often. " +
                            "They keep working after you reach your plan's usage limit."
                    trimmed.isEmpty() -> "Not listed? Type a model ID in the search box to use it."
                    else -> null
                }
                if (hint != null) item(key = "hint") {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(OcShapes.control)
            .background(colors.surfaceContainerLowest)
            .border(1.dp, colors.outlineVariant, OcShapes.control)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = "Search or enter a model ID",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Clear search",
                modifier = Modifier
                    .clip(OcShapes.tag)
                    .clickable { onValueChange("") }
                    .padding(4.dp)
                    .size(16.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelRow(model: GoModel, selected: Boolean, onClick: () -> Unit) {
    ModelRow(
        name = model.name,
        detail = model.id,
        selected = selected,
        free = model.free,
        capabilities = buildList {
            if (model.efforts.isNotEmpty()) add(Icons.Outlined.Lightbulb to "Thinking effort")
            if (MediaKind.IMAGE in model.media) add(Icons.Outlined.Image to "Image input")
            if (MediaKind.AUDIO in model.media) add(Icons.Outlined.GraphicEq to "Audio input")
        },
        onClick = onClick,
    )
}

@Composable
private fun ModelRow(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    free: Boolean = false,
    capabilities: List<Pair<ImageVector, String>> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OcShapes.control)
            .background(if (selected) colors.surfaceContainerHighest else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (free) {
                    Spacer(Modifier.width(8.dp))
                    Tag("Free")
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (capabilities.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                capabilities.forEach { (icon, label) ->
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(15.dp),
                        tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = "Selected",
                    modifier = Modifier.size(18.dp),
                    tint = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun CustomModelRow(modelId: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OcShapes.control)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Use “$modelId”",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Custom model ID",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
