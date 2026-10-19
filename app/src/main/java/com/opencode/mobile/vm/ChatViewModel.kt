package com.opencode.mobile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.mobile.data.AttachmentStore
import com.opencode.mobile.data.ChatAttachment
import com.opencode.mobile.data.ChatMessage
import com.opencode.mobile.data.ChatToolCall
import com.opencode.mobile.data.Conversation
import com.opencode.mobile.data.ConversationStore
import com.opencode.mobile.data.ModelCatalog
import com.opencode.mobile.data.SettingsStore
import com.opencode.mobile.data.ThinkingEffort
import com.opencode.mobile.network.ChatClient
import com.opencode.mobile.network.ShellTool
import com.opencode.mobile.network.StreamEvent
import com.opencode.mobile.network.WebSearchClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.UUID

data class ChatUiState(
    val conversation: Conversation? = null,
    val isLoading: Boolean = true,
    val isStreaming: Boolean = false,
    val draft: String = "",
    val pendingAttachments: List<ChatAttachment> = emptyList(),
    val importingAttachments: Boolean = false,
)

sealed interface ChatEvent {
    data class Message(val text: String) : ChatEvent
    data object Deleted : ChatEvent
}

private data class MutableToolCall(
    var id: String? = null,
    var name: String? = null,
    val arguments: StringBuilder = StringBuilder(),
)

