package com.omniai.core.provider.adapter

import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIResponse
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.GenerationParameter
import com.omniai.core.model.MessageRole
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.model.TokenUsage
import com.omniai.core.provider.AdapterCapabilities
import com.omniai.core.provider.ProtocolParameters
import com.omniai.core.provider.ProviderException
import com.omniai.core.provider.http.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/**
 * OpenAI Responses API (`POST {base}/responses`). Requests are sent with `store: false` so the
 * provider does not keep conversation state on OmniAI's behalf. Tool calling is not wired for this
 * protocol yet; use Chat Completions for tools.
 */
class OpenAiResponsesAdapter(
    config: ProviderConfig,
    client: OkHttpClient,
    credentials: CredentialSupplier,
) : BaseHttpAdapter(config, client, credentials) {

    override val capabilities = AdapterCapabilities(
        streaming = config.streamingEnabled,
        modelListing = config.modelsPath != null,
        imageInput = true,
        toolCalling = false,
        supportedParameters = ProtocolParameters.supported(config.protocol),
    )

    private val path get() = config.chatPath ?: "responses"

    override suspend fun fetchModels(): List<AIModel> {
        val modelsPath = config.modelsPath ?: return emptyList()
        return ModelMetadataParser.parseOpenAiStyle(config.id, parseJson(get(modelsPath).body))
    }

    override suspend fun generate(request: AIRequest): AIResponse {
        val result = postJson(path, buildBody(request, false), request.requestId)
        val root = parseJson(result.body)
        root["error"].obj()?.let { throw ProviderException(ProviderError.Unknown(it["message"].str() ?: "Error")) }
        val text = StringBuilder()
        val reasoning = StringBuilder()
        root["output"].arr()?.forEach { item ->
            when (item["type"].str()) {
                "message" -> item["content"].arr()?.forEach { c -> if (c["type"].str() == "output_text") text.append(c["text"].str().orEmpty()) }
                "reasoning" -> item["summary"].arr()?.forEach { s -> reasoning.append(s["text"].str().orEmpty()) }
            }
        }
        return AIResponse(text.toString(), reasoning.toString().ifEmpty { null }, usage(root["usage"]), root["status"].str(), emptyList(), result.status)
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> =
        streamSse(request.requestId, path, buildBody(request, true)) { parseStreamEvent(it) }

    internal fun parseStreamEvent(event: SseEvent): List<AIStreamEvent> {
        val root = runCatching { json.parseToJsonElement(event.data) }.getOrNull() ?: return emptyList()
        return when (root["type"].str() ?: event.event) {
            "response.output_text.delta" -> listOfNotNull(root["delta"].str()?.let { AIStreamEvent.TextDelta(it) })
            "response.reasoning_summary_text.delta" -> listOfNotNull(root["delta"].str()?.let { AIStreamEvent.ReasoningDelta(it) })
            "response.completed", "response.incomplete" -> listOfNotNull(
                usage(root["response"]["usage"])?.let { AIStreamEvent.Usage(it) },
                AIStreamEvent.Completed(root["response"]["status"].str()),
            )
            "response.failed" -> listOf(AIStreamEvent.Failed(ProviderError.Unknown(root["response"]["error"]["message"].str() ?: "Response failed")))
            "error" -> listOf(AIStreamEvent.Failed(ProviderError.Unknown(root["message"].str() ?: root["error"]["message"].str() ?: "Stream error")))
            else -> emptyList()
        }
    }

    private fun usage(u: JsonElement?): TokenUsage? {
        if (u.obj() == null) return null
        return TokenUsage(u["input_tokens"].int(), u["output_tokens"].int(), providerReported = true)
    }

    internal fun buildBody(request: AIRequest, stream: Boolean): JsonObject {
        val p = request.params
        val body = buildJsonObject {
            put("model", request.modelId)
            put("store", false)
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("instructions", it) }
            putJsonArray("input") {
                request.messages.filter { it.role != MessageRole.TOOL }.forEach { m ->
                    addJsonObject {
                        val assistant = m.role == MessageRole.ASSISTANT
                        put("role", if (assistant) "assistant" else if (m.role == MessageRole.SYSTEM) "developer" else "user")
                        putJsonArray("content") {
                            if (m.text.isNotEmpty()) addJsonObject { put("type", if (assistant) "output_text" else "input_text"); put("text", m.text) }
                            if (!assistant) m.images.forEach { img ->
                                addJsonObject { put("type", "input_image"); put("image_url", "data:${img.mimeType};base64,${img.base64Data}") }
                            }
                        }
                    }
                }
            }
            p.temperature?.let { put("temperature", it) }
            p.topP?.let { put("top_p", it) }
            p.maxOutputTokens?.let { put("max_output_tokens", it) }
            p.reasoningEffort?.let { putJsonObject("reasoning") { put("effort", it.wire) } }
            if (p.jsonMode) putJsonObject("text") { putJsonObject("format") { put("type", "json_object") } }
            if (stream) put("stream", true)
        }
        return withCustomParams(body)
    }

    @Suppress("unused")
    private val unsupported = setOf(GenerationParameter.STOP_SEQUENCES, GenerationParameter.SEED)
}
