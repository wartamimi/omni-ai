package com.omniai.core.domain.compare

import com.omniai.core.common.Clock
import com.omniai.core.common.CostCalculator
import com.omniai.core.common.Ids
import com.omniai.core.common.TokenEstimator
import com.omniai.core.domain.repository.AdapterProvider
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.MessageRole
import com.omniai.core.model.ModelKey
import com.omniai.core.model.PromptMessage
import com.omniai.core.model.ProviderError
import com.omniai.core.model.TokenUsage
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

enum class CompareStatus { WAITING, RUNNING, DONE, FAILED, STOPPED }

data class CompareResult(
    val target: ModelKey,
    val providerName: String,
    val status: CompareStatus = CompareStatus.WAITING,
    val text: String = "",
    val error: String? = null,
    val firstTokenMs: Long? = null,
    val totalMs: Long? = null,
    val usage: TokenUsage? = null,
    val estimatedCostUsd: Double? = null,
)

/**
 * Sends one prompt to several models in parallel. Each target has its own job so a failure,
 * rate limit or stop on one target never blocks the others.
 */
class CompareEngine(
    private val providers: ProviderRepository,
    private val models: ModelRepository,
    private val adapters: AdapterProvider,
    private val usage: UsageRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val _results = MutableStateFlow<List<CompareResult>>(emptyList())
    val results: StateFlow<List<CompareResult>> = _results.asStateFlow()
    private val jobs = mutableMapOf<ModelKey, Job>()
    private val adapterRefs = mutableMapOf<ModelKey, Pair<String, AIProviderAdapter>>()

    suspend fun start(prompt: String, systemPrompt: String?, targets: List<ModelKey>, params: GenerationParams, limit: Int) {
        stopAll()
        val selected = targets.distinct().take(limit)
        _results.value = selected.map { key -> CompareResult(key, providers.get(key.providerId)?.name ?: key.providerId) }
        selected.forEach { key -> jobs[key] = scope.launch { runTarget(key, prompt, systemPrompt, params) } }
    }

    private suspend fun runTarget(key: ModelKey, prompt: String, systemPrompt: String?, baseParams: GenerationParams) {
        val provider = providers.get(key.providerId)
        val adapter = try {
            adapters.adapterFor(key.providerId)
        } catch (e: ProviderException) {
            return update(key) { it.copy(status = CompareStatus.FAILED, error = "${e.error.title}: ${e.error.message}") }
        }
        if (provider == null || adapter == null) return update(key) { it.copy(status = CompareStatus.FAILED, error = "Provider not found") }
        val params = baseParams.restrictedTo(adapter.capabilities.supportedParameters)
        val streaming = params.stream && adapter.capabilities.streaming && provider.streamingEnabled
        val requestId = Ids.new()
        adapterRefs[key] = requestId to adapter
        val request = AIRequest(requestId, key.modelId, systemPrompt, listOf(PromptMessage(MessageRole.USER, prompt)), params.copy(stream = streaming))
        val begin = System.nanoTime()
        fun elapsed() = (System.nanoTime() - begin) / 1_000_000
        update(key) { it.copy(status = CompareStatus.RUNNING) }
        val text = StringBuilder()
        var reported: TokenUsage? = null
        var error: ProviderError? = null
        var stopped = false
        try {
            if (streaming) {
                adapter.stream(request).collect { e ->
                    when (e) {
                        is AIStreamEvent.TextDelta -> {
                            text.append(e.text)
                            update(key) { r -> r.copy(text = text.toString(), firstTokenMs = r.firstTokenMs ?: elapsed()) }
                        }
                        is AIStreamEvent.Usage -> reported = e.usage
                        is AIStreamEvent.Failed -> error = e.error
                        else -> Unit
                    }
                }
            } else {
                val r = adapter.generate(request)
                text.append(r.text)
                reported = r.usage
            }
        } catch (e: CancellationException) {
            stopped = true
        } catch (e: ProviderException) {
            error = e.error
        }
        val total = elapsed()
        val tokens = reported?.takeIf { it.inputTokens != null || it.outputTokens != null }
            ?: TokenUsage(TokenEstimator.estimate(prompt + systemPrompt.orEmpty()), TokenEstimator.estimate(text.toString()), false)
        val model = models.get(key)
        val cost = model?.let { CostCalculator.estimateUsd(it.pricing, tokens.inputTokens ?: 0, tokens.outputTokens ?: 0) }
        withContext(NonCancellable) {
            update(key) {
                it.copy(
                    status = when {
                        stopped || error is ProviderError.Cancelled -> CompareStatus.STOPPED
                        error != null -> CompareStatus.FAILED
                        else -> CompareStatus.DONE
                    },
                    text = text.toString(), error = error?.let { e -> "${e.title}: ${e.message}" },
                    totalMs = total, usage = tokens, estimatedCostUsd = cost,
                )
            }
            usage.record(
                UsageRecord(
                    Ids.new(), provider.id, provider.name, key.modelId, null, tokens.inputTokens ?: 0, tokens.outputTokens ?: 0,
                    tokens.providerReported, cost, error == null && !stopped, error?.httpStatus, error?.let { it::class.simpleName }, total, clock.now(),
                ),
            )
        }
    }

    fun stop(key: ModelKey) {
        adapterRefs[key]?.let { (id, adapter) -> scope.launch { adapter.cancelRequest(id) } }
        jobs[key]?.cancel()
    }

    fun stopAll() {
        jobs.keys.toList().forEach { stop(it) }
        jobs.clear()
    }

    fun clear() {
        stopAll()
        _results.value = emptyList()
    }

    private fun update(key: ModelKey, transform: (CompareResult) -> CompareResult) {
        _results.update { list -> list.map { if (it.target == key) transform(it) else it } }
    }
}
