package com.omniai.core.domain.chat

import com.omniai.core.common.Clock
import com.omniai.core.common.CostCalculator
import com.omniai.core.common.Ids
import com.omniai.core.common.TokenEstimator
import com.omniai.core.domain.repository.AdapterProvider
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.ConversationSummary
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.domain.tools.ToolApprover
import com.omniai.core.domain.tools.ToolExecutor
import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.Attachment
import com.omniai.core.model.AttachmentType
import com.omniai.core.model.CapabilityState
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.Conversation
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.ImagePart
import com.omniai.core.model.MessageRole
import com.omniai.core.model.MessageStatus
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelKey
import com.omniai.core.model.PromptMessage
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.model.ProviderHealthEvent
import com.omniai.core.model.TokenUsage
import com.omniai.core.model.ToolCall
import com.omniai.core.model.UsageRecord
import com.omniai.core.provider.AIProviderAdapter
import com.omniai.core.provider.ProviderException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads attachment bytes for the request (implemented by the data layer). */
fun interface AttachmentImageLoader {
    suspend fun load(attachment: Attachment): ImagePart?
}

/** In-memory state of a running generation; the UI overlays it on persisted messages. */
data class LiveGeneration(
    val conversationId: String,
    val messageId: String,
    val text: String,
    val reasoning: String,
    val startedAt: Long,
    val omittedMessages: Int,
    val usedSummary: Boolean,
    val streaming: Boolean,
    val statusNote: String? = null,
)

/**
 * The provider-independent chat engine: builds context, picks parameters the provider supports,
 * streams (or not, if unsupported), persists results, records usage and handles tool calls.
 * Generations run in [scope] so they survive screen changes; each can be cancelled.
 */
