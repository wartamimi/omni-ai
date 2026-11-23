package com.omniai.core.data.mapper

import com.omniai.core.database.entity.AttachmentEntity
import com.omniai.core.database.entity.ConversationEntity
import com.omniai.core.database.entity.CredentialEntity
import com.omniai.core.database.entity.MessageEntity
import com.omniai.core.database.entity.ModelEntity
import com.omniai.core.database.entity.PersonaEntity
import com.omniai.core.database.entity.PromptEntity
import com.omniai.core.database.entity.ProviderEntity
import com.omniai.core.database.entity.ProviderHealthEntity
import com.omniai.core.database.entity.ToolEntity
import com.omniai.core.database.entity.ToolLogEntity
import com.omniai.core.database.entity.UsageEntity
import com.omniai.core.model.AIModel
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.Attachment
import com.omniai.core.model.AttachmentType
import com.omniai.core.model.AuthConfig
import com.omniai.core.model.AuthType
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.Conversation
import com.omniai.core.model.CredentialInfo
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.CustomApiMapping
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.HeaderEntry
import com.omniai.core.model.MessageRole
import com.omniai.core.model.MessageStatus
import com.omniai.core.model.ModelCapabilities
import com.omniai.core.model.ModelCategory
import com.omniai.core.model.ModelPricing
import com.omniai.core.model.ModelSource
import com.omniai.core.model.Persona
import com.omniai.core.model.PricingSource
import com.omniai.core.model.PromptCategory
import com.omniai.core.model.PromptTemplate
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderHealthEvent
import com.omniai.core.model.TokenUsage
import com.omniai.core.model.ToolConfig
import com.omniai.core.model.ToolExecutionLog
import com.omniai.core.model.ToolPermission
import com.omniai.core.model.UsageRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json

internal val dbJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

fun ProviderEntity.toModel() = ProviderConfig(
    id = id, definitionId = definitionId, name = name, description = description, iconEmoji = iconEmoji,
    category = enumOr(category, ProviderCategory.CUSTOM), protocol = enumOr(protocol, ApiProtocol.OPENAI_CHAT_COMPLETIONS),
    baseUrl = baseUrl, auth = AuthConfig(enumOr(authType, AuthType.BEARER), authName), chatPath = chatPath, modelsPath = modelsPath,
    apiVersion = apiVersion,
    customHeaders = runCatching { dbJson.decodeFromString(ListSerializer(HeaderEntry.serializer()), customHeadersJson) }.getOrDefault(emptyList()),
    customParamsJson = customParamsJson, timeoutSeconds = timeoutSeconds, streamingEnabled = streamingEnabled, enabled = enabled,
    isDefault = isDefault, defaultModelId = defaultModelId,
    customMapping = customMappingJson?.let { runCatching { dbJson.decodeFromString(CustomApiMapping.serializer(), it) }.getOrNull() },
    maxRetries = maxRetries, createdAt = createdAt, updatedAt = updatedAt,
)

fun ProviderConfig.toEntity() = ProviderEntity(
    id = id, definitionId = definitionId, name = name, description = description, iconEmoji = iconEmoji, category = category.name,
    protocol = protocol.name, baseUrl = baseUrl, authType = auth.type.name, authName = auth.name, chatPath = chatPath, modelsPath = modelsPath,
    apiVersion = apiVersion, customHeadersJson = dbJson.encodeToString(ListSerializer(HeaderEntry.serializer()), customHeaders),
    customParamsJson = customParamsJson, timeoutSeconds = timeoutSeconds, streamingEnabled = streamingEnabled, enabled = enabled,
    isDefault = isDefault, defaultModelId = defaultModelId,
    customMappingJson = customMapping?.let { dbJson.encodeToString(CustomApiMapping.serializer(), it) },
    maxRetries = maxRetries, createdAt = createdAt, updatedAt = updatedAt,
)

fun CredentialEntity.toModel() = CredentialInfo(id, providerId, label, maskedHint, enumOr(status, CredentialStatus.UNVERIFIED), isActive, createdAt, lastValidatedAt)

fun ModelEntity.toModel() = AIModel(
    providerId = providerId, modelId = modelId, displayName = displayName, description = description, contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens, pricing = ModelPricing(inputPricePerMillion, outputPricePerMillion, enumOr(pricingSource, PricingSource.NONE)),
    capabilities = runCatching { dbJson.decodeFromString(ModelCapabilities.serializer(), capabilitiesJson) }.getOrDefault(ModelCapabilities.Unknown),
    userCategories = runCatching { dbJson.decodeFromString(SetSerializer(ModelCategory.serializer()), userCategoriesJson) }.getOrDefault(emptySet()),
    source = enumOr(source, ModelSource.DISCOVERED), isFavorite = isFavorite, lastUsedAt = lastUsedAt,
)

