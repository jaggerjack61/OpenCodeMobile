package com.opencode.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
data class ChatAttachment(
    val id: String,
    val kind: String,
    val fileName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
) {
    val mediaKind: MediaKind? get() = MediaKind.entries.firstOrNull { it.name == kind }
}

@Serializable
data class ChatToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
data class ChatMessage(
    val id: String,
    val role: String,
    val content: String = "",
    val reasoning: String = "",
    val model: String? = null,
    val timestamp: Long,
    val error: String? = null,
    val attachments: List<ChatAttachment> = emptyList(),
    val toolCalls: List<ChatToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolDetail: String? = null,
) {
    val isUser: Boolean get() = role == ROLE_USER
    val isTool: Boolean get() = role == ROLE_TOOL

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_TOOL = "tool"
    }
}

@Serializable
data class Conversation(
    val id: String,
    val title: String = DEFAULT_TITLE,
    val modelId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val thinkingEffort: String = ThinkingEffort.AUTO.name,
    val webSearchEnabled: Boolean = true,
    val shellEnabled: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
) {
    companion object {
        const val DEFAULT_TITLE = "New chat"
    }
}

data class ConversationSummary(
    val id: String,
    val title: String,
    val modelId: String,
    val updatedAt: Long,
    val preview: String,
    val messageCount: Int,
)

@Serializable
data class GoModel(
    val id: String,
    val name: String,
    val free: Boolean = false,
    val efforts: List<ThinkingEffort> = emptyList(),
    val media: Set<MediaKind> = emptySet(),
)

enum class MediaKind(val label: String, val mimeTypes: List<String>) {
    IMAGE("Images", listOf("image/*")),
    AUDIO("Audio", listOf("audio/*")),
}

enum class ThinkingEffort(val apiValue: String?, val label: String) {
    AUTO(null, "Auto"),
    NONE("none", "None"),
    MINIMAL("minimal", "Minimal"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
    XHIGH("xhigh", "Extra high"),
    MAX("max", "Max");

    companion object {
        fun from(value: String?): ThinkingEffort =
            entries.firstOrNull { it.name == value || it.apiValue == value } ?: AUTO
    }
}

/**
 * The models offered in the picker. Starts from the list bundled with the app and is replaced by
 * the live OpenCode Go catalog once it has been fetched, so free models that come and go stay
 * current without an app update.
 */
object ModelCatalog {
    const val defaultModelId = "glm-5.3"

    private val lowHighMax = listOf(ThinkingEffort.LOW, ThinkingEffort.HIGH, ThinkingEffort.MAX)
    private val highMax = listOf(ThinkingEffort.HIGH, ThinkingEffort.MAX)
    private val lowMediumXhigh = listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.XHIGH)
    private val lowMediumHigh = listOf(ThinkingEffort.LOW, ThinkingEffort.MEDIUM, ThinkingEffort.HIGH)
    private val lowToMax = listOf(
        ThinkingEffort.LOW,
        ThinkingEffort.MEDIUM,
        ThinkingEffort.HIGH,
        ThinkingEffort.XHIGH,
        ThinkingEffort.MAX,
    )
    private val image = setOf(MediaKind.IMAGE)
    private val imageAudio = setOf(MediaKind.IMAGE, MediaKind.AUDIO)

    /** OpenCode Go lineup as of 2026-11-10. Also sets the display order of the picker. */
    val bundled: List<GoModel> = listOf(
        GoModel("glm-5.3", "GLM-5.3", efforts = lowHighMax),
        GoModel("glm-5.3-flash", "GLM-5.3-Flash", efforts = lowHighMax, media = image),
        GoModel("glm-5.2", "GLM-5.2", efforts = highMax),
        GoModel("kimi-k3.1-pro", "Kimi K3.1 Pro", efforts = lowHighMax, media = image),
        GoModel("kimi-k3", "Kimi K3", efforts = listOf(ThinkingEffort.MAX), media = image),
        GoModel("kimi-k2.7-code", "Kimi K2.7 Code", media = image),
        GoModel("deepseek-v4-pro", "DeepSeek V4 Pro", efforts = highMax),
        GoModel("deepseek-v4.1-flash", "DeepSeek V4.1 Flash", efforts = lowHighMax, media = image),
        GoModel("qwen3.8-max", "Qwen3.8 Max", efforts = lowMediumXhigh, media = image),
        GoModel("qwen3.8-flash", "Qwen3.8 Flash", efforts = lowMediumXhigh, media = image),
        GoModel("qwen3.7-plus", "Qwen3.7 Plus", media = image),
        GoModel("minimax-m3", "MiniMax-M3", media = image),
        GoModel("minimax-m3.1-flash", "MiniMax M3.1 Flash", efforts = lowToMax, media = image),
        GoModel("mimo-v2.6-pro", "MiMo-V2.6-Pro", media = imageAudio),
        GoModel("mimo-v2.6-flash", "MiMo-V2.6-Flash", media = imageAudio),
        GoModel("longcat-2.0", "LongCat-2.0"),
        GoModel("step-5-preview", "Step 5 Preview", efforts = lowMediumHigh, media = image),
        GoModel("hy4", "Hy4", efforts = listOf(ThinkingEffort.NONE, ThinkingEffort.HIGH), media = image),
        GoModel("hy4-preview", "Hy4 preview", efforts = listOf(ThinkingEffort.NONE, ThinkingEffort.HIGH)),
        GoModel("hy3", "Hy3", efforts = listOf(ThinkingEffort.NONE, ThinkingEffort.LOW, ThinkingEffort.HIGH)),
        GoModel("beam-free", "Beam", free = true, efforts = lowToMax),
        GoModel("longcat-2.5-free", "LongCat 2.5", free = true, media = image),
        GoModel("iceman-contributor-free", "Iceman Contributor", free = true),
    )

    private val state = MutableStateFlow(bundled)

    /** Emits whenever the live catalog replaces the current list. */
    val modelsFlow: StateFlow<List<GoModel>> = state.asStateFlow()

    val models: List<GoModel> get() = state.value

    /** Replaces the catalog with a freshly fetched one. An empty list is ignored. */
    fun update(models: List<GoModel>) {
        if (models.isNotEmpty()) state.value = models
    }

    fun reset() {
        state.value = bundled
    }

    fun find(id: String): GoModel? = models.firstOrNull { it.id == id }

    fun nameFor(id: String): String = find(id)?.name ?: id

    fun isFree(id: String): Boolean = find(id)?.free == true

    fun effortsFor(id: String): List<ThinkingEffort> {
        val model = find(id) ?: return ThinkingEffort.entries.filter { it != ThinkingEffort.AUTO }
        return model.efforts
    }

    fun resolveEffort(id: String, effort: ThinkingEffort): ThinkingEffort {
        if (effort == ThinkingEffort.AUTO) return ThinkingEffort.AUTO
        return if (effortsFor(id).contains(effort)) effort else ThinkingEffort.AUTO
    }

    fun mediaKindsFor(id: String): Set<MediaKind> {
        val model = find(id) ?: return MediaKind.entries.toSet()
        return model.media
    }
}
