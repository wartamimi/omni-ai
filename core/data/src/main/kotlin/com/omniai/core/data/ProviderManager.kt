package com.omniai.core.data

import com.omniai.core.common.Clock
import com.omniai.core.common.EndpointValidator
import com.omniai.core.common.Ids
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.ConnectionResult
import com.omniai.core.model.CredentialStatus
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderDefinition
import com.omniai.core.model.ProviderError
import com.omniai.core.model.ProviderHealthEvent
import com.omniai.core.provider.ProviderException
import javax.inject.Inject
import javax.inject.Singleton

/** Provider use cases: create from catalog, test connections, refresh model lists. */
@Singleton
class ProviderManager @Inject constructor(
    private val providers: ProviderRepository,
    private val credentials: CredentialRepository,
    private val models: ModelRepository,
    private val settings: SettingsRepository,
    private val adapters: AdapterProviderImpl,
) {
    private val clock = Clock.System

    fun draftFromDefinition(definition: ProviderDefinition): ProviderConfig {
        val now = clock.now()
        return ProviderConfig(
            id = Ids.new(), definitionId = definition.id, name = definition.displayName, category = definition.category,
            protocol = definition.protocol, baseUrl = definition.defaultBaseUrl, auth = definition.auth, chatPath = definition.chatPath,
            modelsPath = definition.modelsPath, apiVersion = definition.apiVersion, customHeaders = definition.defaultHeaders,
            streamingEnabled = definition.supportsStreaming, createdAt = now, updatedAt = now,
        )
    }

    suspend fun validateUrl(baseUrl: String): EndpointValidator.Result {
        val s = settings.current()
        return EndpointValidator.validateBaseUrl(baseUrl, EndpointValidator.Policy(s.allowLocalNetworkEndpoints, s.allowCleartextLocalEndpoints))
    }

    /** Saves the provider and (optionally) a new key. Returns the saved config. */
    suspend fun save(config: ProviderConfig, newKey: String?, keyLabel: String): ProviderConfig {
        val url = validateUrl(config.baseUrl)
        if (url is EndpointValidator.Result.Invalid) throw ProviderException(ProviderError.BlockedEndpoint(url.reason))
        val normalized = config.copy(baseUrl = (url as EndpointValidator.Result.Valid).normalizedBaseUrl)
        providers.upsert(normalized)
        if (!newKey.isNullOrBlank()) credentials.add(normalized.id, keyLabel, newKey)
        return providers.get(normalized.id) ?: normalized
    }

    /** Tests an unsaved draft (from the editor) with the typed key or the stored key. */
    suspend fun testDraft(config: ProviderConfig, typedKey: String?): ConnectionResult {
        return try {
            adapters.validate(config)
            val key = typedKey?.takeIf { it.isNotBlank() } ?: credentials.activeSecret(config.id)
            adapters.adapterForDraft(config, key).validateConnection()
        } catch (e: ProviderException) {
            ConnectionResult(false, e.error.httpStatus, 0, e.error.message, e.error)
        }
    }

    /** Tests a saved provider, updates the credential status and records provider health. */
    suspend fun test(providerId: String): ConnectionResult {
        val result = try {
            val adapter = adapters.adapterFor(providerId) ?: return ConnectionResult(false, null, 0, "Provider not found")
            adapter.validateConnection()
        } catch (e: ProviderException) {
            ConnectionResult(false, e.error.httpStatus, 0, e.error.message, e.error)
        }
        credentials.activeCredential(providerId)?.let { cred ->
            val status = when {
                result.success -> CredentialStatus.VALID
                result.error is ProviderError.Unauthorized || result.error is ProviderError.Forbidden -> CredentialStatus.INVALID
                else -> CredentialStatus.ERROR
            }
            credentials.updateStatus(cred.id, status)
        }
        if (result.error !is ProviderError.MissingCredential) {
            providers.recordHealth(ProviderHealthEvent(providerId, clock.now(), result.success, result.httpStatus, result.latencyMs, result.error?.let { it::class.simpleName }))
        }
        return result
    }

    /** Fetches models from the provider API. Returns the number of models, or the error. */
    suspend fun refreshModels(providerId: String): Result<Int> = try {
        val adapter = adapters.adapterFor(providerId) ?: throw ProviderException(ProviderError.NotFound("Provider not found"))
        if (!adapter.capabilities.modelListing) {
            Result.failure(ProviderException(ProviderError.BadRequest("This provider has no documented model list endpoint. Add model IDs manually.")))
        } else {
            val list = adapter.fetchModels()
            models.replaceDiscovered(providerId, list)
            Result.success(list.size)
        }
    } catch (e: ProviderException) {
        Result.failure(e)
    }
}
