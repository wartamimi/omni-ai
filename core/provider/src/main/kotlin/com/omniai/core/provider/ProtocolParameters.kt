package com.omniai.core.provider

import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.GenerationParameter
import com.omniai.core.model.GenerationParameter.FREQUENCY_PENALTY
import com.omniai.core.model.GenerationParameter.JSON_MODE
import com.omniai.core.model.GenerationParameter.MAX_OUTPUT_TOKENS
import com.omniai.core.model.GenerationParameter.PRESENCE_PENALTY
import com.omniai.core.model.GenerationParameter.REASONING_EFFORT
import com.omniai.core.model.GenerationParameter.SEED
import com.omniai.core.model.GenerationParameter.STOP_SEQUENCES
import com.omniai.core.model.GenerationParameter.STREAMING
import com.omniai.core.model.GenerationParameter.SYSTEM_PROMPT
import com.omniai.core.model.GenerationParameter.TEMPERATURE
import com.omniai.core.model.GenerationParameter.TOP_K
import com.omniai.core.model.GenerationParameter.TOP_P

/**
 * Parameters each protocol accepts on the wire, per its official API reference.
 * Individual models may still reject some of them; such errors are surfaced, never hidden.
 */
object ProtocolParameters {
    fun supported(protocol: ApiProtocol, apiVersion: String? = null): Set<GenerationParameter> = when (protocol) {
        ApiProtocol.OPENAI_CHAT_COMPLETIONS -> setOf(
            TEMPERATURE, TOP_P, MAX_OUTPUT_TOKENS, FREQUENCY_PENALTY, PRESENCE_PENALTY, STOP_SEQUENCES, SEED,
            REASONING_EFFORT, JSON_MODE, STREAMING, SYSTEM_PROMPT,
        )
        ApiProtocol.OPENAI_RESPONSES -> setOf(TEMPERATURE, TOP_P, MAX_OUTPUT_TOKENS, REASONING_EFFORT, JSON_MODE, STREAMING, SYSTEM_PROMPT)
        ApiProtocol.ANTHROPIC_MESSAGES -> if (AnthropicVersions.rejectsSamplingParams(apiVersion)) {
            setOf(MAX_OUTPUT_TOKENS, STOP_SEQUENCES, STREAMING, SYSTEM_PROMPT)
        } else {
            setOf(TEMPERATURE, TOP_P, TOP_K, MAX_OUTPUT_TOKENS, STOP_SEQUENCES, STREAMING, SYSTEM_PROMPT)
        }
        ApiProtocol.GEMINI_GENERATE_CONTENT -> setOf(
            TEMPERATURE, TOP_P, TOP_K, MAX_OUTPUT_TOKENS, STOP_SEQUENCES, SEED, FREQUENCY_PENALTY, PRESENCE_PENALTY,
            JSON_MODE, STREAMING, SYSTEM_PROMPT,
        )
        ApiProtocol.CUSTOM_REST -> setOf(TEMPERATURE, MAX_OUTPUT_TOKENS, STREAMING, SYSTEM_PROMPT)
    }
}

object AnthropicVersions {
    /** Version that still accepts temperature/top_p/top_k. */
    const val DEFAULT = "2023-06-01"
    const val LATEST = "2026-10-21"

    /** Per Anthropic's versioning docs, 2026-10-21 rejects temperature, top_p and top_k with HTTP 400. */
    fun rejectsSamplingParams(version: String?): Boolean = version != null && version >= LATEST
}
