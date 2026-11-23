package com.omniai.core.data

import com.omniai.core.common.EndpointValidator
import com.omniai.core.domain.repository.AdapterProvider
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.provider.AIProviderAdapter
import com.omniai.core.provider.AdapterFactory
import com.omniai.core.provider.ProviderException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates adapters on demand. The API key is fetched (decrypted) only when a request is sent,
 * and the base URL is re-validated against the current network policy every time.
 */
@Singleton
class AdapterProviderImpl @Inject constructor(
    private val providers: ProviderRepository,
    private val credentials: CredentialRepository,
    private val settings: SettingsRepository,
    private val factory: AdapterFactory,
) : AdapterProvider {

    override suspend fun adapterFor(providerId: String): AIProviderAdapter? {
        val config = providers.get(providerId) ?: return null
        validate(config)
        return factory.create(config) { credentials.activeSecret(providerId) }
    }

    override fun adapterForDraft(config: ProviderConfig, secret: String?): AIProviderAdapter =
        factory.create(config) { secret }

    suspend fun validate(config: ProviderConfig) {
        val s = settings.current()
        val result = EndpointValidator.validateBaseUrl(config.baseUrl, EndpointValidator.Policy(s.allowLocalNetworkEndpoints, s.allowCleartextLocalEndpoints))
        if (result is EndpointValidator.Result.Invalid) throw ProviderException(ProviderError.BlockedEndpoint(result.reason))
    }
}
