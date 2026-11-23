package com.omniai.core.provider

import com.omniai.core.model.AIRequest
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.AuthConfig
import com.omniai.core.model.GenerationParams
import com.omniai.core.model.MessageRole
import com.omniai.core.model.PromptMessage
import com.omniai.core.model.ProviderCategory
import com.omniai.core.model.ProviderConfig
import com.omniai.core.provider.http.HttpClientFactory

internal object TestSupport {
    val clients = HttpClientFactory(allowLocalNetwork = { true })

    fun config(
        baseUrl: String,
        protocol: ApiProtocol,
        auth: AuthConfig = AuthConfig.Bearer,
        definitionId: String? = null,
        chatPath: String? = null,
        modelsPath: String? = "models",
        apiVersion: String? = null,
    ) = ProviderConfig(
        id = "p1", definitionId = definitionId, name = "Test", category = ProviderCategory.CUSTOM, protocol = protocol,
        baseUrl = baseUrl.trimEnd('/'), auth = auth, chatPath = chatPath, modelsPath = modelsPath, apiVersion = apiVersion,
        timeoutSeconds = 10, maxRetries = 1, createdAt = 0, updatedAt = 0,
    )

    fun request(text: String = "Hello", params: GenerationParams = GenerationParams(temperature = 0.5, maxOutputTokens = 100)) = AIRequest(
        requestId = "r1", modelId = "test-model", systemPrompt = "Be brief.",
        messages = listOf(PromptMessage(MessageRole.USER, text)), params = params,
    )

    const val KEY = "sk-test-0123456789abcdefghij"
}
