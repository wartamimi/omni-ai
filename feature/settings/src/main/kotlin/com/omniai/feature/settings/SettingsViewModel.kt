package com.omniai.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.data.ApiLogBuffer
import com.omniai.core.data.ProviderManager
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.domain.repository.ToolRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.AppSettings
import com.omniai.core.model.ToolPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val providerRepository: ProviderRepository,
    private val modelRepository: ModelRepository,
    personaRepository: PersonaRepository,
    private val conversations: ConversationRepository,
    private val credentials: CredentialRepository,
    private val usage: UsageRepository,
    private val tools: ToolRepository,
    private val exporter: DataExporter,
    private val providerManager: ProviderManager,
    val apiLog: ApiLogBuffer,
) : ViewModel() {

    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val providers = providerRepository.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val models = modelRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val personas = personaRepository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val toolConfigs = tools.observeTools().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val toolLogs = tools.observeLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun setToolPermission(name: String, permission: ToolPermission) = viewModelScope.launch { tools.setPermission(name, permission) }
    fun clearToolLogs() = viewModelScope.launch { tools.clearLogs() }

    suspend fun exportAll(includeConversations: Boolean): String = exporter.exportAll(includeConversations)

    fun import(raw: String) = viewModelScope.launch {
        runCatching { exporter.import(raw) }
            .onSuccess { _messages.emit("Imported ${it.providers} providers, ${it.conversations} chats, ${it.prompts} prompts, ${it.personas} personas. Add API keys for imported providers.") }
            .onFailure { _messages.emit("Import failed: ${it.message?.take(120)}") }
    }

    fun clearHistory() = viewModelScope.launch { conversations.deleteAll(); _messages.emit("Conversation history deleted.") }
    fun deleteCredentials() = viewModelScope.launch { credentials.deleteAll(); _messages.emit("All API keys deleted from this device.") }
    fun deleteProviders() = viewModelScope.launch {
        credentials.deleteAll()
        providerRepository.deleteAll()
        settingsRepository.update { it.copy(defaultProviderId = null, defaultModelId = null) }
        _messages.emit("All providers and keys deleted.")
    }
    fun resetUsage() = viewModelScope.launch { usage.deleteAll(); _messages.emit("Usage statistics reset.") }

    fun clearAll() = viewModelScope.launch {
        conversations.deleteAll()
        credentials.deleteAll()
        providerRepository.deleteAll()
        usage.deleteAll()
        tools.clearLogs()
        settingsRepository.update { AppSettings() }
        apiLog.clear()
        _messages.emit("All OmniAI data on this device was cleared.")
    }

    /** Network diagnostics: tests every enabled provider and reports status and latency. */
    fun runDiagnostics(onResult: (List<String>) -> Unit) = viewModelScope.launch {
        val lines = providers.value.filter { it.enabled }.map { p ->
            val r = providerManager.test(p.id)
            val status = if (r.success) "OK" else (r.error?.title ?: "Failed")
            "${p.name}: $status · ${r.latencyMs} ms${r.httpStatus?.let { " · HTTP $it" } ?: ""}${if (!r.success) " · ${r.message.take(100)}" else ""}"
        }
        onResult(lines.ifEmpty { listOf("No enabled providers to test.") })
    }
}
