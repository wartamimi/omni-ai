package com.omniai.feature.providers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.common.JsonPath
import com.omniai.core.data.ProviderManager
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.AppSettings
import com.omniai.core.model.ConnectionResult
import com.omniai.core.model.CredentialInfo
import com.omniai.core.model.CustomApiMapping
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderDefinition
import com.omniai.core.provider.ProviderCatalog
import com.omniai.core.provider.ProviderException
import com.omniai.core.provider.adapter.TemplateRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject

data class EditorState(
    val draft: ProviderConfig? = null,
    val definition: ProviderDefinition? = null,
    val isNew: Boolean = true,
    val newKey: String = "",
    val newKeyLabel: String = "Default",
    val testing: Boolean = false,
    val testResult: ConnectionResult? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ProviderEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val providers: ProviderRepository,
    private val credentials: CredentialRepository,
    private val manager: ProviderManager,
    settings: SettingsRepository,
) : ViewModel() {
    private val providerId: String? = savedState.get<String>("providerId")?.takeIf { it.isNotBlank() }
    private val definitionId: String? = savedState.get<String>("definitionId")?.takeIf { it.isNotBlank() }
    private val templateIndex: Int = savedState.get<Int>("templateIndex") ?: -1

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val appSettings = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val savedId = MutableStateFlow(providerId)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val keys: StateFlow<List<CredentialInfo>> = savedId.flatMapLatest { id -> if (id == null) emptyFlow() else credentials.observeForProvider(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    init {
        viewModelScope.launch {
            val existing = providerId?.let { providers.get(it) }
            if (existing != null) {
                _state.value = EditorState(existing, ProviderCatalog.find(existing.definitionId), isNew = false)
                return@launch
            }
            val definition = ProviderCatalog.find(definitionId)
            val draft = when {
                definition != null -> manager.draftFromDefinition(definition)
                else -> customDraft(templateIndex)
            }
            _state.value = EditorState(draft, definition, isNew = true)
        }
    }

    private fun customDraft(index: Int): ProviderConfig {
        val template = ProviderCatalog.customTemplates.getOrElse(index) { ProviderCatalog.customTemplates.first() }
        val now = Clock.System.now()
        return ProviderConfig(
            id = Ids.new(), definitionId = null, name = "My ${template.label} API", category = ProviderCategory.CUSTOM,
            protocol = template.protocol, baseUrl = "https://", auth = template.auth, chatPath = template.chatPath,
            modelsPath = template.modelsPath, apiVersion = template.apiVersion,
            customMapping = if (template.protocol == ApiProtocol.CUSTOM_REST) DEFAULT_MAPPING else null,
            createdAt = now, updatedAt = now,
        )
    }

    fun edit(transform: (ProviderConfig) -> ProviderConfig) = _state.update { s -> s.copy(draft = s.draft?.let(transform), testResult = null, error = null) }
    fun setNewKey(key: String) = _state.update { it.copy(newKey = key, testResult = null) }
    fun setNewKeyLabel(label: String) = _state.update { it.copy(newKeyLabel = label) }

    /** Validates fields that would otherwise fail at request time. Returns an error message or null. */
    private fun validate(c: ProviderConfig): String? {
        if (c.name.isBlank()) return "Name is required."
        c.customParamsJson?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            if (parsed !is JsonObject) return "Custom parameters must be a JSON object, e.g. {\"user\": \"me\"}."
        }
        c.customHeaders.forEach { h ->
            if (h.name.isBlank() || !h.name.matches(Regex("[A-Za-z0-9-]+"))) return "Header name \"${h.name}\" is not valid."
            if (h.name.equals("host", true) || h.name.equals("content-length", true)) return "The ${h.name} header can't be overridden."
        }
        if (c.protocol == ApiProtocol.CUSTOM_REST) {
            val m = c.customMapping ?: return "Custom REST providers need a request/response mapping."
            if (m.httpMethod.uppercase() !in setOf("POST", "PUT")) return "Only POST and PUT are supported."
            val unknown = TemplateRenderer.placeholders(m.bodyTemplate) - TemplateRenderer.known
            if (unknown.isNotEmpty()) return "Unknown placeholders: ${unknown.joinToString()}"
            val sample = TemplateRenderer.render(m.bodyTemplate, TemplateRenderer.known.associateWith { kotlinx.serialization.json.JsonPrimitive("x") })
            if (runCatching { json.parseToJsonElement(sample) as JsonObject }.isFailure) return "The body template must render to a JSON object."
            if (!JsonPath.isValid(m.responseTextPath)) return "Response text path is not valid."
            m.streamDeltaPath?.takeIf { it.isNotBlank() }?.let { if (!JsonPath.isValid(it)) return "Stream delta path is not valid." }
        }
        return null
    }

    fun test() {
        val c = _state.value.draft ?: return
        validate(c)?.let { msg -> _state.update { it.copy(error = msg) }; return }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null) }
            val result = manager.testDraft(c, _state.value.newKey)
            _state.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun save() {
        val s = _state.value
        val c = s.draft ?: return
        validate(c)?.let { msg -> _state.update { it.copy(error = msg) }; return }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            try {
                val saved = manager.save(c, s.newKey.takeIf { it.isNotBlank() }, s.newKeyLabel)
                savedId.value = saved.id
                _state.update { it.copy(draft = saved, isNew = false, newKey = "", saving = false, saved = true) }
                if (saved.modelsPath != null && (s.newKey.isNotBlank() || credentials.activeCredential(saved.id) != null)) manager.refreshModels(saved.id)
            } catch (e: ProviderException) {
                _state.update { it.copy(saving = false, error = e.error.message) }
            }
        }
    }

    fun addKey(label: String, secret: String) = viewModelScope.launch { savedId.value?.let { credentials.add(it, label, secret) } }
    fun replaceKey(id: String, secret: String) = viewModelScope.launch { credentials.replaceSecret(id, secret) }
    fun renameKey(id: String, label: String) = viewModelScope.launch { credentials.rename(id, label) }
    fun deleteKey(id: String) = viewModelScope.launch { credentials.delete(id) }
    fun activateKey(id: String) = viewModelScope.launch { credentials.setActive(id) }

    fun configAsJson(): String = _state.value.draft?.let { json.encodeToString(ProviderConfig.serializer(), it) } ?: ""

    fun applyJson(raw: String): String? {
        val current = _state.value.draft ?: return "Nothing to edit."
        val parsed = runCatching { json.decodeFromString(ProviderConfig.serializer(), raw) }.getOrElse { return "Invalid JSON: ${it.message?.take(160)}" }
        // id, timestamps and default flag cannot be changed from JSON.
        val merged = parsed.copy(id = current.id, createdAt = current.createdAt, updatedAt = current.updatedAt, isDefault = current.isDefault)
        validate(merged)?.let { return it }
        _state.update { it.copy(draft = merged, testResult = null, error = null) }
        return null
    }

    companion object {
        val DEFAULT_MAPPING = CustomApiMapping(
            endpointPath = "v1/chat",
            bodyTemplate = "{\n  \"model\": {{model}},\n  \"messages\": {{messages}},\n  \"temperature\": {{temperature}},\n  \"stream\": {{stream}}\n}",
            responseTextPath = "choices[0].message.content",
            inputTokensPath = "usage.prompt_tokens",
            outputTokensPath = "usage.completion_tokens",
            errorMessagePath = "error.message",
            streamDeltaPath = "choices[0].delta.content",
        )
    }
}

