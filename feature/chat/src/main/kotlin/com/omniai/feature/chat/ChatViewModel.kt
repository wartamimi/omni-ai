package com.omniai.feature.chat

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.common.TokenEstimator
import com.omniai.core.data.ToolApprovalBridge
import com.omniai.core.data.attachments.AttachmentProcessor
import com.omniai.core.data.attachments.AttachmentResult
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.PromptRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.router.ModelRouter
import com.omniai.core.domain.router.RouteCandidate
import com.omniai.core.domain.router.RoutingRule
import com.omniai.core.domain.router.RoutingTask
import com.omniai.core.model.AIModel
import com.omniai.core.model.AppSettings
import com.omniai.core.model.Attachment
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.GenerationParameter
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.MessageStatus
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelKey
import com.omniai.core.model.Persona
import com.omniai.core.model.ProviderConfig
import com.omniai.core.provider.ProtocolParameters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUi(
    val messages: List<ChatMessage>,
    val generating: Boolean,
    val liveNote: String?,
    val omittedMessages: Int,
    val usedSummary: Boolean,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val conversations: ConversationRepository,
    private val engine: ChatEngine,
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val personas: PersonaRepository,
    prompts: PromptRepository,
    private val settingsRepo: SettingsRepository,
    private val attachmentProcessor: AttachmentProcessor,
    private val exporter: DataExporter,
    private val router: ModelRouter,
    val approvals: ToolApprovalBridge,
) : ViewModel() {
    val conversationId: String = checkNotNull(savedState.get<String>("conversationId"))

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val conversation = conversations.observeConversation(conversationId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val modelList = models.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val personaList = personas.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val promptList = prompts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Persisted messages with the in-flight generation overlaid. */
    val ui: StateFlow<ChatUi?> = combine(conversations.observeMessages(conversationId), engine.live) { stored, live ->
        val gen = live[conversationId]
        val merged = if (gen == null) stored else stored.map { m ->
            if (m.id == gen.messageId) m.copy(content = gen.text, reasoning = gen.reasoning.ifBlank { m.reasoning }, status = MessageStatus.STREAMING) else m
        }
        ChatUi(merged, gen != null, gen?.statusNote, gen?.omittedMessages ?: 0, gen?.usedSummary ?: false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentProvider: StateFlow<ProviderConfig?> = combine(conversation, providerList) { c, ps -> ps.firstOrNull { it.id == c?.providerId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val currentModel: StateFlow<AIModel?> = combine(conversation, modelList) { c, ms -> ms.firstOrNull { it.providerId == c?.providerId && it.modelId == c.modelId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Parameters the selected provider's protocol accepts; others are hidden in the UI. */
    val supportedParams: StateFlow<Set<GenerationParameter>> = currentProvider.map { p ->
        if (p == null) emptySet() else ProtocolParameters.supported(p.protocol, p.apiVersion)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _pending = MutableStateFlow<List<Attachment>>(emptyList())
    val pendingAttachments: StateFlow<List<Attachment>> = _pending.asStateFlow()
    val importing = MutableStateFlow(false)

    val draft = MutableStateFlow(savedState.get<String>("initialText").orEmpty())

    fun send() {
        val text = draft.value
        val c = conversation.value ?: return
        if (c.providerId == null || c.modelId.isNullOrBlank()) {
            _events.tryEmit("Choose a provider and model first.")
            return
        }
        val attachments = _pending.value
        if (attachments.any { it.type == com.omniai.core.model.AttachmentType.IMAGE } &&
            currentModel.value?.capabilities?.state(ModelCapability.VISION) == com.omniai.core.model.CapabilityState.UNSUPPORTED
        ) {
            _events.tryEmit("This model doesn't accept images. Pick a vision model or remove the image.")
            return
        }
        if (engine.send(conversationId, text, attachments) != null) {
            draft.value = ""
            _pending.value = emptyList()
        }
    }

    fun stop() = engine.stop(conversationId)
    fun regenerate() = engine.regenerate(conversationId)
    fun editAndResend(messageId: String, text: String) = engine.editAndResend(conversationId, messageId, text)
    fun deleteMessage(id: String) = viewModelScope.launch { conversations.deleteMessage(id) }

    fun selectModel(key: ModelKey) = viewModelScope.launch {
        val c = conversations.get(conversationId) ?: return@launch
        conversations.upsert(c.copy(providerId = key.providerId, modelId = key.modelId))
        models.markUsed(key, System.currentTimeMillis())
    }

    fun setParams(params: GenerationParams?) = viewModelScope.launch {
        val c = conversations.get(conversationId) ?: return@launch
        conversations.upsert(c.copy(params = params))
    }

    fun setSystemPrompt(prompt: String) = viewModelScope.launch {
        val c = conversations.get(conversationId) ?: return@launch
        conversations.upsert(c.copy(systemPrompt = prompt.ifBlank { null }))
    }

    fun applyPersona(persona: Persona?) = viewModelScope.launch {
        val c = conversations.get(conversationId) ?: return@launch
        val providerId = persona?.defaultProviderId?.takeIf { id -> providerList.value.any { it.id == id && it.enabled } } ?: c.providerId
        val modelId = if (persona?.defaultProviderId == providerId && persona?.defaultModelId != null) persona.defaultModelId else c.modelId
        conversations.upsert(
            c.copy(
                personaId = persona?.id,
                systemPrompt = persona?.systemPrompt ?: settings.value.defaultSystemPrompt.ifBlank { null },
                params = persona?.params,
                providerId = providerId,
                modelId = modelId,
            ),
        )
    }

    fun rename(title: String) = viewModelScope.launch {
        val c = conversations.get(conversationId) ?: return@launch
        if (title.isNotBlank()) conversations.upsert(c.copy(title = title.trim()))
    }

    fun addAttachment(uri: Uri) = viewModelScope.launch {
        importing.value = true
        try {
            when (val r = attachmentProcessor.importFile(uri)) {
                is AttachmentResult.Ok -> _pending.update { it + r.attachment }
                is AttachmentResult.Unsupported -> _events.emit(r.reason)
            }
        } finally {
            importing.value = false
        }
    }

    fun removeAttachment(a: Attachment) = viewModelScope.launch {
        _pending.update { list -> list.filterNot { it.id == a.id } }
        attachmentProcessor.discard(a)
    }

    fun estimatedDraftTokens(): Int = TokenEstimator.estimate(draft.value) + _pending.value.sumOf { TokenEstimator.estimate(it.extractedText.orEmpty()) }

    suspend fun exportMarkdown(): String? = exporter.exportConversationMarkdown(conversationId)
    suspend fun exportJson(): String? = exporter.exportConversationJson(conversationId)

    // ----- Router -----
    suspend fun suggest(task: RoutingTask): List<RouteCandidate> {
        val s = settings.value
        val rules = s.routingRules.mapNotNull { (task, keys) -> runCatching { RoutingRule(RoutingTask.valueOf(task), keys) }.getOrNull() }
        val mode = if (s.routingMode == com.omniai.core.model.RoutingMode.MANUAL) com.omniai.core.model.RoutingMode.RECOMMENDED else s.routingMode
        return router.recommend(
            task = task, mode = mode, models = modelList.value, providers = providerList.value,
            health = providers.recentHealth(), rules = rules, estimatedInputTokens = estimatedDraftTokens(),
        )
    }

    fun pinCurrentModelForTask(task: RoutingTask) = viewModelScope.launch {
        val c = conversation.value ?: return@launch
        val key = "${c.providerId}::${c.modelId}"
        if (c.providerId == null || c.modelId == null) return@launch
        settingsRepo.update { s ->
            val list = s.routingRules[task.name].orEmpty().filterNot { it == key }
            s.copy(routingRules = s.routingRules + (task.name to (list + key)))
        }
        _events.emit("Added to your rule for ${task.label}.")
    }
}
