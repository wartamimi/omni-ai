package com.omniai.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "providers", indices = [Index("isDefault")])
data class ProviderEntity(
    @PrimaryKey val id: String,
    val definitionId: String?,
    val name: String,
    val description: String,
    val iconEmoji: String?,
    val category: String,
    val protocol: String,
    val baseUrl: String,
    val authType: String,
    val authName: String?,
    val chatPath: String?,
    val modelsPath: String?,
    val apiVersion: String?,
    val customHeadersJson: String,
    val customParamsJson: String?,
    val timeoutSeconds: Int,
    val streamingEnabled: Boolean,
    val enabled: Boolean,
    val isDefault: Boolean,
    val defaultModelId: String?,
    val customMappingJson: String?,
    val maxRetries: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Credential metadata only. The encrypted secret is stored by CredentialVault, never in this table. */
@Entity(
    tableName = "credentials",
    foreignKeys = [ForeignKey(entity = ProviderEntity::class, parentColumns = ["id"], childColumns = ["providerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("providerId")],
)
data class CredentialEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val label: String,
    val maskedHint: String,
    val status: String,
    val isActive: Boolean,
    val createdAt: Long,
    val lastValidatedAt: Long?,
)

@Entity(
    tableName = "models",
    primaryKeys = ["providerId", "modelId"],
    foreignKeys = [ForeignKey(entity = ProviderEntity::class, parentColumns = ["id"], childColumns = ["providerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("isFavorite"), Index("lastUsedAt")],
)
data class ModelEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String?,
    val contextWindow: Int?,
    val maxOutputTokens: Int?,
    val inputPricePerMillion: Double?,
    val outputPricePerMillion: Double?,
    val pricingSource: String,
    val capabilitiesJson: String,
    val userCategoriesJson: String,
    val source: String,
    val isFavorite: Boolean,
    val lastUsedAt: Long?,
)

@Entity(tableName = "conversations", indices = [Index("updatedAt"), Index("isPinned"), Index("isArchived")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val providerId: String?,
    val modelId: String?,
    val personaId: String?,
    val systemPrompt: String?,
    val paramsJson: String?,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val summary: String? = null,
    val summaryUntil: Long? = null,
    val summaryCount: Int? = null,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(entity = ConversationEntity::class, parentColumns = ["id"], childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["conversationId", "createdAt"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoning: String?,
    val status: String,
    val providerId: String?,
    val modelId: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val usageProviderReported: Boolean?,
    val latencyMs: Long?,
    val errorMessage: String?,
    val toolCallsJson: String?,
    val toolCallId: String?,
    val parentId: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = MessageEntity::class, parentColumns = ["id"], childColumns = ["messageId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val type: String,
    val displayName: String,
    val mimeType: String,
    val localPath: String,
    val sizeBytes: Long,
    val extractedText: String?,
)

@Entity(tableName = "usage", indices = [Index("timestamp"), Index("providerId")])
data class UsageEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val conversationId: String?,
    val inputTokens: Int,
    val outputTokens: Int,
    val providerReported: Boolean,
    val estimatedCostUsd: Double?,
    val success: Boolean,
    val httpStatus: Int?,
    val errorKind: String?,
    val latencyMs: Long?,
    val timestamp: Long,
    val isManualAdjustment: Boolean,
)

@Entity(tableName = "prompts", indices = [Index("category")])
data class PromptEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val category: String,
    val isFavorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val avatarEmoji: String,
    val systemPrompt: String,
    val defaultProviderId: String?,
    val defaultModelId: String?,
    val temperature: Double?,
    val topP: Double?,
    val maxOutputTokens: Int?,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "tools")
data class ToolEntity(
    @PrimaryKey val name: String,
    val description: String,
    val isBuiltIn: Boolean,
    val permission: String,
    val configJson: String?,
)

@Entity(tableName = "tool_logs", indices = [Index("timestamp")])
data class ToolLogEntity(
    @PrimaryKey val id: String,
    val conversationId: String?,
    val toolName: String,
    val argumentsJson: String,
    val resultPreview: String,
    val approved: Boolean,
    val success: Boolean,
    val timestamp: Long,
)

@Entity(
    tableName = "provider_health",
    foreignKeys = [ForeignKey(entity = ProviderEntity::class, parentColumns = ["id"], childColumns = ["providerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["providerId", "timestamp"])],
)
data class ProviderHealthEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val providerId: String,
    val timestamp: Long,
    val success: Boolean,
    val httpStatus: Int?,
    val latencyMs: Long?,
    val errorKind: String?,
)

/** Key/value store for structured settings that relate to database content (e.g. routing rules). */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)
