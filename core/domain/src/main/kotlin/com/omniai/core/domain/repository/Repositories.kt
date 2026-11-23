package com.omniai.core.domain.repository

import com.omniai.core.model.AIModel
import com.omniai.core.model.AppSettings
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.Conversation
import com.omniai.core.model.CredentialInfo
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.ModelKey
import com.omniai.core.model.Persona
import com.omniai.core.model.PromptTemplate
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderHealthEvent
import com.omniai.core.model.ToolConfig
import com.omniai.core.model.ToolExecutionLog
import com.omniai.core.model.ToolPermission
import com.omniai.core.model.UsageRecord
import com.omniai.core.provider.AIProviderAdapter
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface ProviderRepository {
    fun observeProviders(): Flow<List<ProviderConfig>>
    fun observeHealth(providerId: String): Flow<List<ProviderHealthEvent>>
    suspend fun get(id: String): ProviderConfig?
    suspend fun all(): List<ProviderConfig>
    suspend fun upsert(config: ProviderConfig)
    suspend fun delete(id: String)
    suspend fun setDefault(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun duplicate(id: String): ProviderConfig?
    suspend fun recordHealth(event: ProviderHealthEvent)
    suspend fun recentHealth(limitPerProvider: Int = 20): Map<String, List<ProviderHealthEvent>>
    suspend fun deleteAll()
}

interface CredentialRepository {
    fun observeForProvider(providerId: String): Flow<List<CredentialInfo>>
    fun observeAll(): Flow<List<CredentialInfo>>
    /** Encrypts and stores [secret]. Returns metadata only. */
    suspend fun add(providerId: String, label: String, secret: String): CredentialInfo
    suspend fun replaceSecret(credentialId: String, secret: String)
    suspend fun rename(credentialId: String, label: String)
    suspend fun delete(credentialId: String)
    suspend fun setActive(credentialId: String)
    suspend fun updateStatus(credentialId: String, status: CredentialStatus)
    /** Decrypts the active key for a provider. Call only right before a request. */
    suspend fun activeSecret(providerId: String): String?
    suspend fun activeCredential(providerId: String): CredentialInfo?
    suspend fun deleteAll()
}

interface ModelRepository {
    fun observeAll(): Flow<List<AIModel>>
    fun observeForProvider(providerId: String): Flow<List<AIModel>>
    suspend fun get(key: ModelKey): AIModel?
    /** Replaces discovered models for the provider, keeping user data (favorites, pricing, categories). */
    suspend fun replaceDiscovered(providerId: String, models: List<AIModel>)
    suspend fun upsert(model: AIModel)
    suspend fun delete(key: ModelKey)
    suspend fun setFavorite(key: ModelKey, favorite: Boolean)
    suspend fun markUsed(key: ModelKey, timestamp: Long)
}

interface ConversationRepository {
    fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>>
    fun search(query: String): Flow<List<ConversationSearchHit>>
    fun observeConversation(id: String): Flow<Conversation?>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun get(id: String): Conversation?
    suspend fun messages(conversationId: String): List<ChatMessage>
    suspend fun upsert(conversation: Conversation)
    suspend fun delete(id: String)
    suspend fun deleteAll()
    suspend fun upsertMessage(message: ChatMessage)
    suspend fun deleteMessage(id: String)
    /** Deletes messages created at or after [createdAt] (used for regenerate/edit). */
    suspend fun deleteMessagesFrom(conversationId: String, createdAt: Long)
    suspend fun duplicate(id: String, newTitle: String): Conversation?
    suspend fun summary(conversationId: String): ConversationSummary?
    suspend fun saveSummary(summary: ConversationSummary)
}

data class ConversationSearchHit(val conversation: Conversation, val snippet: String?)

data class ConversationSummary(val conversationId: String, val summary: String, val coversUntilCreatedAt: Long, val messageCount: Int)

interface UsageRepository {
    fun observeSince(since: Long): Flow<List<UsageRecord>>
    suspend fun record(record: UsageRecord)
    suspend fun all(): List<UsageRecord>
    suspend fun deleteAll()
}

interface PromptRepository {
    fun observeAll(): Flow<List<PromptTemplate>>
    suspend fun get(id: String): PromptTemplate?
    suspend fun upsert(prompt: PromptTemplate)
    suspend fun delete(id: String)
    suspend fun seedDefaultsIfEmpty()
}

interface PersonaRepository {
    fun observeAll(): Flow<List<Persona>>
    suspend fun get(id: String): Persona?
    suspend fun upsert(persona: Persona)
    suspend fun delete(id: String)
    suspend fun seedDefaultsIfEmpty()
}

interface ToolRepository {
    fun observeTools(): Flow<List<ToolConfig>>
    fun observeLogs(): Flow<List<ToolExecutionLog>>
    suspend fun permission(toolName: String): ToolPermission
    suspend fun setPermission(toolName: String, permission: ToolPermission)
    suspend fun log(entry: ToolExecutionLog)
    suspend fun seedDefaultsIfEmpty(defaults: List<ToolConfig>)
    suspend fun clearLogs()
}

/** Builds a ready-to-use adapter with just-in-time credential access. */
interface AdapterProvider {
    suspend fun adapterFor(providerId: String): AIProviderAdapter?
    /** Adapter for an unsaved configuration (connection test from the editor). */
    fun adapterForDraft(config: ProviderConfig, secret: String?): AIProviderAdapter
}
