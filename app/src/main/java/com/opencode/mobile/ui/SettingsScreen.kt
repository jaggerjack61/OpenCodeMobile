package com.opencode.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.opencode.mobile.BuildConfig
import com.opencode.mobile.data.AppSettings
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.data.ThemeMode
import com.opencode.mobile.data.ThinkingEffort
import com.opencode.mobile.vm.SettingsEvent
import com.opencode.mobile.vm.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSaved: (AppSettings) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()
    var showModels by remember { mutableStateOf(false) }
    val catalog by ModelCatalog.modelsFlow.collectAsStateWithLifecycle()
    val model = remember(catalog, state.modelId) { catalog.firstOrNull { it.id == state.modelId } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is SettingsEvent.Saved -> onSaved(event.settings)
            }
        }
    }

    Scaffold(
        // Keeps the endpoint field and the buttons under it reachable while the keyboard is open.
        modifier = Modifier.imePadding(),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                    actions = {
                        TextButton(onClick = viewModel::save) {
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
                if (scrollState.canScrollBackward) HorizontalDivider(color = colors.outlineVariant)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            ConnectionCard()

            SettingsSection("API key") {
                ApiKeyField(value = state.apiKey, onValueChange = viewModel::setApiKey)
                Hint("Encrypted with the Android Keystore and stored only on this device.")
            }

            SettingsSection("New chats") {
                Panel(modifier = Modifier.clickable { showModels = true }) {
                    Row(
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = model?.name ?: state.modelId,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (model?.free == true) {
                                    Spacer(Modifier.width(8.dp))
                                    Tag("Free")
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (model == null) "Custom model ID" else state.modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "Change model",
                            modifier = Modifier.size(20.dp),
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                val effortChoices = remember(catalog, state.modelId) {
                    listOf(ThinkingEffort.AUTO) + ModelCatalog.effortsFor(state.modelId)
                }
                if (effortChoices.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        effortChoices.forEach { effort ->
                            ChoiceChip(
                                text = effort.label,
                                selected = state.thinkingEffort == effort,
                                onClick = { viewModel.setThinkingEffort(effort) },
                            )
                        }
                    }
                }
                Hint(
                    if (effortChoices.size > 1) {
                        "New chats start with the model and thinking effort you used last. " +
                            "Change them here or from any chat."
                    } else {
                        "New chats start with the model you used last. This model has no " +
                            "adjustable thinking effort."
                    },
                )
            }

            SettingsSection("Endpoint") {
                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = viewModel::setBaseUrl,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = OcShapes.control,
                    colors = fieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                )
                Hint("OpenAI-compatible base URL for OpenCode Go.")
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SecondaryButton(
                        text = if (state.testing) "Testing…" else "Test connection",
                        onClick = viewModel::testConnection,
                        enabled = !state.testing,
                        leading = if (state.testing) {
                            { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) }
                        } else {
                            null
                        },
                    )
                    TextButton(onClick = viewModel::resetBaseUrl) { Text("Reset URL") }
                }
            }

            SettingsSection("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        ChoiceChip(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            selected = state.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                        )
                    }
                }
            }

            SettingsSection("About") {
                Text("OpenCode Mobile", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Version ${appVersion()} · Community client for OpenCode Go",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showModels) {
        ModelSheet(
            currentModelId = state.modelId,
            onSelect = { selected ->
                viewModel.setModelId(selected)
                showModels = false
            },
            onDismiss = { showModels = false },
            onOpened = viewModel::refreshModels,
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(28.dp))
    SectionLabel(title)
    Spacer(Modifier.height(10.dp))
    content()
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
            .ifBlank { BuildConfig.VERSION_NAME }
    }
}

@Composable
private fun ApiKeyField(value: String, onValueChange: (String) -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Paste your OpenCode API key", style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = OcShapes.control,
        colors = fieldColors(),
        textStyle = MaterialTheme.typography.bodyMedium,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Hide API key" else "Show API key",
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    )
}

@Composable
private fun ConnectionCard() {
    val uriHandler = LocalUriHandler.current
    val colors = MaterialTheme.colorScheme
    Panel {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OpenCode Go",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Tag("Go")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Grab an API key from the OpenCode Console and paste it below. Go plans include " +
                    "GLM, Kimi, DeepSeek, Qwen and more, plus free models that keep working after " +
                    "you reach your usage limit.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            SecondaryButton(
                text = "Open the OpenCode Console",
                onClick = { uriHandler.openUri("https://opencode.ai/console") },
                leading = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                },
            )
        }
    }
}
