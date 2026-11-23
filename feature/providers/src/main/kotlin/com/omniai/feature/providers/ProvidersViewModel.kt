package com.omniai.feature.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.data.ProviderManager
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.CredentialInfo
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProviderRow(
    val config: ProviderConfig,
    val keys: List<CredentialInfo>,
    val modelCount: Int,
    val busy: Boolean,
) {
    val activeKey: CredentialInfo? get() = keys.firstOrNull { it.isActive }
}

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val providers: ProviderRepository,
    credentials: CredentialRepository,
    models: ModelRepository,
    private val manager: ProviderManager,
    private val settings: SettingsRepository,
    private val exporter: DataExporter,
) : ViewModel() {
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val rows = combine(providers.observeProviders(), credentials.observeAll(), models.observeAll(), busy) { ps, creds, ms, b ->
        val byProvider = creds.groupBy { it.providerId }
        val counts = ms.groupingBy { it.providerId }.eachCount()
        ps.map { ProviderRow(it, byProvider[it.id].orEmpty(), counts[it.id] ?: 0, it.id in b) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun withBusy(id: String, block: suspend () -> Unit) = viewModelScope.launch {
        busy.update { it + id }
        try { block() } finally { busy.update { it - id } }
    }

    fun test(id: String) = withBusy(id) {
        val r = manager.test(id)
        _messages.emit(if (r.success) "✓ ${r.message} (${r.latencyMs} ms)" else "✗ ${r.error?.title ?: "Failed"}: ${r.message}")
    }

    fun refreshModels(id: String) = withBusy(id) {
        manager.refreshModels(id)
            .onSuccess { _messages.emit("Found $it models.") }
            .onFailure { _messages.emit(it.message ?: "Could not refresh models.") }
    }

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch { providers.setEnabled(id, enabled) }
    fun setDefault(id: String) = viewModelScope.launch {
        providers.setDefault(id)
        val p = providers.get(id)
        settings.update { it.copy(defaultProviderId = id, defaultModelId = p?.defaultModelId ?: it.defaultModelId.takeIf { _ -> it.defaultProviderId == id }) }
    }
    fun duplicate(id: String) = viewModelScope.launch { providers.duplicate(id)?.let { _messages.emit("Duplicated as \"${it.name}\". Add an API key to use it.") } }
    fun delete(id: String) = viewModelScope.launch { providers.delete(id); _messages.emit("Provider deleted.") }

    suspend fun exportConfiguration(): String = exporter.exportConfiguration()
    fun importConfiguration(raw: String) = viewModelScope.launch {
        runCatching { exporter.importAll(raw) }
            .onSuccess { _messages.emit("Imported ${it.providers} providers. Add API keys to use them.") }
            .onFailure { _messages.emit("Import failed: ${it.message?.take(120)}") }
    }

    fun statusOf(row: ProviderRow): CredentialStatus? = row.activeKey?.status
}
