package com.omniai.core.provider.adapter

import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIResponse
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.MessageRole
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.model.TokenUsage
import com.omniai.core.model.ToolCall
import com.omniai.core.provider.AdapterCapabilities
import com.omniai.core.provider.ProtocolParameters
import com.omniai.core.provider.ProviderException
import com.omniai.core.provider.http.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/**
 * OpenAI-compatible Chat Completions (`POST {base}/chat/completions`).
 * Used by OpenAI and by every gateway documented as OpenAI-compatible; per-provider differences
 * (paths, max token field, stream usage option) come from [OpenAiChatOptions].
 */
class OpenAiChatAdapter(
    config: ProviderConfig,
    client: OkHttpClient,
    credentials: CredentialSupplier,
    private val options: OpenAiChatOptions = OpenAiChatOptions(),
) : BaseHttpAdapter(config, client, credentials) {

    override val capabilities = AdapterCapabilities(
        streaming = config.streamingEnabled,
        modelListing = config.modelsPath != null,
        imageInput = true,
        toolCalling = true,
        supportedParameters = ProtocolParameters.supported(config.protocol),
    )

    private val chatPath get() = config.chatPath ?: "chat/completions"

    override suspend fun fetchModels(): List<AIModel> {
        val path = config.modelsPath ?: return emptyList()
        val result = if (path.startsWith("http")) get(null, absoluteUrl = path) else get(path)
        return ModelMetadataParser.parseOpenAiStyle(config.id, parseJson(result.body))
    }

    override suspend fun generate(request: AIRequest): AIResponse {
        val result = postJson(chatPath, buildBody(request, stream = false), request.requestId)
        val root = parseJson(result.body)
        root["error"]?.let { throw ProviderException(ProviderError.Unknown(it["message"].str() ?: it.toString())) }
        val choice = root["choices"][0] ?: throw ProviderException(ProviderError.InvalidResponse("Response contained no choices."))
        val message = choice["message"]
        return AIResponse(
            text = contentText(message["content"]),
            reasoning = message["reasoning_content"].str() ?: message["reasoning"].str(),
            usage = parseUsage(root["usage"]),
            finishReason = choice["finish_reason"].str(),
            toolCalls = message["tool_calls"].arr()?.mapNotNull { tc ->
                val fn = tc["function"]
                ToolCall(tc["id"].str() ?: return@mapNotNull null, fn["name"].str() ?: return@mapNotNull null, fn["arguments"].str() ?: "{}")
            }.orEmpty(),
            httpStatus = result.status,
        )
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> =
        streamSse(request.requestId, chatPath, buildBody(request, stream = true)) { event -> parseStreamEvent(event) }

    internal fun parseStreamEvent(event: SseEvent): List<AIStreamEvent> {
        val data = event.data.trim()
        if (data == "[DONE]") return listOf(AIStreamEvent.Completed(null))
        if (data.isEmpty()) return emptyList()
        val root = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return emptyList()
        root["error"]?.let { err ->
            return listOf(AIStreamEvent.Failed(ProviderError.Unknown(err["message"].str() ?: err.toString())))
        }
        val out = mutableListOf<AIStreamEvent>()
        val choice = root["choices"][0]
        val delta = choice["delta"]
        (delta["reasoning_content"].str() ?: delta["reasoning"].str())?.takeIf { it.isNotEmpty() }?.let { out += AIStreamEvent.ReasoningDelta(it) }
        delta["content"].str()?.takeIf { it.isNotEmpty() }?.let { out += AIStreamEvent.TextDelta(it) }
        delta["tool_calls"].arr()?.forEach { tc ->
            out += AIStreamEvent.ToolCallDelta(
                index = tc["index"].int() ?: 0,
                id = tc["id"].str(),
                name = tc["function"]["name"].str(),
                argumentsFragment = tc["function"]["arguments"].str().orEmpty(),
            )
        }
        val usage = parseUsage(root["usage"]) ?: parseUsage(root["x_groq"]["usage"])
        if (usage != null) out += AIStreamEvent.Usage(usage)
        // finish_reason arrives before the optional usage chunk; completion is signalled by [DONE].
        return out
    }

    internal fun buildBody(request: AIRequest, stream: Boolean): JsonObject {
        val p = request.params
        val body = buildJsonObject {
            put("model", request.modelId)
            putJsonArray("messages") {
                request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sys ->
                    addJsonObject {
                        put("role", options.systemRole)
                        put("content", sys)
                    }
                }
                request.messages.forEach { m -> add(messageJson(m)) }
            }
            p.temperature?.let { put("temperature", it) }
            p.topP?.let { put("top_p", it) }
            p.maxOutputTokens?.let { put(options.maxTokensField, it) }
            p.frequencyPenalty?.let { put("frequency_penalty", it) }
            p.presencePenalty?.let { put("presence_penalty", it) }
            if (p.stopSequences.isNotEmpty()) putJsonArray("stop") { p.stopSequences.forEach { add(it) } }
            p.seed?.let { put("seed", it) }
            p.reasoningEffort?.let { put("reasoning_effort", it.wire) }
            if (p.jsonMode) putJsonObject("response_format") { put("type", "json_object") }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { t ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", t.name)
                                put("description", t.description)
                                put("parameters", json.parseToJsonElement(t.parametersJsonSchema))
                            }
                        }
                    }
                }
            }
            if (stream) {
                put("stream", true)
                if (options.streamUsageOption) putJsonObject("stream_options") { put("include_usage", true) }
            }
        }
        return withCustomParams(body)
    }

    private fun messageJson(m: com.omniai.core.model.PromptMessage): JsonObject = buildJsonObject {
        val role = when (m.role) {
            MessageRole.SYSTEM -> options.systemRole
            MessageRole.USER -> "user"
            MessageRole.ASSISTANT -> "assistant"
            MessageRole.TOOL -> "tool"
        }
        put("role", role)
        if (m.images.isEmpty()) {
            put("content", m.text)
        } else {
            put(
                "content",
                buildJsonArray {
                    if (m.text.isNotEmpty()) addJsonObject { put("type", "text"); put("text", m.text) }
                    m.images.forEach { img ->
                        addJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", "data:${img.mimeType};base64,${img.base64Data}") }
                        }
                    }
                },
            )
        }
        if (m.toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                m.toolCalls.forEach { tc ->
                    addJsonObject {
                        put("id", tc.id)
                        put("type", "function")
                        putJsonObject("function") { put("name", tc.name); put("arguments", tc.argumentsJson) }
                    }
                }
            }
        }
        m.toolCallId?.let { put("tool_call_id", it) }
    }

    private fun contentText(content: JsonElement?): String = when {
        content == null -> ""
        content.str() != null -> content.str()!!
        content.arr() != null -> content.arr()!!.mapNotNull { it["text"].str() }.joinToString("")
        else -> ""
    }

    private fun parseUsage(usage: JsonElement?): TokenUsage? {
        if (usage == null || usage.obj() == null) return null
        val input = usage["prompt_tokens"].int() ?: usage["input_tokens"].int()
        val output = usage["completion_tokens"].int() ?: usage["output_tokens"].int()
        if (input == null && output == null) return null
        return TokenUsage(input, output, providerReported = true)
    }
}

data class OpenAiChatOptions(
    /** OpenAI's current API uses max_completion_tokens; most compatible gateways use max_tokens. */
    val maxTokensField: String = "max_tokens",
    /** Send stream_options.include_usage (only where documented). */
    val streamUsageOption: Boolean = false,
    val systemRole: String = "system",
)
