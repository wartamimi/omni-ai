package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

@Serializable
enum class MessageStatus { COMPLETE, STREAMING, ERROR, CANCELLED }

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val providerId: String?,
    val modelId: String?,
    val personaId: String?,
    val systemPrompt: String?,
    val params: GenerationParams?,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class TokenUsage(
    val inputTokens: Int?,
    val outputTokens: Int?,
    /** True when numbers came from the provider response; false when locally estimated. */
    val providerReported: Boolean,
) {
    val total: Int? get() = if (inputTokens == null && outputTokens == null) null else (inputTokens ?: 0) + (outputTokens ?: 0)
}

@Serializable
data class ChatMessage(
    val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String? = null,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val providerId: String? = null,
    val modelId: String? = null,
    val usage: TokenUsage? = null,
    val latencyMs: Long? = null,
    val errorMessage: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val parentId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val createdAt: Long,
)

@Serializable
enum class AttachmentType { IMAGE, DOCUMENT, AUDIO, VIDEO }

@Serializable
data class Attachment(
    val id: String,
    val type: AttachmentType,
    val displayName: String,
    val mimeType: String,
    /** App-private file path of the (compressed) copy. */
    val localPath: String,
    val sizeBytes: Long,
    /** Text extracted locally from documents; sent to the model instead of the binary. */
    val extractedText: String? = null,
)
