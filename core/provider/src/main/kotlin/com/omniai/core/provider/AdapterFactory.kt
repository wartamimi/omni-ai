package com.omniai.core.provider

import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.ProviderConfig
import com.omniai.core.provider.adapter.AnthropicAdapter
import com.omniai.core.provider.adapter.CredentialSupplier
import com.omniai.core.provider.adapter.CustomRestAdapter
import com.omniai.core.provider.adapter.GeminiAdapter
import com.omniai.core.provider.adapter.OpenAiChatAdapter
import com.omniai.core.provider.adapter.OpenAiChatOptions
import com.omniai.core.provider.adapter.OpenAiResponsesAdapter
import com.omniai.core.provider.http.HttpClientFactory

/** Creates the protocol-specific adapter for a provider configuration. */
class AdapterFactory(private val httpClients: HttpClientFactory) {

    fun create(config: ProviderConfig, credentials: CredentialSupplier): AIProviderAdapter {
        val client = httpClients.clientFor(config.timeoutSeconds)
        return when (config.protocol) {
            ApiProtocol.OPENAI_CHAT_COMPLETIONS -> OpenAiChatAdapter(config, client, credentials, optionsFor(config))
            ApiProtocol.OPENAI_RESPONSES -> OpenAiResponsesAdapter(config, client, credentials)
            ApiProtocol.ANTHROPIC_MESSAGES -> AnthropicAdapter(config, client, credentials)
            ApiProtocol.GEMINI_GENERATE_CONTENT -> GeminiAdapter(config, client, credentials)
            ApiProtocol.CUSTOM_REST -> CustomRestAdapter(config, client, credentials)
        }
    }

    companion object {
        /** Providers documenting `stream_options.include_usage`. Others may still report usage; we parse it if present. */
        private val streamUsageProviders = setOf("openai", "openrouter", "deepseek", "perplexity", "qwen")

        fun optionsFor(config: ProviderConfig) = OpenAiChatOptions(
            maxTokensField = if (config.definitionId == "openai") "max_completion_tokens" else "max_tokens",
            streamUsageOption = config.definitionId in streamUsageProviders,
        )
    }
}
