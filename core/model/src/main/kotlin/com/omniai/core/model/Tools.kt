package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ToolPermission(val label: String) {
    /** The tool runs without asking (local, side-effect-free tools only). */
    ALLOW("Always allow"),
    /** Ask the user each time the model calls it. */
    ASK("Ask every time"),
    DENY("Disabled"),
}

@Serializable
data class ToolConfig(
    val id: String,
    val name: String,
    val description: String,
    val isBuiltIn: Boolean,
    val permission: ToolPermission,
    /** For custom HTTP tools: JSON config; null for built-ins. */
    val configJson: String? = null,
)

@Serializable
data class ToolExecutionLog(
    val id: String,
    val conversationId: String?,
    val toolName: String,
    val argumentsJson: String,
    val resultPreview: String,
    val approved: Boolean,
    val success: Boolean,
    val timestamp: Long,
)