class ChatViewModel(
    private val conversationId: String?,
    private val conversationStore: ConversationStore,
    private val settingsStore: SettingsStore,
    private val attachmentStore: AttachmentStore,
    private val chatClient: ChatClient,
    private val webSearchClient: WebSearchClient,
    private val shellTool: ShellTool,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    private var streamJob: Job? = null
    private var persisted = false
    private var isDeleted = false
    private val saveMutex = Mutex()

    init {
        if (conversationId == null) {
            val settings = settingsStore.load()
            _state.value = ChatUiState(
                conversation = conversationStore.newConversation(
                    modelId = settings.modelId,
                    thinkingEffort = ModelCatalog.resolveEffort(
                        settings.modelId,
                        ThinkingEffort.from(settings.thinkingEffort),
                    ),
                ),
                isLoading = false,
            )
        } else {
            viewModelScope.launch {
                val loaded = conversationStore.load(conversationId)
                val settings = settingsStore.load()
                if (loaded == null) {
                    _state.value = ChatUiState(
                        conversation = conversationStore.newConversation(
                            modelId = settings.modelId,
                            thinkingEffort = ModelCatalog.resolveEffort(
                                settings.modelId,
                                ThinkingEffort.from(settings.thinkingEffort),
                            ),
                        ),
                        isLoading = false,
                    )
                    _events.tryEmit(ChatEvent.Message("That conversation is no longer available."))
                } else {
                    persisted = true
                    _state.value = ChatUiState(conversation = loaded, isLoading = false)
                }
            }
        }
    }

    fun setDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun addAttachments(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        val current = _state.value
        val conversation = current.conversation ?: return
        if (current.isStreaming || current.importingAttachments) return
        val allowed = ModelCatalog.mediaKindsFor(conversation.modelId)
        if (allowed.isEmpty()) {
            _events.tryEmit(ChatEvent.Message("This model does not accept image or audio attachments."))
            return
        }
        val remaining = MAX_ATTACHMENTS - current.pendingAttachments.size
        if (remaining <= 0) {
            _events.tryEmit(ChatEvent.Message("You can attach up to $MAX_ATTACHMENTS files per message."))
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(importingAttachments = true) }
            val imported = mutableListOf<ChatAttachment>()
            for (uri in uriStrings.take(remaining)) {
                runCatching { attachmentStore.import(conversation.id, uri, allowed) }
                    .onSuccess { imported += it }
                    .onFailure {
                        _events.emit(ChatEvent.Message(it.message ?: "Could not attach that file."))
                    }
            }
            _state.update {
                it.copy(
                    pendingAttachments = it.pendingAttachments + imported,
                    importingAttachments = false,
                )
            }
        }
    }

    fun removePendingAttachment(attachmentId: String) {
        val attachment = _state.value.pendingAttachments.firstOrNull { it.id == attachmentId } ?: return
        attachmentStore.delete(attachment)
        _state.update { it.copy(pendingAttachments = it.pendingAttachments.filterNot { a -> a.id == attachmentId }) }
    }

    fun send() {
        val current = _state.value
        val conversation = current.conversation ?: return
        val text = current.draft.trim()
        val attachments = current.pendingAttachments
        if ((text.isEmpty() && attachments.isEmpty()) || current.isStreaming) return

        val settings = settingsStore.load()
        if (!settings.hasApiKey) {
            _events.tryEmit(ChatEvent.Message("Add your OpenCode API key in Settings first."))
            return
        }

        val now = System.currentTimeMillis()
        val titleSource = text.ifBlank { attachments.firstOrNull()?.fileName.orEmpty() }
        val updated = conversation.copy(
            title = if (conversation.title == Conversation.DEFAULT_TITLE) {
                autoTitle(titleSource)
            } else {
                conversation.title
            },
            messages = conversation.messages + ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatMessage.ROLE_USER,
                content = text,
                timestamp = now,
                attachments = attachments,
            ),
            updatedAt = now,
        )

        _state.update {
            it.copy(
                conversation = updated,
                draft = "",
                pendingAttachments = emptyList(),
                isStreaming = true,
            )
        }
        runAgent(updated)
    }

    fun stop() {
        streamJob?.cancel()
    }

    fun retry() {
        val current = _state.value
        if (current.isStreaming) return
        val conversation = current.conversation ?: return
        val last = conversation.messages.lastOrNull() ?: return
        if (last.role != ChatMessage.ROLE_ASSISTANT || last.error == null) return

        val updated = conversation.copy(
            messages = conversation.messages.dropLast(1),
            updatedAt = System.currentTimeMillis(),
        )
        _state.update { it.copy(conversation = updated, isStreaming = true) }
        runAgent(updated)
    }

    fun selectThinkingEffort(effort: ThinkingEffort) {
        if (_state.value.isStreaming) return
        val conversation = _state.value.conversation ?: return
        val resolved = ModelCatalog.resolveEffort(conversation.modelId, effort)
        if (ThinkingEffort.from(conversation.thinkingEffort) == resolved) return
        val updated = conversation.copy(thinkingEffort = resolved.name)
        _state.update { it.copy(conversation = updated) }
        if (persisted) {
            viewModelScope.launch { saveCurrent() }
        }
    }

    fun selectModel(modelId: String) {
        if (modelId.isBlank() || _state.value.isStreaming) return
        val conversation = _state.value.conversation ?: return
        if (conversation.modelId == modelId) return
        val effort = ModelCatalog.resolveEffort(
            modelId,
            ThinkingEffort.from(conversation.thinkingEffort),
        )
        val allowed = ModelCatalog.mediaKindsFor(modelId)
        val kept = mutableListOf<ChatAttachment>()
        val removed = mutableListOf<ChatAttachment>()
        _state.value.pendingAttachments.forEach { attachment ->
            val kind = attachment.mediaKind
            if (kind != null && kind in allowed) kept += attachment else removed += attachment
        }
        if (removed.isNotEmpty()) {
            removed.forEach(attachmentStore::delete)
            _events.tryEmit(ChatEvent.Message("Removed attachments that this model does not support."))
        }
        val updated = conversation.copy(modelId = modelId, thinkingEffort = effort.name)
        _state.update { it.copy(conversation = updated, pendingAttachments = kept) }
        if (persisted) {
            viewModelScope.launch { saveCurrent() }
        }
    }

    fun setWebSearchEnabled(enabled: Boolean) {
        if (_state.value.isStreaming) return
        val conversation = _state.value.conversation ?: return
        if (conversation.webSearchEnabled == enabled) return
        val updated = conversation.copy(webSearchEnabled = enabled)
        _state.update { it.copy(conversation = updated) }
        if (persisted) {
            viewModelScope.launch { saveCurrent() }
        }
    }

    fun setShellEnabled(enabled: Boolean) {
        if (_state.value.isStreaming) return
        val conversation = _state.value.conversation ?: return
        if (conversation.shellEnabled == enabled) return
        val updated = conversation.copy(shellEnabled = enabled)
        _state.update { it.copy(conversation = updated) }
        if (persisted) {
            viewModelScope.launch { saveCurrent() }
        }
    }

    fun deleteConversation() {
        val conversation = _state.value.conversation ?: return
        isDeleted = true
        viewModelScope.launch {
            streamJob?.cancelAndJoin()
            conversationStore.delete(conversation.id)
            _events.emit(ChatEvent.Deleted)
        }
    }

    override fun onCleared() {
        _state.value.pendingAttachments.forEach(attachmentStore::delete)
        super.onCleared()
    }

    private fun runAgent(start: Conversation) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            val settings = settingsStore.load()
            var conversation = start
            var toolRounds = 0
            var toolsDisabled = false
            try {
                while (true) {
                    val now = System.currentTimeMillis()
                    conversation = conversation.copy(
                        messages = conversation.messages + ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatMessage.ROLE_ASSISTANT,
                            timestamp = now,
                            model = conversation.modelId,
                        ),
                        updatedAt = now,
                    )
                    _state.update { it.copy(conversation = conversation, isStreaming = true) }
                    saveCurrent()

                    val toolCalls = linkedMapOf<Int, MutableToolCall>()
                    var failure: String? = null

                    chatClient.stream(
                        settings = settings,
                        modelId = conversation.modelId,
                        sessionId = conversation.id,
                        messages = conversation.messages,
                        thinkingEffort = ModelCatalog.resolveEffort(
                            conversation.modelId,
                            ThinkingEffort.from(conversation.thinkingEffort),
                        ),
                        webSearchEnabled = conversation.webSearchEnabled && !toolsDisabled,
                        shellEnabled = conversation.shellEnabled && !toolsDisabled,
                        systemHint = if (toolsDisabled) TOOL_LIMIT_HINT else null,
                    ).collect { event ->
                        when (event) {
                            is StreamEvent.Content ->
                                updateAssistant { it.copy(content = it.content + event.text) }
                            is StreamEvent.Reasoning ->
                                updateAssistant { it.copy(reasoning = it.reasoning + event.text) }
                            is StreamEvent.ToolCall -> {
                                val delta = event.delta
                                val call = toolCalls.getOrPut(delta.index) { MutableToolCall() }
                                delta.id?.takeIf { it.isNotBlank() }?.let { call.id = it }
                                delta.name?.takeIf { it.isNotBlank() }?.let { call.name = it }
                                delta.arguments?.let { call.arguments.append(it) }
                            }
                            is StreamEvent.Failure -> {
                                failure = event.message
                                updateAssistant { it.copy(error = event.message) }
                            }
                            StreamEvent.Finished -> Unit
                        }
                    }

                    if (failure != null) break

                    val completedCalls = toolCalls.values.mapNotNull { call ->
                        val name = call.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        ChatToolCall(
                            id = call.id?.takeIf { it.isNotBlank() } ?: "call_${UUID.randomUUID()}",
                            name = name,
                            arguments = call.arguments.toString(),
                        )
                    }
                    if (completedCalls.isEmpty()) break

                    if (toolsDisabled) {
                        updateAssistant {
                            it.copy(error = "The model tried to use a tool after the limit.")
                        }
                        break
                    }

                    if (toolRounds >= MAX_TOOL_ROUNDS) {
                        val current = _state.value.conversation ?: conversation
                        if (current.messages.lastOrNull()?.role == ChatMessage.ROLE_ASSISTANT) {
                            conversation = current.copy(messages = current.messages.dropLast(1))
                            _state.update { it.copy(conversation = conversation) }
                        }
                        toolsDisabled = true
                        continue
                    }

                    updateAssistant { it.copy(toolCalls = completedCalls) }
                    saveCurrent()

                    val toolMessages = completedCalls.map { call ->
                        executeTool(call, conversation)
                    }
                    conversation = (_state.value.conversation ?: conversation).copy(
                        messages = (_state.value.conversation ?: conversation).messages + toolMessages,
                        updatedAt = System.currentTimeMillis(),
                    )
                    _state.update { it.copy(conversation = conversation) }
                    saveCurrent()
                    toolRounds++
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                updateAssistant {
                    it.copy(error = error.message ?: "Something went wrong while contacting OpenCode Go.")
                }
            } finally {
                withContext(NonCancellable) { finishStream() }
            }
        }
    }

    private suspend fun executeTool(call: ChatToolCall, conversation: Conversation): ChatMessage {
        val detail: String
        val result: String
        when (call.name) {
            "web_search" -> {
                val query = extractArgument(call.arguments, "query").orEmpty().trim()
                detail = query
                result = if (!conversation.webSearchEnabled) {
                    "Web search is disabled for this conversation."
                } else if (query.isEmpty()) {
                    "Web search failed: the query was missing."
                } else {
                    runCatching { webSearchClient.search(query) }
                        .getOrElse { "Web search failed: ${it.message ?: "unknown error"}" }
                }
            }
            "shell" -> {
                val command = extractArgument(call.arguments, "command").orEmpty().trim()
                detail = command
                result = if (!conversation.shellEnabled) {
                    "Shell access is disabled for this conversation."
                } else if (command.isEmpty()) {
                    "Shell error: the command was missing."
                } else {
                    runCatching { shellTool.run(command) }
                        .getOrElse { "Shell error: ${it.message ?: "could not run the command"}" }
                }
            }
            else -> {
                detail = call.name
                result = "Unknown tool: ${call.name}"
            }
        }

        return ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatMessage.ROLE_TOOL,
            content = result.take(MAX_TOOL_RESULT_CHARS),
            timestamp = System.currentTimeMillis(),
            model = conversation.modelId,
            toolCallId = call.id,
            toolName = call.name,
            toolDetail = detail,
        )
    }

    private suspend fun finishStream() {
        val conversation = _state.value.conversation
        if (conversation == null) {
            _state.update { it.copy(isStreaming = false) }
            return
        }
        val messages = conversation.messages.toMutableList()
        val last = messages.lastOrNull()
        if (last != null && last.role == ChatMessage.ROLE_ASSISTANT) {
            if (last.error == null &&
                last.content.isBlank() &&
                last.reasoning.isBlank() &&
                last.toolCalls.isEmpty()
            ) {
                messages[messages.lastIndex] = last.copy(
                    error = "The model returned an empty response. Try again.",
                )
            }
        }
        val finalConversation = conversation.copy(
            messages = messages,
            updatedAt = System.currentTimeMillis(),
        )
        _state.update { it.copy(conversation = finalConversation, isStreaming = false) }
        saveCurrent()
    }

    private suspend fun saveCurrent() {
        saveMutex.withLock {
            if (isDeleted) return
            val conversation = _state.value.conversation ?: return
            runCatching { conversationStore.save(conversation) }
                .onSuccess { persisted = true }
        }
    }

    private fun updateAssistant(transform: (ChatMessage) -> ChatMessage) {
        _state.update { state ->
            val conversation = state.conversation ?: return@update state
            val last = conversation.messages.lastOrNull() ?: return@update state
            if (last.role != ChatMessage.ROLE_ASSISTANT) return@update state
            state.copy(
                conversation = conversation.copy(
                    messages = conversation.messages.dropLast(1) + transform(last),
                ),
            )
        }
    }

    private fun extractArgument(arguments: String, key: String): String? {
        val parsed = runCatching {
            Json.parseToJsonElement(arguments).jsonObject
        }.getOrNull()
        (parsed?.get(key) as? JsonPrimitive)?.contentOrNull?.let { return it }
        val pattern = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return pattern.find(arguments)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
    }

    private fun autoTitle(text: String): String {
        val line = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (line.isEmpty()) return Conversation.DEFAULT_TITLE
        return if (line.length <= 48) line else line.take(47).trimEnd() + "…"
    }

    companion object {
        private const val MAX_ATTACHMENTS = 4
        private const val MAX_TOOL_ROUNDS = 5
        private const val MAX_TOOL_RESULT_CHARS = 16_000
        private const val TOOL_LIMIT_HINT =
            "You have reached the tool limit. Answer the user's request now using the information " +
                "already gathered. Do not request more tools."
    }
}