fun AIModel.toEntity() = ModelEntity(
    providerId = providerId, modelId = modelId, displayName = displayName, description = description, contextWindow = contextWindow,
    maxOutputTokens = maxOutputTokens, inputPricePerMillion = pricing.inputPerMillion, outputPricePerMillion = pricing.outputPerMillion,
    pricingSource = pricing.source.name, capabilitiesJson = dbJson.encodeToString(ModelCapabilities.serializer(), capabilities),
    userCategoriesJson = dbJson.encodeToString(SetSerializer(ModelCategory.serializer()), userCategories),
    source = source.name, isFavorite = isFavorite, lastUsedAt = lastUsedAt,
)

fun ConversationEntity.toModel() = Conversation(
    id = id, title = title, providerId = providerId, modelId = modelId, personaId = personaId, systemPrompt = systemPrompt,
    params = paramsJson?.let { runCatching { dbJson.decodeFromString(GenerationParams.serializer(), it) }.getOrNull() },
    isPinned = isPinned, isArchived = isArchived, createdAt = createdAt, updatedAt = updatedAt,
)

fun Conversation.toEntity(existing: ConversationEntity? = null) = ConversationEntity(
    id = id, title = title, providerId = providerId, modelId = modelId, personaId = personaId, systemPrompt = systemPrompt,
    paramsJson = params?.let { dbJson.encodeToString(GenerationParams.serializer(), it) }, isPinned = isPinned, isArchived = isArchived,
    createdAt = createdAt, updatedAt = updatedAt,
    summary = existing?.summary, summaryUntil = existing?.summaryUntil, summaryCount = existing?.summaryCount,
)

fun MessageEntity.toModel(attachments: List<Attachment>) = ChatMessage(
    id = id, conversationId = conversationId, role = enumOr(role, MessageRole.USER), content = content, reasoning = reasoning,
    status = enumOr(status, MessageStatus.COMPLETE), providerId = providerId, modelId = modelId,
    usage = if (inputTokens == null && outputTokens == null) null else TokenUsage(inputTokens, outputTokens, usageProviderReported ?: false),
    latencyMs = latencyMs, errorMessage = errorMessage, toolCallsJson = toolCallsJson, toolCallId = toolCallId, parentId = parentId,
    attachments = attachments, createdAt = createdAt,
)

fun ChatMessage.toEntity() = MessageEntity(
    id = id, conversationId = conversationId, role = role.name, content = content, reasoning = reasoning, status = status.name,
    providerId = providerId, modelId = modelId, inputTokens = usage?.inputTokens, outputTokens = usage?.outputTokens,
    usageProviderReported = usage?.providerReported, latencyMs = latencyMs, errorMessage = errorMessage, toolCallsJson = toolCallsJson,
    toolCallId = toolCallId, parentId = parentId, createdAt = createdAt,
)

fun AttachmentEntity.toModel() = Attachment(id, enumOr(type, AttachmentType.DOCUMENT), displayName, mimeType, localPath, sizeBytes, extractedText)
fun Attachment.toEntity(messageId: String) = AttachmentEntity(id, messageId, type.name, displayName, mimeType, localPath, sizeBytes, extractedText)

fun UsageEntity.toModel() = UsageRecord(
    id, providerId, providerName, modelId, conversationId, inputTokens, outputTokens, providerReported, estimatedCostUsd, success,
    httpStatus, errorKind, latencyMs, timestamp, isManualAdjustment,
)

fun UsageRecord.toEntity() = UsageEntity(
    id, providerId, providerName, modelId, conversationId, inputTokens, outputTokens, providerReported, estimatedCostUsd, success,
    httpStatus, errorKind, latencyMs, timestamp, isManualAdjustment,
)

fun PromptEntity.toModel() = PromptTemplate(id, title, content, enumOr(category, PromptCategory.GENERAL), isFavorite, createdAt, updatedAt)
fun PromptTemplate.toEntity() = PromptEntity(id, title, content, category.name, isFavorite, createdAt, updatedAt)

fun PersonaEntity.toModel() = Persona(
    id, name, description, avatarEmoji, systemPrompt, defaultProviderId, defaultModelId, temperature, topP, maxOutputTokens, isBuiltIn, createdAt, updatedAt,
)
fun Persona.toEntity() = PersonaEntity(
    id, name, description, avatarEmoji, systemPrompt, defaultProviderId, defaultModelId, temperature, topP, maxOutputTokens, isBuiltIn, createdAt, updatedAt,
)

fun ToolEntity.toModel() = ToolConfig(name, name, description, isBuiltIn, enumOr(permission, ToolPermission.ASK), configJson)
fun ToolLogEntity.toModel() = ToolExecutionLog(id, conversationId, toolName, argumentsJson, resultPreview, approved, success, timestamp)
fun ToolExecutionLog.toEntity() = ToolLogEntity(id, conversationId, toolName, argumentsJson, resultPreview, approved, success, timestamp)

fun ProviderHealthEntity.toModel() = ProviderHealthEvent(providerId, timestamp, success, httpStatus, latencyMs, errorKind)
fun ProviderHealthEvent.toEntity() = ProviderHealthEntity(providerId = providerId, timestamp = timestamp, success = success, httpStatus = httpStatus, latencyMs = latencyMs, errorKind = errorKind)
