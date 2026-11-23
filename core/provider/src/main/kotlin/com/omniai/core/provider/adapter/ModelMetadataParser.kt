package com.omniai.core.provider.adapter

import com.omniai.core.model.AIModel
import com.omniai.core.model.CapabilityState
import com.omniai.core.model.ModelCapabilities
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelPricing
import com.omniai.core.model.ModelSource
import com.omniai.core.model.PricingSource
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * Parses OpenAI-style `/models` listings. Only fields the provider actually returns are used;
 * everything else stays null/UNKNOWN. Field names are taken from each provider's documented schema
 * (OpenRouter, SambaNova, DeepInfra, Groq, Mistral, xAI, DeepSeek, Hugging Face).
 */
object ModelMetadataParser {

    fun parseOpenAiStyle(providerId: String, root: JsonElement): List<AIModel> {
        val items = (root["data"] as? JsonArray) ?: (root as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
        return items.mapNotNull { item -> parseItem(providerId, item) }.distinctBy { it.modelId }
    }

    private fun parseItem(providerId: String, item: JsonElement): AIModel? {
        val id = item["id"].str() ?: item["name"].str() ?: return null
        val meta = item["metadata"]
        val context = item["context_length"].int()
            ?: item["context_window"].int()
            ?: item["max_context_length"].int()
            ?: meta["context_length"].int()
            ?: item["context_size"].int()
        val maxOut = item["top_provider"]["max_completion_tokens"].int()
            ?: item["max_completion_tokens"].int()
            ?: meta["max_tokens"].int()
            ?: item["max_output_tokens"].int()
        val pricing = parsePricing(item)
        var caps = ModelCapabilities.Unknown
        // Mistral: capabilities { completion_chat, function_calling, vision }
        item["capabilities"].obj()?.let { c ->
            c["vision"].bool()?.let { caps = caps.with(ModelCapability.VISION, it.state()) }
            c["function_calling"].bool()?.let { caps = caps.with(ModelCapability.TOOL_CALLING, it.state()) }
        }
        // OpenRouter: architecture.input_modalities, supported_parameters
        item["architecture"]["input_modalities"].arr()?.let { mods ->
            val m = mods.mapNotNull { it.str() }
            caps = caps.with(ModelCapability.VISION, ("image" in m).state())
            caps = caps.with(ModelCapability.AUDIO_INPUT, ("audio" in m).state())
        }
        item["supported_parameters"].arr()?.let { params ->
            val p = params.mapNotNull { it.str() }
            caps = caps.with(ModelCapability.TOOL_CALLING, ("tools" in p).state())
            caps = caps.with(ModelCapability.STRUCTURED_OUTPUT, ("response_format" in p || "structured_outputs" in p).state())
            if ("reasoning" in p || "include_reasoning" in p) caps = caps.with(ModelCapability.REASONING, CapabilityState.SUPPORTED)
        }
        return AIModel(
            providerId = providerId,
            modelId = id,
            displayName = item["name"].str()?.takeIf { it != id } ?: item["display_name"].str() ?: id,
            description = item["description"].str()?.take(500),
            contextWindow = context,
            maxOutputTokens = maxOut,
            pricing = pricing,
            capabilities = caps,
            source = ModelSource.DISCOVERED,
        )
    }

    /**
     * OpenRouter / SambaNova: `pricing.prompt` / `pricing.completion` in USD per token (strings).
     * DeepInfra: `metadata.pricing.input_tokens` / `output_tokens` per 1M tokens.
     * Unrecognized shapes are ignored rather than guessed.
     */
    private fun parsePricing(item: JsonElement): ModelPricing {
        item["pricing"].obj()?.let { p ->
            val prompt = p["prompt"].dbl()
            val completion = p["completion"].dbl()
            if (prompt != null || completion != null) {
                return ModelPricing(prompt?.times(1_000_000), completion?.times(1_000_000), PricingSource.PROVIDER_API)
            }
        }
        item["metadata"]["pricing"].obj()?.let { p ->
            val input = p["input_tokens"].dbl()
            val output = p["output_tokens"].dbl()
            if (input != null || output != null) return ModelPricing(input, output, PricingSource.PROVIDER_API)
        }
        return ModelPricing.Unknown
    }

    private fun Boolean.state() = if (this) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED
}
