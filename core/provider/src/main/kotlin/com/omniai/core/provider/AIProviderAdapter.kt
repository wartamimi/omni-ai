package com.omniai.core.provider

import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIResponse
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.ConnectionResult
import com.omniai.core.model.GenerationParameter
import kotlinx.coroutines.flow.Flow

/**
 * Provider-neutral contract. The chat engine only talks to this interface, so adapters can be
 * swapped without touching the UI. Each adapter declares what it really supports in [capabilities].
 */
interface AIProviderAdapter {
    val capabilities: AdapterCapabilities

    suspend fun validateConnection(): ConnectionResult

    /** Throws [ProviderException] on failure. Returns an empty list when listing is unsupported. */
    suspend fun fetchModels(): List<AIModel>

    /** Throws [ProviderException] on failure. */
    suspend fun generate(request: AIRequest): AIResponse

    /** Emits [AIStreamEvent.Failed] instead of throwing. Only call if [AdapterCapabilities.streaming]. */
    fun stream(request: AIRequest): Flow<AIStreamEvent>

    suspend fun cancelRequest(requestId: String)
}

data class AdapterCapabilities(
    val streaming: Boolean,
    val modelListing: Boolean,
    val imageInput: Boolean,
    val toolCalling: Boolean,
    val supportedParameters: Set<GenerationParameter>,
)

class ProviderException(val error: com.omniai.core.model.ProviderError) : Exception(error.message)
