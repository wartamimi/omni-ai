package com.omniai.core.provider.adapter

import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIResponse
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.CapabilityState
import com.omniai.core.model.MessageRole
import com.omniai.core.model.ModelCapabilities
import com.omniai.core.model.ModelCapability
import com.omniai.core.model.ModelSource
import com.omniai.core.model.PromptMessage
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.model.TokenUsage
import com.omniai.core.model.ToolCall
import com.omniai.core.provider.AdapterCapabilities
import com.omniai.core.provider.AnthropicVersions
import com.omniai.core.provider.ProtocolParameters
import com.omniai.core.provider.ProviderException
import com.omniai.core.provider.http.SseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/** Anthropic Messages API (`POST {base}/v1/messages`). */
class AnthropicAdapter(
    config: ProviderConfig,
    client: OkHttpClient,
    credentials: CredentialSupplier,
) : BaseHttpAdapter(config, client, credentials) {

    private val apiVersion get() = config.apiVersion?.takeIf { it.isNotBlank() } ?: AnthropicVersions.DEFAULT

    override val capabilities = AdapterCapabilities(
        streaming = config.streamingEnabled,
        modelListing = config.modelsPath != null,
        imageInput = true,
        toolCalling = true,
        supportedParameters = ProtocolParameters.supported(config.protocol, config.apiVersion),
    )

    private val chatPath get() = config.chatPath ?: "v1/messages"

    override fun protocolHeaders() = mapOf("anthropic-version" to apiVersion)

    override suspend fun fetchModels(): List<AIModel> {
        val path = config.modelsPath ?: return emptyList()
        val models = mutableListOf<AIModel>()
        var afterId: String? = null
        repeat(10) { // pagination guard
            val query = buildMap { put("limit", "1000"); afterId?.let { put("after_id", it) } }
            val root = parseJson(get(path, query).body)
            root["data"].arr()?.forEach { item ->
                val id = item["id"].str() ?: return@forEach
                models += AIModel(
                    providerId = config.id,
                    modelId = id,
                    displayName = item["display_name"].str() ?: id,
                    contextWindow = item["max_input_tokens"].int(),
                    maxOutputTokens = item["max_tokens"].int(),
                    capabilities = ModelCapabilities.Unknown.with(ModelCapability.STREAMING, CapabilityState.SUPPORTED),
                    source = ModelSource.DISCOVERED,
                )
            }
            if (root["has_more"].bool() != true) return models
            afterId = root["last_id"].str() ?: return models
        }
        return models
    }

    override suspend fun generate(request: AIRequest): AIResponse {
        val result = postJson(chatPath, buildBody(request, stream = false), request.requestId)
        val root = parseJson(result.body)
        if (root["type"].str() == "error") throw ProviderException(ProviderError.Unknown(root["error"]["message"].str() ?: "Unknown error"))
        val blocks = root["content"].arr() ?: JsonArray(emptyList())
        val text = blocks.filter { it["type"].str() == "text" }.joinToString("") { it["text"].str().orEmpty() }
        val thinking = blocks.filter { it["type"].str() == "thinking" }.joinToString("") { it["thinking"].str().orEmpty() }
        val toolCalls = blocks.filter { it["type"].str() == "tool_use" }.mapNotNull { b ->
            ToolCall(b["id"].str() ?: return@mapNotNull null, b["name"].str() ?: return@mapNotNull null, b["input"]?.toString() ?: "{}")
        }
        return AIResponse(
            text = text,
            reasoning = thinking.ifEmpty { null },
            usage = TokenUsage(root["usage"]["input_tokens"].int(), root["usage"]["output_tokens"].int(), providerReported = true),
            finishReason = root["stop_reason"].str(),
            toolCalls = toolCalls,
            httpStatus = result.status,
        )
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> {
        var inputTokens: Int? = null
        return streamSse(request.requestId, chatPath, buildBody(request, stream = true)) { event ->
            val (events, input) = parseStreamEvent(event, inputTokens)
            inputTokens = input
            events
        }
    }

    /** Returns events plus the input token count carried from message_start. */
    internal fun parseStreamEvent(event: SseEvent, inputTokens: Int?): Pair<List<AIStreamEvent>, Int?> {
        val root = runCatching { json.parseToJsonElement(event.data) }.getOrNull() ?: return emptyList<AIStreamEvent>() to inputTokens
        val type = root["type"].str() ?: event.event
        return when (type) {
            "message_start" -> emptyList<AIStreamEvent>() to (root["message"]["usage"]["input_tokens"].int() ?: inputTokens)
            "content_block_start" -> {
                val block = root["content_block"]
                if (block["type"].str() == "tool_use") {
                    listOf(AIStreamEvent.ToolCallDelta(root["index"].int() ?: 0, block["id"].str(), block["name"].str(), "")) to inputTokens
                } else emptyList<AIStreamEvent>() to inputTokens
            }
            "content_block_delta" -> {
                val delta = root["delta"]
                val e: AIStreamEvent? = when (delta["type"].str()) {
                    "text_delta" -> delta["text"].str()?.let { AIStreamEvent.TextDelta(it) }
                    "thinking_delta" -> delta["thinking"].str()?.let { AIStreamEvent.ReasoningDelta(it) }
                    "input_json_delta" -> AIStreamEvent.ToolCallDelta(root["index"].int() ?: 0, null, null, delta["partial_json"].str().orEmpty())
                    else -> null
                }
                listOfNotNull(e) to inputTokens
            }
            "message_delta" -> {
                val out = root["usage"]["output_tokens"].int()
                val list = mutableListOf<AIStreamEvent>()
                if (out != null || inputTokens != null) list += AIStreamEvent.Usage(TokenUsage(inputTokens, out, providerReported = true))
                list to inputTokens
            }
            "message_stop" -> listOf(AIStreamEvent.Completed(null)) to inputTokens
            "error" -> listOf(AIStreamEvent.Failed(errorFrom(root))) to inputTokens
            else -> emptyList<AIStreamEvent>() to inputTokens // ping and future event types
        }
    }

    private fun errorFrom(root: kotlinx.serialization.json.JsonElement): ProviderError {
        val type = root["error"]["type"].str()
        val msg = root["error"]["message"].str() ?: "Stream error"
        return when (type) {
            "overloaded_error" -> ProviderError.Server(msg, 529)
            "rate_limit_error" -> ProviderError.RateLimited(msg, null)
            "invalid_request_error" -> ProviderError.BadRequest(msg)
            else -> ProviderError.Unknown(msg)
        }
    }

    internal fun buildBody(request: AIRequest, stream: Boolean): JsonObject {
        val p = request.params
        val dropSampling = AnthropicVersions.rejectsSamplingParams(apiVersion)
        val body = buildJsonObject {
            put("model", request.modelId)
            // max_tokens is required by the Messages API.
            put("max_tokens", p.maxOutputTokens ?: DEFAULT_MAX_TOKENS)
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { put("system", it) }
            put("messages", messagesJson(request.messages))
            if (!dropSampling) {
                p.temperature?.let { put("temperature", it.coerceIn(0.0, 1.0)) }
                p.topP?.let { put("top_p", it) }
                p.topK?.let { put("top_k", it) }
            }
            if (p.stopSequences.isNotEmpty()) putJsonArray("stop_sequences") { p.stopSequences.forEach { add(it) } }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", json.parseToJsonElement(t.parametersJsonSchema))
                        }
                    }
                }
            }
            if (stream) put("stream", true)
        }
        return withCustomParams(body)
    }

    /** Converts history; system messages inside history are folded into user text (API has no system role in messages). */
    private fun messagesJson(messages: List<PromptMessage>): JsonArray = buildJsonArray {
        messages.forEach { m ->
            addJsonObject {
                when (m.role) {
                    MessageRole.TOOL -> {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "tool_result")
                                put("tool_use_id", m.toolCallId.orEmpty())
                                put("content", m.text)
                            }
                        }
                    }
                    else -> {
                        put("role", if (m.role == MessageRole.ASSISTANT) "assistant" else "user")
                        putJsonArray("content") {
                            m.images.forEach { img ->
                                addJsonObject {
                                    put("type", "image")
                                    putJsonObject("source") {
                                        put("type", "base64")
                                        put("media_type", img.mimeType)
                                        put("data", img.base64Data)
                                    }
                                }
                            }
                            if (m.text.isNotEmpty()) addJsonObject { put("type", "text"); put("text", m.text) }
                            m.toolCalls.forEach { tc ->
                                addJsonObject {
                                    put("type", "tool_use")
                                    put("id", tc.id)
                                    put("name", tc.name)
                                    put("input", runCatching { json.parseToJsonElement(tc.argumentsJson) }.getOrElse { buildJsonObject { } })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 4096
    }
}
