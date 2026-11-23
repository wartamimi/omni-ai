package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ModelSource { DISCOVERED, MANUAL }

@Serializable
enum class PricingSource(val label: String) {
    PROVIDER_API("Reported by provider API"),
    USER("Entered by you"),
    NONE("Unknown"),
}

/** Price in USD per one million tokens. */
@Serializable
data class ModelPricing(
    val inputPerMillion: Double?,
    val outputPerMillion: Double?,
    val source: PricingSource,
) {
    val isKnown: Boolean get() = inputPerMillion != null || outputPerMillion != null

    companion object {
        val Unknown = ModelPricing(null, null, PricingSource.NONE)
    }
}

@Serializable
enum class ModelCategory(val label: String) {
    GENERAL_CHAT("General Chat"),
    REASONING("Reasoning"),
    CODING("Coding"),
    TRANSLATION("Translation"),
    WRITING("Writing"),
    MATHEMATICS("Mathematics"),
    VISION("Vision"),
    IMAGE_GENERATION("Image Generation"),
    AUDIO("Audio"),
    EMBEDDINGS("Embeddings"),
    RESEARCH("Research"),
    LOCAL("Local Models"),
}

@Serializable
data class AIModel(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val description: String? = null,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val pricing: ModelPricing = ModelPricing.Unknown,
    val capabilities: ModelCapabilities = ModelCapabilities.Unknown,
    /** Categories the user assigned explicitly (task-type categories cannot be inferred reliably). */
    val userCategories: Set<ModelCategory> = emptySet(),
    val source: ModelSource = ModelSource.DISCOVERED,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
) {
    val key: ModelKey get() = ModelKey(providerId, modelId)
}

@Serializable
data class ModelKey(val providerId: String, val modelId: String)
