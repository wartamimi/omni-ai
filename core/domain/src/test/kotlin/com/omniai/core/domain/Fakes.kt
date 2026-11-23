package com.omniai.core.domain

import com.omniai.core.domain.repository.AdapterProvider
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.ConversationSearchHit
import com.omniai.core.domain.repository.ConversationSummary
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.ToolRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.*
import com.omniai.core.provider.AIProviderAdapter
import com.omniai.core.provider.AdapterCapabilities
import com.omniai.core.provider.ProtocolParameters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun current() = state.value
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

class FakeConversations : ConversationRepository {
    val convs = MutableStateFlow<Map<String, Conversation>>(emptyMap())
    val msgs = MutableStateFlow<Map<String, ChatMessage>>(emptyMap())
    var summary: ConversationSummary? = null
    override fun observeConversations(includeArchived: Boolean) = convs.map { it.values.toList() }
    override fun search(query: String): Flow<List<ConversationSearchHit>> = convs.map { emptyList() }
    override fun observeConversation(id: String) = convs.map { it[id] }
    override fun observeMessages(conversationId: String) = msgs.map { m -> m.values.filter { it.conversationId == conversationId }.sortedBy { it.createdAt } }
    override suspend fun get(id: String) = convs.value[id]
    override suspend fun messages(conversationId: String) = msgs.value.values.filter { it.conversationId == conversationId }.sortedBy { it.createdAt }
    override suspend fun upsert(conversation: Conversation) { convs.value = convs.value + (conversation.id to conversation) }
    override suspend fun delete(id: String) { convs.value = convs.value - id }
    override suspend fun deleteAll() { convs.value = emptyMap(); msgs.value = emptyMap() }
    override suspend fun upsertMessage(message: ChatMessage) { msgs.value = msgs.value + (message.id to message) }
    override suspend fun deleteMessage(id: String) { msgs.value = msgs.value - id }
    override suspend fun deleteMessagesFrom(conversationId: String, createdAt: Long) {
        msgs.value = msgs.value.filterValues { !(it.conversationId == conversationId && it.createdAt >= createdAt) }
    }
    override suspend fun duplicate(id: String, newTitle: String): Conversation? = null
    override suspend fun summary(conversationId: String) = summary
    override suspend fun saveSummary(summary: ConversationSummary) { this.summary = summary }
}

class FakeProviders(vararg configs: ProviderConfig) : ProviderRepository {
    val map = MutableStateFlow(configs.associateBy { it.id })
    val health = mutableListOf<ProviderHealthEvent>()
    override fun observeProviders() = map.map { it.values.toList() }
    override fun observeHealth(providerId: String) = flow { emit(health.filter { it.providerId == providerId }) }
    override suspend fun get(id: String) = map.value[id]
    override suspend fun all() = map.value.values.toList()
    override suspend fun upsert(config: ProviderConfig) { map.value = map.value + (config.id to config) }
    override suspend fun delete(id: String) { map.value = map.value - id }
    override suspend fun setDefault(id: String) {}
    override suspend fun setEnabled(id: String, enabled: Boolean) {}
    override suspend fun duplicate(id: String): ProviderConfig? = null
    override suspend fun recordHealth(event: ProviderHealthEvent) { health += event }
    override suspend fun recentHealth(limitPerProvider: Int) = health.groupBy { it.providerId }
    override suspend fun deleteAll() {}
}

class FakeModels(vararg models: AIModel) : ModelRepository {
    val map = MutableStateFlow(models.associateBy { it.key })
    override fun observeAll() = map.map { it.values.toList() }
    override fun observeForProvider(providerId: String) = map.map { m -> m.values.filter { it.providerId == providerId } }
    override suspend fun get(key: ModelKey) = map.value[key]
    override suspend fun replaceDiscovered(providerId: String, models: List<AIModel>) {}
    override suspend fun upsert(model: AIModel) { map.value = map.value + (model.key to model) }
    override suspend fun delete(key: ModelKey) {}
    override suspend fun setFavorite(key: ModelKey, favorite: Boolean) {}
    override suspend fun markUsed(key: ModelKey, timestamp: Long) {}
}

class FakePersonas : PersonaRepository {
    override fun observeAll(): Flow<List<Persona>> = flow { emit(emptyList()) }
    override suspend fun get(id: String): Persona? = null
    override suspend fun upsert(persona: Persona) {}
    override suspend fun delete(id: String) {}
    override suspend fun seedDefaultsIfEmpty() {}
}

class FakeUsage : UsageRepository {
    val records = mutableListOf<UsageRecord>()
    override fun observeSince(since: Long): Flow<List<UsageRecord>> = flow { emit(records.toList()) }
    override suspend fun record(record: UsageRecord) { records += record }
    override suspend fun all() = records.toList()
    override suspend fun deleteAll() { records.clear() }
}

class FakeTools(private val permission: ToolPermission = ToolPermission.ALLOW) : ToolRepository {
    val logs = mutableListOf<ToolExecutionLog>()
    override fun observeTools(): Flow<List<ToolConfig>> = flow { emit(emptyList()) }
    override fun observeLogs(): Flow<List<ToolExecutionLog>> = flow { emit(logs.toList()) }
    override suspend fun permission(toolName: String) = permission
    override suspend fun setPermission(toolName: String, permission: ToolPermission) {}
    override suspend fun log(entry: ToolExecutionLog) { logs += entry }
    override suspend fun seedDefaultsIfEmpty(defaults: List<ToolConfig>) {}
    override suspend fun clearLogs() {}
}

/** Scripted adapter: each call to stream/generate consumes the next script entry. */
class ScriptedAdapter(
    private val scripts: ArrayDeque<List<AIStreamEvent>>,
    override val capabilities: AdapterCapabilities = AdapterCapabilities(true, false, true, true, ProtocolParameters.supported(ApiProtocol.OPENAI_CHAT_COMPLETIONS)),
) : AIProviderAdapter {
    val requests = mutableListOf<AIRequest>()
    override suspend fun validateConnection() = ConnectionResult(true, 200, 1, "ok")
    override suspend fun fetchModels(): List<AIModel> = emptyList()
    override suspend fun generate(request: AIRequest): AIResponse {
        requests += request
        val events = scripts.removeFirst()
        return AIResponse(events.filterIsInstance<AIStreamEvent.TextDelta>().joinToString("") { it.text }, null, null, "stop", emptyList(), 200)
    }
    override fun stream(request: AIRequest): Flow<AIStreamEvent> = flow {
        requests += request
        scripts.removeFirst().forEach { emit(it) }
    }
    override suspend fun cancelRequest(requestId: String) {}
}

class FakeAdapterProvider(private val adapter: AIProviderAdapter) : AdapterProvider {
    override suspend fun adapterFor(providerId: String) = adapter
    override fun adapterForDraft(config: ProviderConfig, secret: String?) = adapter
}

fun provider(id: String = "p1", enabled: Boolean = true) = ProviderConfig(
    id = id, definitionId = "openai", name = "Prov $id", category = ProviderCategory.OFFICIAL, protocol = ApiProtocol.OPENAI_CHAT_COMPLETIONS,
    baseUrl = "https://example.com/v1", auth = AuthConfig.Bearer, enabled = enabled, defaultModelId = "m1", createdAt = 0, updatedAt = 0,
)
