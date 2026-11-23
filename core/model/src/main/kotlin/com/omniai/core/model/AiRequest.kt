package com.omniai.core.model

/** Provider-neutral request built by the chat engine. */
data class AIRequest(
    val requestId: String,
    val modelId: String,
    val systemPrompt: String?,
    val messages: List<PromptMessage>,
    val params: GenerationParams,
    val tools: List<ToolSpec> = emptyList(),
)

data class PromptMessage(
    val role: MessageRole,
    val text: String,
    val images: List<ImagePart> = emptyList(),
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
)

class ImagePart(val mimeType: String, val base64Data: String) {
    override fun toString(): String = "ImagePart($mimeType, ${base64Data.length} b64 chars)"
}

data class ToolSpec(val name: String, val description: String, val parametersJsonSchema: String)

data class ToolCall(val id: String, val name: String, val argumentsJson: String)

data class AIResponse(
    val text: String,
    val reasoning: String?,
    val usage: TokenUsage?,
    val finishReason: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    val httpStatus: Int,
)

sealed interface AIStreamEvent {
    data object Started : AIStreamEvent
    data class TextDelta(val text: String) : AIStreamEvent
    data class ReasoningDelta(val text: String) : AIStreamEvent
    data class ToolCallDelta(val index: Int, val id: String?, val name: String?, val argumentsFragment: String) : AIStreamEvent
    data class Usage(val usage: TokenUsage) : AIStreamEvent
    data class Completed(val finishReason: String?) : AIStreamEvent
    data class Failed(val error: ProviderError) : AIStreamEvent
}

data class ConnectionResult(
    val success: Boolean,
    val httpStatus: Int?,
    val latencyMs: Long,
    val message: String,
    val error: ProviderError? = null,
    val modelCount: Int? = null,
)
