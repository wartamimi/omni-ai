package com.omniai.feature.personas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.data.export.DataExporter
import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.AppSettings
import com.omniai.core.model.Persona
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonasViewModel @Inject constructor(
    private val personas: PersonaRepository,
    private val engine: ChatEngine,
    private val exporter: DataExporter,
    private val settingsRepo: SettingsRepository,
    providers: ProviderRepository,
    models: ModelRepository,
) : ViewModel() {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()
    val list = personas.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val providerList = providers.observeProviders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val modelList = models.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(p: Persona) = viewModelScope.launch { personas.upsert(p.copy(updatedAt = Clock.System.now())) }
    fun delete(p: Persona) = viewModelScope.launch {
        personas.delete(p.id)
        if (settings.value.defaultPersonaId == p.id) settingsRepo.update { it.copy(defaultPersonaId = null) }
    }
    fun setDefault(p: Persona?) = viewModelScope.launch { settingsRepo.update { it.copy(defaultPersonaId = p?.id) } }

    fun newPersona(): Persona {
        val now = Clock.System.now()
        return Persona(Ids.new(), "", "", "🙂", "", null, null, null, null, null, false, now, now)
    }

    suspend fun startChat(p: Persona): String = engine.createConversation(personaId = p.id).id

    suspend fun exportJson(): String = exporter.personasToJson(personas.observeAll().first())

    fun importJson(raw: String) = viewModelScope.launch {
        runCatching { exporter.personasFromJson(raw) }
            .onSuccess { list ->
                val now = Clock.System.now()
                // Imported personas get new IDs and don't carry provider bindings from another device.
                list.forEach { personas.upsert(it.copy(id = Ids.new(), isBuiltIn = false, defaultProviderId = null, defaultModelId = null, createdAt = now, updatedAt = now)) }
                _events.emit("Imported ${list.size} personas.")
            }
            .onFailure { _events.emit("Import failed: not a valid persona list.") }
    }
}
