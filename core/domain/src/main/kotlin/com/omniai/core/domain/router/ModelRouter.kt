package com.omniai.core.domain.router

import com.omniai.core.model.AIModel
import com.omniai.core.model.CapabilityState
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelCategory
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderHealthEvent
import com.omniai.core.model.RoutingMode
import kotlinx.serialization.Serializable

@Serializable
enum class RoutingTask(val label: String, val category: ModelCategory?, val required: ModelCapability? = null) {
    GENERAL("General conversation", ModelCategory.GENERAL_CHAT),
    CODING("Coding", ModelCategory.CODING),
    TRANSLATION("Translation", ModelCategory.TRANSLATION),
    SUMMARIZATION("Summarization", ModelCategory.WRITING),
    REASONING("Complex reasoning", ModelCategory.REASONING),
    CREATIVE("Creative writing", ModelCategory.WRITING),
    RESEARCH("Research", ModelCategory.RESEARCH),
    IMAGE_UNDERSTANDING("Image understanding", ModelCategory.VISION, ModelCapability.VISION),
    DATA_ANALYSIS("Data analysis", ModelCategory.MATHEMATICS),
}

/** A user-defined rule: for this task, prefer these models in this order. */
@Serializable
data class RoutingRule(val task: RoutingTask, val preferred: List<String>) // "providerId::modelId"

data class RouteCandidate(val model: AIModel, val providerName: String, val score: Double, val reasons: List<String>)

/**
 * Local-only routing: uses stored metadata, user tags, favorites, pricing and the provider health
 * history recorded on this device. It never sends the prompt anywhere to decide.
 * Scores are heuristics to rank options; they are not quality claims.
 */
class ModelRouter {

    fun recommend(
        task: RoutingTask,
        mode: RoutingMode,
        models: List<AIModel>,
        providers: List<ProviderConfig>,
        health: Map<String, List<ProviderHealthEvent>>,
        rules: List<RoutingRule> = emptyList(),
        estimatedInputTokens: Int = 0,
        limit: Int = 5,
    ): List<RouteCandidate> {
        val enabled = providers.filter { it.enabled }.associateBy { it.id }
        val pool = models.filter { it.providerId in enabled }
            .filter { task.required == null || it.capabilities.state(task.required) != CapabilityState.UNSUPPORTED }
            .filter { m -> val cw = m.contextWindow; cw == null || estimatedInputTokens == 0 || cw >= estimatedInputTokens }
        if (mode == RoutingMode.MANUAL) return emptyList()

        if (mode == RoutingMode.USER_DEFINED) {
            val rule = rules.firstOrNull { it.task == task } ?: return emptyList()
            return rule.preferred.mapIndexedNotNull { index, key ->
                val m = pool.firstOrNull { "${it.providerId}::${it.modelId}" == key } ?: return@mapIndexedNotNull null
                RouteCandidate(m, enabled.getValue(m.providerId).name, (100 - index).toDouble(), listOf("Your rule for ${task.label}, choice #${index + 1}"))
            }.take(limit)
        }

        return pool.map { m -> score(m, task, mode, health[m.providerId].orEmpty(), enabled.getValue(m.providerId)) }
            .sortedByDescending { it.score }
            .take(limit)
    }

    private fun score(m: AIModel, task: RoutingTask, mode: RoutingMode, events: List<ProviderHealthEvent>, provider: ProviderConfig): RouteCandidate {
        val reasons = mutableListOf<String>()
        var score = 0.0
        if (task.category != null && task.category in m.userCategories) {
            score += 40; reasons += "You tagged it for ${task.category.label}"
        }
        if (task.required != null && m.capabilities.state(task.required) == CapabilityState.SUPPORTED) {
            score += 30; reasons += "Supports ${task.required.label.lowercase()}"
        } else if (task.required != null) {
            reasons += "${task.required.label} support is unknown"
        }
        if (task == RoutingTask.REASONING && m.capabilities.supports(ModelCapability.REASONING)) { score += 15; reasons += "Reports reasoning support" }
        if (m.isFavorite) { score += 10; reasons += "In your favorites" }
        if (provider.isDefault) score += 5

        val recent = events.takeLast(20)
        if (recent.isNotEmpty()) {
            val failRate = recent.count { !it.success } / recent.size.toDouble()
            score -= failRate * 40
            if (failRate > 0.3) reasons += "Recent failures: ${(failRate * 100).toInt()}%"
        }
        val latencies = recent.filter { it.success }.mapNotNull { it.latencyMs }
        val avgLatency = latencies.takeIf { it.isNotEmpty() }?.average()

        when (mode) {
            RoutingMode.COST_OPTIMIZED -> {
                val blended = blendedPrice(m)
                if (blended != null) {
                    score += 50.0 / (1.0 + blended); reasons += "Price known: about $${"%.2f".format(blended)} per 1M tokens (blended)"
                } else {
                    score -= 25; reasons += "Pricing unknown"
                }
            }
            RoutingMode.PERFORMANCE -> {
                if (avgLatency != null) {
                    score += 50.0 * (1000.0 / (1000.0 + avgLatency)); reasons += "Avg latency on this device: ${avgLatency.toLong()} ms"
                } else {
                    reasons += "No latency history yet"
                }
                m.contextWindow?.let { score += kotlin.math.ln(it.toDouble()) }
            }
            else -> {
                m.lastUsedAt?.let { score += 5; reasons += "Used recently" }
                avgLatency?.let { score += 10.0 * (1000.0 / (1000.0 + it)) }
            }
        }
        return RouteCandidate(m, provider.name, score, reasons)
    }

    private fun blendedPrice(m: AIModel): Double? {
        val i = m.pricing.inputPerMillion ?: return null
        val o = m.pricing.outputPerMillion ?: return null
        return (3 * i + o) / 4
    }
}
