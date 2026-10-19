package com.opencode.mobile.data

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

data class GoModel(
    val id: String,
    val name: String,
    val badge: String? = null,
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

object ModelCatalog {
    val defaultModelId = "glm-5.3"

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

    val models: List<GoModel> = listOf(
        GoModel("glm-5.3", "GLM-5.3", efforts = lowHighMax),
        GoModel("glm-5.3-flash", "GLM-5.3 Flash", efforts = lowHighMax, media = setOf(MediaKind.IMAGE)),
        GoModel("glm-5.2", "GLM-5.2", efforts = highMax),
        GoModel("kimi-k3", "Kimi K3", efforts = listOf(ThinkingEffort.MAX), media = setOf(MediaKind.IMAGE)),
        GoModel("kimi-k2.7-code", "Kimi K2.7 Code", media = setOf(MediaKind.IMAGE)),
        GoModel("deepseek-v4-pro", "DeepSeek V4 Pro", efforts = highMax),
        GoModel(
            "deepseek-v4.1-flash",
            "DeepSeek V4.1 Flash",
            efforts = lowHighMax,
            media = setOf(MediaKind.IMAGE),
        ),
        GoModel("qwen3.8-max", "Qwen3.8 Max", efforts = lowMediumXhigh, media = setOf(MediaKind.IMAGE)),
        GoModel("qwen3.8-flash", "Qwen3.8 Flash", efforts = lowMediumXhigh, media = setOf(MediaKind.IMAGE)),
        GoModel("qwen3.7-plus", "Qwen3.7 Plus", media = setOf(MediaKind.IMAGE)),
        GoModel("minimax-m3", "MiniMax M3"),
        GoModel("minimax-m2.7", "MiniMax M2.7"),
        GoModel(
            "minimax-m3.1-flash-free",
            "MiniMax M3.1 Flash",
            badge = "Free",
            efforts = lowToMax,
            media = setOf(MediaKind.IMAGE),
        ),
        GoModel(
            "mimo-v2.6-pro",
            "MiMo V2.6 Pro",
            media = setOf(MediaKind.IMAGE, MediaKind.AUDIO),
        ),
        GoModel(
            "mimo-v2.6-flash",
            "MiMo V2.6 Flash",
            media = setOf(MediaKind.IMAGE, MediaKind.AUDIO),
        ),
        GoModel("mimo-v2.5-pro", "MiMo V2.5 Pro"),
        GoModel(
            "mimo-v2.5",
            "MiMo V2.5",
            media = setOf(MediaKind.IMAGE, MediaKind.AUDIO),
        ),
        GoModel("longcat-2.0", "LongCat-2.0"),
        GoModel(
            "longcat-2.5-preview-free",
            "LongCat 2.5 Preview",
            badge = "Free",
            media = setOf(MediaKind.IMAGE),
        ),
        GoModel(
            "step-5-preview",
            "Step 5 Preview",
            efforts = lowMediumHigh,
            media = setOf(MediaKind.IMAGE),
        ),
        GoModel(
            "step-5-preview-free",
            "Step 5 Preview",
            badge = "Free",
            efforts = lowMediumHigh,
            media = setOf(MediaKind.IMAGE),
        ),
        GoModel(
            "hy3",
            "Hy3",
            efforts = listOf(ThinkingEffort.NONE, ThinkingEffort.LOW, ThinkingEffort.HIGH),
        ),
        GoModel(
            "hy4-preview",
            "Hy4 Preview",
            efforts = listOf(ThinkingEffort.NONE, ThinkingEffort.HIGH),
        ),
        GoModel("beam-free", "Beam", badge = "Free", efforts = lowToMax),
        GoModel("cat-jam-free", "Cat Jam", badge = "Free", efforts = lowToMax),
    )

    fun nameFor(id: String): String = models.firstOrNull { it.id == id }?.name ?: id

    fun effortsFor(id: String): List<ThinkingEffort> {
        val model = models.firstOrNull { it.id == id } ?: return ThinkingEffort.entries.filter {
            it != ThinkingEffort.AUTO
        }
        return model.efforts
    }

    fun resolveEffort(id: String, effort: ThinkingEffort): ThinkingEffort {
        if (effort == ThinkingEffort.AUTO) return ThinkingEffort.AUTO
        return if (effortsFor(id).contains(effort)) effort else ThinkingEffort.AUTO
    }

    fun mediaKindsFor(id: String): Set<MediaKind> {
        val model = models.firstOrNull { it.id == id } ?: return MediaKind.entries.toSet()
        return model.media
    }
}
