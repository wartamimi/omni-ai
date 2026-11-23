package com.omniai.core.data.export

import com.omniai.core.common.Clock
import com.omniai.core.common.CostCalculator
import com.omniai.core.common.Ids
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.PromptRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.AIModel
import com.omniai.core.model.AppSettings
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.Conversation
import com.omniai.core.model.MessageRole
import com.omniai.core.model.Persona
import com.omniai.core.model.PromptTemplate
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.UsageRecord
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ConversationExport(val conversation: Conversation, val messages: List<ChatMessage>)

/** Full backup. API keys are never included: they stay encrypted on this device only. */
@Serializable
data class OmniExport(
    val format: String = "omniai-export",
    val version: Int = 1,
    val exportedAt: Long,
    val credentialsIncluded: Boolean = false,
    val settings: AppSettings? = null,
    val providers: List<ProviderConfig> = emptyList(),
    val models: List<AIModel> = emptyList(),
    val conversations: List<ConversationExport> = emptyList(),
    val prompts: List<PromptTemplate> = emptyList(),
    val personas: List<Persona> = emptyList(),
)

data class ImportSummary(val providers: Int, val conversations: Int, val prompts: Int, val personas: Int)

@Singleton
class DataExporter @Inject constructor(
    private val settings: SettingsRepository,
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val conversations: ConversationRepository,
    private val prompts: PromptRepository,
    private val personas: PersonaRepository,
    private val usage: UsageRepository,
) {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportAll(includeConversations: Boolean = true): String {
        val convs = if (includeConversations) {
            conversations.observeConversations(includeArchived = true).first().map { c ->
                ConversationExport(c, conversations.messages(c.id).map { it.copy(attachments = it.attachments.map { a -> a.copy(localPath = "") }) })
            }
        } else emptyList()
        val export = OmniExport(
            exportedAt = Clock.System.now(),
            settings = settings.current(),
            providers = providers.all().map { it.copy(customHeaders = it.customHeaders.map { h -> if (isSecretHeader(h.name)) h.copy(value = "") else h }) },
            models = models.observeAll().first(),
            conversations = convs,
            prompts = prompts.observeAll().first(),
            personas = personas.observeAll().first(),
        )
        return json.encodeToString(OmniExport.serializer(), export)
    }

    suspend fun exportConfiguration(): String {
        val export = OmniExport(
            exportedAt = Clock.System.now(),
            providers = providers.all().map { it.copy(customHeaders = it.customHeaders.map { h -> if (isSecretHeader(h.name)) h.copy(value = "") else h }) },
            models = models.observeAll().first(),
        )
        return json.encodeToString(OmniExport.serializer(), export)
    }

    suspend fun importAll(raw: String): ImportSummary {
        val data = json.decodeFromString(OmniExport.serializer(), raw)
        require(data.format == "omniai-export") { "Not an OmniAI export file." }
        data.providers.forEach { providers.upsert(it.copy(isDefault = false)) }
        data.models.filter { m -> data.providers.any { it.id == m.providerId } || providers.get(m.providerId) != null }.forEach { models.upsert(it) }
        data.conversations.forEach { importConversation(it) }
        data.prompts.forEach { prompts.upsert(it) }
        data.personas.forEach { personas.upsert(it) }
        return ImportSummary(data.providers.size, data.conversations.size, data.prompts.size, data.personas.size)
    }

    suspend fun exportConversationJson(conversationId: String): String? {
        val c = conversations.get(conversationId) ?: return null
        return json.encodeToString(ConversationExport.serializer(), ConversationExport(c, conversations.messages(conversationId).map { it.copy(attachments = emptyList()) }))
    }

    suspend fun exportConversationMarkdown(conversationId: String): String? {
        val c = conversations.get(conversationId) ?: return null
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return buildString {
            appendLine("# ${c.title}")
            appendLine()
            appendLine("_Exported from OmniAI · ${df.format(Date())}_")
            conversations.messages(conversationId).filter { it.role != MessageRole.TOOL }.forEach { m ->
                appendLine()
                val who = if (m.role == MessageRole.USER) "You" else listOfNotNull("Assistant", m.modelId?.let { "($it)" }).joinToString(" ")
                appendLine("### $who · ${df.format(Date(m.createdAt))}")
                appendLine()
                appendLine(m.content)
                m.attachments.forEach { appendLine("\n> Attachment: ${it.displayName}") }
            }
        }
    }

    /** Imports one conversation as a new copy (new ids) so it never overwrites existing chats. */
    suspend fun importConversationJson(raw: String): Conversation {
        val data = json.decodeFromString(ConversationExport.serializer(), raw)
        return importConversation(data, freshIds = true)
    }

    private suspend fun importConversation(data: ConversationExport, freshIds: Boolean = false): Conversation {
        val conv = if (freshIds) data.conversation.copy(id = Ids.new()) else data.conversation
        conversations.upsert(conv)
        data.messages.forEach { m ->
            conversations.upsertMessage(m.copy(id = if (freshIds) Ids.new() else m.id, conversationId = conv.id, attachments = emptyList()))
        }
        return conv
    }

    fun promptsToJson(list: List<PromptTemplate>) = json.encodeToString(ListSerializer(PromptTemplate.serializer()), list)
    fun promptsFromJson(raw: String) = json.decodeFromString(ListSerializer(PromptTemplate.serializer()), raw)
    fun personasToJson(list: List<Persona>) = json.encodeToString(ListSerializer(Persona.serializer()), list)
    fun personasFromJson(raw: String) = json.decodeFromString(ListSerializer(Persona.serializer()), raw)

    suspend fun usageCsv(): String {
        val s = settings.current()
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        val currency = s.currency.display
        return buildString {
            appendLine("timestamp,provider,model,input_tokens,output_tokens,token_source,estimated_cost_usd,estimated_cost_${currency.name.lowercase()},success,http_status,error,latency_ms,manual_adjustment")
            usage.all().forEach { r ->
                val local = r.estimatedCostUsd?.let { CostCalculator.convert(it, currency, s.currency) }
                appendLine(
                    listOf(
                        df.format(Date(r.timestamp)), csv(r.providerName), csv(r.modelId), r.inputTokens, r.outputTokens,
                        if (r.providerReported) "provider_reported" else "estimated", r.estimatedCostUsd ?: "", local ?: "",
                        r.success, r.httpStatus ?: "", csv(r.errorKind.orEmpty()), r.latencyMs ?: "", r.isManualAdjustment,
                    ).joinToString(","),
                )
            }
        }
    }

    suspend fun usageJson(): String = json.encodeToString(ListSerializer(UsageRecord.serializer()), usage.all())

    private fun csv(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    private fun isSecretHeader(name: String) = name.lowercase().let { it.contains("key") || it.contains("auth") || it.contains("token") || it.contains("secret") }
}
