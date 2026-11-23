package com.omniai.core.model

import kotlinx.serialization.Serializable

/**
 * Tri-state capability flag. OmniAI never upgrades UNKNOWN to SUPPORTED without evidence
 * (provider metadata or explicit user confirmation).
 */
@Serializable
enum class CapabilityState { SUPPORTED, UNSUPPORTED, UNKNOWN }

@Serializable
enum class ModelCapability(val label: String) {
    STREAMING("Streaming"),
    VISION("Image input"),
    AUDIO_INPUT("Audio input"),
    IMAGE_GENERATION("Image generation"),
    TOOL_CALLING("Tool calling"),
    REASONING("Reasoning"),
    STRUCTURED_OUTPUT("Structured output"),
    EMBEDDINGS("Embeddings"),
}

@Serializable
data class ModelCapabilities(
    val states: Map<ModelCapability, CapabilityState> = emptyMap(),
) {
    fun state(capability: ModelCapability): CapabilityState = states[capability] ?: CapabilityState.UNKNOWN
    fun supports(capability: ModelCapability): Boolean = state(capability) == CapabilityState.SUPPORTED
    fun with(capability: ModelCapability, state: CapabilityState) = copy(states = states + (capability to state))

    /** Merge preferring known values from [other] over UNKNOWN values here. */
    fun mergedWith(other: ModelCapabilities): ModelCapabilities {
        val keys = states.keys + other.states.keys
        return ModelCapabilities(
            keys.associateWith { key ->
                val mine = state(key)
                val theirs = other.state(key)
                if (theirs != CapabilityState.UNKNOWN) theirs else mine
            },
        )
    }

    companion object {
        val Unknown = ModelCapabilities()
    }
}
