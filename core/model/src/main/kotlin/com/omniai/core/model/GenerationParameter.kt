package com.omniai.core.model

import kotlinx.serialization.Serializable

/** Every tunable generation parameter. Protocols declare which subset they accept. */
@Serializable
enum class GenerationParameter(val label: String) {
    TEMPERATURE("Temperature"),
    TOP_P("Top P"),
    TOP_K("Top K"),
    MAX_OUTPUT_TOKENS("Max output tokens"),
    FREQUENCY_PENALTY("Frequency penalty"),
    PRESENCE_PENALTY("Presence penalty"),
    STOP_SEQUENCES("Stop sequences"),
    SEED("Seed"),
    REASONING_EFFORT("Reasoning effort"),
    JSON_MODE("JSON mode"),
    STREAMING("Streaming"),
    SYSTEM_PROMPT("System prompt"),
}

@Serializable
enum class ReasoningEffort(val wire: String) { MINIMAL("minimal"), LOW("low"), MEDIUM("medium"), HIGH("high") }

@Serializable
data class GenerationParams(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val stopSequences: List<String> = emptyList(),
    val seed: Long? = null,
    val reasoningEffort: ReasoningEffort? = null,
    val jsonMode: Boolean = false,
    val stream: Boolean = true,
) {
    /** Parameters with non-default values set. */
    fun explicitlySet(): Set<GenerationParameter> = buildSet {
        if (temperature != null) add(GenerationParameter.TEMPERATURE)
        if (topP != null) add(GenerationParameter.TOP_P)
        if (topK != null) add(GenerationParameter.TOP_K)
        if (maxOutputTokens != null) add(GenerationParameter.MAX_OUTPUT_TOKENS)
        if (frequencyPenalty != null) add(GenerationParameter.FREQUENCY_PENALTY)
        if (presencePenalty != null) add(GenerationParameter.PRESENCE_PENALTY)
        if (stopSequences.isNotEmpty()) add(GenerationParameter.STOP_SEQUENCES)
        if (seed != null) add(GenerationParameter.SEED)
        if (reasoningEffort != null) add(GenerationParameter.REASONING_EFFORT)
        if (jsonMode) add(GenerationParameter.JSON_MODE)
    }

    /** Overlay non-null values from [override] on top of this. */
    fun overriddenBy(override: GenerationParams?): GenerationParams {
        if (override == null) return this
        return GenerationParams(
            temperature = override.temperature ?: temperature,
            topP = override.topP ?: topP,
            topK = override.topK ?: topK,
            maxOutputTokens = override.maxOutputTokens ?: maxOutputTokens,
            frequencyPenalty = override.frequencyPenalty ?: frequencyPenalty,
            presencePenalty = override.presencePenalty ?: presencePenalty,
            stopSequences = override.stopSequences.ifEmpty { stopSequences },
            seed = override.seed ?: seed,
            reasoningEffort = override.reasoningEffort ?: reasoningEffort,
            jsonMode = override.jsonMode || jsonMode,
            stream = override.stream && stream,
        )
    }

    /** Drop parameters the target does not support instead of silently remapping them. */
    fun restrictedTo(supported: Set<GenerationParameter>): GenerationParams = GenerationParams(
        temperature = temperature.takeIf { GenerationParameter.TEMPERATURE in supported },
        topP = topP.takeIf { GenerationParameter.TOP_P in supported },
        topK = topK.takeIf { GenerationParameter.TOP_K in supported },
        maxOutputTokens = maxOutputTokens.takeIf { GenerationParameter.MAX_OUTPUT_TOKENS in supported },
        frequencyPenalty = frequencyPenalty.takeIf { GenerationParameter.FREQUENCY_PENALTY in supported },
        presencePenalty = presencePenalty.takeIf { GenerationParameter.PRESENCE_PENALTY in supported },
        stopSequences = if (GenerationParameter.STOP_SEQUENCES in supported) stopSequences else emptyList(),
        seed = seed.takeIf { GenerationParameter.SEED in supported },
        reasoningEffort = reasoningEffort.takeIf { GenerationParameter.REASONING_EFFORT in supported },
        jsonMode = jsonMode && GenerationParameter.JSON_MODE in supported,
        stream = stream && GenerationParameter.STREAMING in supported,
    )
}