class ChatEngine(
    private val conversations: ConversationRepository,
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val personas: PersonaRepository,
    private val settings: SettingsRepository,
    private val adapters: AdapterProvider,
    private val usage: UsageRepository,
    private val toolExecutor: ToolExecutor,
    private val imageLoader: AttachmentImageLoader,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    private val contextBuilder: ContextBuilder = ContextBuilder(),
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val lastTimestamp = java.util.concurrent.atomic.AtomicLong(0)

    /** Strictly increasing timestamps so message order is stable even within one millisecond. */
    private fun nextTimestamp(): Long = lastTimestamp.updateAndGet { prev -> maxOf(prev + 1, clock.now()) }
    private val requestIds = ConcurrentHashMap<String, Pair<String, AIProviderAdapter>>()
    private val _live = MutableStateFlow<Map<String, LiveGeneration>>(emptyMap())
    val live: StateFlow<Map<String, LiveGeneration>> = _live.asStateFlow()

    @Volatile
    var toolApprover: ToolApprover = ToolApprover { _, _ -> false }

    fun isGenerating(conversationId: String) = jobs[conversationId]?.isActive == true

    /** Creates a conversation using defaults (or the given persona/provider/model). */
    suspend fun createConversation(
        providerId: String? = null,
        modelId: String? = null,
        personaId: String? = null,
        title: String = "New chat",
    ): Conversation {
        val s = settings.current()
        val persona = (personaId ?: s.defaultPersonaId)?.let { personas.get(it) }
        val provider = providerId ?: persona?.defaultProviderId ?: s.defaultProviderId ?: providers.all().firstOrNull { it.isDefault && it.enabled }?.id
            ?: providers.all().firstOrNull { it.enabled }?.id
        val providerConfig = provider?.let { providers.get(it) }
        val model = modelId ?: persona?.defaultModelId?.takeIf { persona.defaultProviderId == provider } ?: providerConfig?.defaultModelId
            ?: s.defaultModelId?.takeIf { s.defaultProviderId == provider }
        val now = clock.now()
        val conversation = Conversation(
            id = Ids.new(), title = title, providerId = provider, modelId = model, personaId = persona?.id,
            systemPrompt = persona?.systemPrompt ?: s.defaultSystemPrompt.ifBlank { null },
            params = persona?.params, isPinned = false, isArchived = false, createdAt = now, updatedAt = now,
        )
        conversations.upsert(conversation)
        return conversation
    }

    fun send(conversationId: String, text: String, attachments: List<Attachment> = emptyList()): Job? {
        if (text.isBlank() && attachments.isEmpty()) return null
        if (isGenerating(conversationId)) return null
        return launch(conversationId) {
            val conversation = conversations.get(conversationId) ?: return@launch
            val now = nextTimestamp()
            conversations.upsertMessage(
                ChatMessage(id = Ids.new(), conversationId = conversationId, role = MessageRole.USER, content = text.trim(), attachments = attachments, createdAt = now),
            )
            val titled = if (conversation.title == "New chat") conversation.copy(title = autoTitle(text, attachments)) else conversation
            conversations.upsert(titled.copy(updatedAt = now))
            generate(titled)
        }
    }

    /** Removes the last assistant answer(s) after the last user message and asks again. */
    fun regenerate(conversationId: String): Job? {
        if (isGenerating(conversationId)) return null
        return launch(conversationId) {
            val conversation = conversations.get(conversationId) ?: return@launch
            val history = conversations.messages(conversationId)
            val lastUser = history.lastOrNull { it.role == MessageRole.USER } ?: return@launch
            history.dropWhile { it.id != lastUser.id }.drop(1).forEach { conversations.deleteMessage(it.id) }
            generate(conversation)
        }
    }

    /** Replaces a user message and everything after it, then regenerates. */
    fun editAndResend(conversationId: String, messageId: String, newText: String): Job? {
        if (isGenerating(conversationId) || newText.isBlank()) return null
        return launch(conversationId) {
            val conversation = conversations.get(conversationId) ?: return@launch
            val original = conversations.messages(conversationId).firstOrNull { it.id == messageId && it.role == MessageRole.USER } ?: return@launch
            conversations.deleteMessagesFrom(conversationId, original.createdAt)
            conversations.upsertMessage(original.copy(id = Ids.new(), content = newText.trim(), createdAt = nextTimestamp()))
            generate(conversation)
        }
    }

    fun stop(conversationId: String) {
        requestIds[conversationId]?.let { (requestId, adapter) -> scope.launch { adapter.cancelRequest(requestId) } }
        jobs[conversationId]?.cancel(CancellationException("Stopped by user"))
    }

    private fun launch(conversationId: String, block: suspend CoroutineScope.() -> Unit): Job {
        val job = scope.launch(block = block)
        jobs[conversationId] = job
        job.invokeOnCompletion {
            jobs.remove(conversationId, job)
            requestIds.remove(conversationId)
            _live.update { it - conversationId }
        }
        return job
    }

    private suspend fun generate(conversation: Conversation) {
        val s = settings.current()
        val assistantId = Ids.new()
        val startedAt = nextTimestamp()
        val providerConfig = conversation.providerId?.let { providers.get(it) }
        val modelId = conversation.modelId

        suspend fun fail(error: ProviderError, partial: String = "") {
            conversations.upsertMessage(
                ChatMessage(
                    id = assistantId, conversationId = conversation.id, role = MessageRole.ASSISTANT, content = partial,
                    status = MessageStatus.ERROR, providerId = providerConfig?.id, modelId = modelId,
                    errorMessage = "${error.title}: ${error.message}", createdAt = startedAt,
                ),
            )
        }

        if (providerConfig == null) return fail(ProviderError.NotFound("Choose a provider for this chat."))
        if (!providerConfig.enabled) return fail(ProviderError.Forbidden("${providerConfig.name} is disabled. Enable it in Providers."))
        if (modelId.isNullOrBlank()) return fail(ProviderError.NotFound("Choose a model for this chat."))
        val adapter = try {
            adapters.adapterFor(providerConfig.id)
        } catch (e: ProviderException) {
            return fail(e.error)
        } ?: return fail(ProviderError.NotFound("Provider configuration is invalid."))

        val model = models.get(ModelKey(providerConfig.id, modelId))
        val persona = conversation.personaId?.let { personas.get(it) }
        val requested = GenerationParams(stream = s.streaming)
            .overriddenBy(persona?.params)
            .overriddenBy(conversation.params)
        val params = requested.restrictedTo(adapter.capabilities.supportedParameters)
        val streaming = params.stream && providerConfig.streamingEnabled && adapter.capabilities.streaming &&
            model?.capabilities?.state(ModelCapability.STREAMING) != CapabilityState.UNSUPPORTED

        val history = conversations.messages(conversation.id)
        val hasImages = history.any { m -> m.attachments.any { it.type == AttachmentType.IMAGE } }
        if (hasImages && (!adapter.capabilities.imageInput || model?.capabilities?.state(ModelCapability.VISION) == CapabilityState.UNSUPPORTED)) {
            return fail(ProviderError.BadRequest("This model does not accept image input. Remove the image or switch to a vision model."))
        }
        val images = loadImages(history)
        var context = contextBuilder.build(
            history, conversation.systemPrompt, s.contextStrategy, s.maxContextMessages, model?.contextWindow,
            params.maxOutputTokens ?: model?.maxOutputTokens?.coerceAtMost(8_192) ?: 4_096,
            conversations.summary(conversation.id),
        ) { images[it.id].orEmpty() }

        _live.update {
            it + (conversation.id to LiveGeneration(conversation.id, assistantId, "", "", startedAt, context.omittedMessages(), context.usedSummary, streaming))
        }

        if (context.needsSummaryOf.isNotEmpty()) {
            setNote(conversation.id, "Summarizing earlier messages…")
            val summary = summarize(conversation.id, context.needsSummaryOf, adapter, providerConfig, modelId, model)
            if (summary != null) {
                conversations.saveSummary(summary)
                context = contextBuilder.build(
                    history, conversation.systemPrompt, s.contextStrategy, s.maxContextMessages, model?.contextWindow,
                    params.maxOutputTokens ?: 4_096, summary,
                ) { images[it.id].orEmpty() }
                _live.update { m -> m[conversation.id]?.let { m + (conversation.id to it.copy(usedSummary = true, omittedMessages = context.omittedCount, statusNote = null)) } ?: m }
            } else {
                setNote(conversation.id, null)
            }
        }

        val tools = if (s.toolsEnabled && adapter.capabilities.toolCalling &&
            model?.capabilities?.state(ModelCapability.TOOL_CALLING) != CapabilityState.UNSUPPORTED
        ) toolExecutor.enabledTools() else emptyList()

        val requestId = Ids.new()
        requestIds[conversation.id] = requestId to adapter
        val request = AIRequest(requestId, modelId, context.systemPrompt, context.messages, params.copy(stream = streaming), tools.map { it.spec() })
        val outcome = runOnce(conversation, assistantId, startedAt, adapter, request, streaming, providerConfig, model)
        if (outcome.toolCalls.isEmpty() || s.maxToolRounds <= 0) return
        continueAfterTools(conversation, adapter, providerConfig, model, modelId, context.systemPrompt, context.messages, outcome, params, streaming, tools, 1, s.maxToolRounds)
    }

    /** Executes requested tools, persists their results and asks the model again (bounded by maxRounds). */
    private suspend fun continueAfterTools(
        conversation: Conversation, adapter: AIProviderAdapter, providerConfig: ProviderConfig, model: AIModel?, modelId: String,
        systemPrompt: String?, previous: List<PromptMessage>, outcome: Outcome, params: GenerationParams, streaming: Boolean,
        tools: List<com.omniai.core.domain.tools.LocalTool>, round: Int, maxRounds: Int,
    ) {
        val results = outcome.toolCalls.map { toolExecutor.execute(it, conversation.id, toolApprover) }
        results.forEach { r ->
            val t = nextTimestamp()
            conversations.upsertMessage(
                ChatMessage(Ids.new(), conversation.id, MessageRole.TOOL, r.output, modelId = r.toolName, toolCallId = r.callId, createdAt = t),
            )
        }
        val messages = previous + PromptMessage(MessageRole.ASSISTANT, outcome.text, toolCalls = outcome.toolCalls) +
            results.map { PromptMessage(MessageRole.TOOL, it.output, toolCallId = it.callId, toolName = it.toolName) }
        val assistantId = Ids.new()
        val startedAt = nextTimestamp()
        _live.update { it + (conversation.id to LiveGeneration(conversation.id, assistantId, "", "", startedAt, 0, false, streaming)) }
        val requestId = Ids.new()
        requestIds[conversation.id] = requestId to adapter
        val offerTools = if (round < maxRounds) tools.map { it.spec() } else emptyList()
        val request = AIRequest(requestId, modelId, systemPrompt, messages, params.copy(stream = streaming), offerTools)
        val next = runOnce(conversation, assistantId, startedAt, adapter, request, streaming, providerConfig, model)
        if (next.toolCalls.isNotEmpty() && round < maxRounds) {
            continueAfterTools(conversation, adapter, providerConfig, model, modelId, systemPrompt, messages, next, params, streaming, tools, round + 1, maxRounds)
        }
    }

    private data class Outcome(val text: String, val toolCalls: List<ToolCall>)

    /** One request/response cycle. Persists the assistant message in every outcome (complete, error, cancelled). */
    private suspend fun runOnce(
        conversation: Conversation,
        assistantId: String,
        startedAt: Long,
        adapter: AIProviderAdapter,
        request: AIRequest,
        streaming: Boolean,
        providerConfig: ProviderConfig,
        model: AIModel?,
    ): Outcome {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var reportedUsage: TokenUsage? = null
        var error: ProviderError? = null
        var cancelled = false
        var finish: String? = null
        val toolAcc = sortedMapOf<Int, Triple<String?, String?, StringBuilder>>()
        var toolCalls: List<ToolCall> = emptyList()
        val begin = System.nanoTime()
        var lastPersist = 0L

        fun base(status: MessageStatus) = ChatMessage(
            id = assistantId, conversationId = conversation.id, role = MessageRole.ASSISTANT, content = text.toString(),
            reasoning = reasoning.toString().ifEmpty { null }, status = status, providerId = providerConfig.id, modelId = request.modelId,
            createdAt = startedAt,
        )

        try {
            conversations.upsertMessage(base(MessageStatus.STREAMING))
            if (streaming) {
                adapter.stream(request).collect { event ->
                    when (event) {
                        is AIStreamEvent.TextDelta -> text.append(event.text)
                        is AIStreamEvent.ReasoningDelta -> reasoning.append(event.text)
                        is AIStreamEvent.Usage -> reportedUsage = event.usage
                        is AIStreamEvent.ToolCallDelta -> {
                            val cur = toolAcc[event.index]
                            toolAcc[event.index] = Triple(event.id ?: cur?.first, event.name ?: cur?.second, (cur?.third ?: StringBuilder()).append(event.argumentsFragment))
                        }
                        is AIStreamEvent.Completed -> finish = event.finishReason
                        is AIStreamEvent.Failed -> error = event.error
                        AIStreamEvent.Started -> Unit
                    }
                    if (event is AIStreamEvent.TextDelta || event is AIStreamEvent.ReasoningDelta) {
                        _live.update { m -> m[conversation.id]?.let { m + (conversation.id to it.copy(text = text.toString(), reasoning = reasoning.toString(), messageId = assistantId)) } ?: m }
                        val now = clock.now()
                        if (now - lastPersist > PERSIST_INTERVAL_MS) {
                            lastPersist = now
                            conversations.upsertMessage(base(MessageStatus.STREAMING))
                        }
                    }
                }
                toolCalls = toolAcc.entries.mapNotNull { (i, t) ->
                    val name = t.second ?: return@mapNotNull null
                    ToolCall(t.first ?: "call_$i", name, t.third.toString().ifBlank { "{}" })
                }
            } else {
                val response = adapter.generate(request)
                text.append(response.text)
                response.reasoning?.let { reasoning.append(it) }
                reportedUsage = response.usage
                finish = response.finishReason
                toolCalls = response.toolCalls
            }
        } catch (e: CancellationException) {
            cancelled = true
        } catch (e: ProviderException) {
            error = e.error
        }

        val latency = (System.nanoTime() - begin) / 1_000_000
        val usageValue = reportedUsage?.takeIf { it.inputTokens != null || it.outputTokens != null }
            ?: TokenUsage(
                inputTokens = TokenEstimator.estimate(request.systemPrompt.orEmpty()) + TokenEstimator.estimateMessages(request.messages.map { it.text }),
                outputTokens = TokenEstimator.estimate(text.toString()),
                providerReported = false,
            )
        val status = when {
            error is ProviderError.Cancelled || cancelled -> MessageStatus.CANCELLED
            error != null -> MessageStatus.ERROR
            else -> MessageStatus.COMPLETE
        }
        withContext(NonCancellable) {
            conversations.upsertMessage(
                base(status).copy(
                    usage = usageValue,
                    latencyMs = latency,
                    errorMessage = error?.takeIf { status == MessageStatus.ERROR }?.let { "${it.title}: ${it.message}" },
                    toolCallsJson = ToolCallCodec.encode(toolCalls),
                ),
            )
            conversations.get(conversation.id)?.let { conversations.upsert(it.copy(updatedAt = clock.now())) }
            val cost = model?.let { CostCalculator.estimateUsd(it.pricing, usageValue.inputTokens ?: 0, usageValue.outputTokens ?: 0) }
            usage.record(
                UsageRecord(
                    id = Ids.new(), providerId = providerConfig.id, providerName = providerConfig.name, modelId = request.modelId,
                    conversationId = conversation.id, inputTokens = usageValue.inputTokens ?: 0, outputTokens = usageValue.outputTokens ?: 0,
                    providerReported = usageValue.providerReported, estimatedCostUsd = cost, success = status == MessageStatus.COMPLETE,
                    httpStatus = error?.httpStatus, errorKind = error?.let { it::class.simpleName }, latencyMs = latency, timestamp = clock.now(),
                ),
            )
            if (!cancelled && error !is ProviderError.Cancelled) {
                providers.recordHealth(
                    ProviderHealthEvent(providerConfig.id, clock.now(), error == null, error?.httpStatus, latency, error?.let { it::class.simpleName }),
                )
            }
            models.markUsed(ModelKey(providerConfig.id, request.modelId), clock.now())
        }
        if (cancelled) throw CancellationException("Stopped")
        @Suppress("UNUSED_VARIABLE") val unusedFinish = finish
        return Outcome(text.toString(), if (status == MessageStatus.COMPLETE) toolCalls else emptyList())
    }

    private suspend fun summarize(
        conversationId: String,
        older: List<ChatMessage>,
        adapter: AIProviderAdapter,
        providerConfig: ProviderConfig,
        modelId: String,
        model: AIModel?,
    ): ConversationSummary? {
        val transcript = older.joinToString("\n") { "${it.role.name.lowercase()}: ${it.content.take(4_000)}" }
        val request = AIRequest(
            requestId = Ids.new(), modelId = modelId,
            systemPrompt = "You summarize conversations. Keep names, numbers, decisions and open questions. Be concise.",
            messages = listOf(PromptMessage(MessageRole.USER, "Summarize this earlier part of our conversation:\n\n$transcript")),
            params = GenerationParams(maxOutputTokens = 800, stream = false).restrictedTo(adapter.capabilities.supportedParameters),
        )
        return try {
            val response = adapter.generate(request)
            val u = response.usage
            usage.record(
                UsageRecord(
                    Ids.new(), providerConfig.id, providerConfig.name, modelId, conversationId, u?.inputTokens ?: 0, u?.outputTokens ?: 0,
                    u?.providerReported ?: false, model?.let { CostCalculator.estimateUsd(it.pricing, u?.inputTokens ?: 0, u?.outputTokens ?: 0) },
                    true, response.httpStatus, null, null, clock.now(),
                ),
            )
            ConversationSummary(conversationId, response.text.trim(), older.last().createdAt, older.size)
        } catch (e: ProviderException) {
            null
        }
    }

    private suspend fun loadImages(history: List<ChatMessage>): Map<String, List<ImagePart>> {
        val recent = history.takeLast(10).filter { m -> m.role == MessageRole.USER && m.attachments.any { it.type == AttachmentType.IMAGE } }
        return recent.associate { m -> m.id to m.attachments.filter { it.type == AttachmentType.IMAGE }.mapNotNull { imageLoader.load(it) } }
    }

    private fun setNote(conversationId: String, note: String?) {
        _live.update { m -> m[conversationId]?.let { m + (conversationId to it.copy(statusNote = note)) } ?: m }
    }

    private fun BuiltContext.omittedMessages() = omittedCount

    companion object {
        private const val PERSIST_INTERVAL_MS = 1_500L

        fun autoTitle(text: String, attachments: List<Attachment>): String {
            val base = text.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: attachments.firstOrNull()?.displayName ?: "New chat"
            return if (base.length > 48) base.take(45).trimEnd() + "…" else base
        }
    }
}
