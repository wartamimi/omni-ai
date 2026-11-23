package com.omniai.feature.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.domain.compare.CompareEngine
import com.omniai.core.domain.compare.CompareResult
import com.omniai.core.domain.compare.CompareStatus
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.AppSettings
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.ModelKey
import com.omniai.core.ui.Format
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val engine: CompareEngine,
    providers: ProviderRepository,
    models: ModelRepository,
    settingsRepo: SettingsRepository,
) : ViewModel() {
    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val modelList = models.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val results = engine.results

    val prompt = MutableStateFlow("")
    val systemPrompt = MutableStateFlow("")
    val temperature = MutableStateFlow<Double?>(null)
    val selected = MutableStateFlow<Set<ModelKey>>(emptySet())

    fun toggle(key: ModelKey) = selected.update { if (key in it) it - key else if (it.size < settings.value.compareLimit) it + key else it }

    fun run() {
        val p = prompt.value.trim()
        if (p.isEmpty() || selected.value.isEmpty()) return
        viewModelScope.launch {
            engine.start(p, systemPrompt.value.ifBlank { null }, selected.value.toList(), GenerationParams(temperature = temperature.value), settings.value.compareLimit)
        }
    }

    fun stop(key: ModelKey) = engine.stop(key)
    fun stopAll() = engine.stopAll()
    fun clear() = engine.clear()

    val isRunning get() = results.value.any { it.status == CompareStatus.RUNNING || it.status == CompareStatus.WAITING }

    fun exportMarkdown(results: List<CompareResult>): String = buildString {
        val s = settings.value
        appendLine("# Model comparison")
        appendLine()
        appendLine("**Prompt:**")
        appendLine()
        appendLine(prompt.value)
        if (systemPrompt.value.isNotBlank()) { appendLine(); appendLine("**System prompt:** ${systemPrompt.value}") }
        results.forEach { r ->
            appendLine()
            appendLine("## ${r.providerName} · ${r.target.modelId}")
            appendLine()
            val meta = listOfNotNull(
                r.status.name.lowercase(),
                r.firstTokenMs?.let { "first token ${Format.latency(it)}" },
                r.totalMs?.let { "total ${Format.latency(it)}" },
                r.usage?.let { "${it.inputTokens ?: 0} in / ${it.outputTokens ?: 0} out tokens" + if (it.providerReported) "" else " (estimated)" },
                r.estimatedCostUsd?.let { "≈ ${Format.money(it, s.currency)}" },
            )
            appendLine("_${meta.joinToString(" · ")}_")
            appendLine()
            appendLine(r.error ?: r.text)
        }
    }
}
