package com.omniai.core.provider.adapter

import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIResponse
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.CapabilityState
import com.omniai.core.model.MessageRole
import com.omniai.core.model.ModelCapabilities
import com.omniai.core.model.ModelCapability
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/** Gemini API `models/{model}:generateContent` and `:streamGenerateContent?alt=sse`. */
class GeminiAdapter(
    config: ProviderConfig,
    client: OkHttpClient,
    credentials: CredentialSupplier,
) : BaseHttpAdapter(config, client, credentials) {

    override val capabilities = AdapterCapabilities(
        streaming = config.streamingEnabled,
        modelListing = config.modelsPath != null,
        imageInput = true,
        toolCalling = true,
        supportedParameters = ProtocolParameters.supported(config.protocol),
    )

    private fun modelPath(modelId: String, method: String) = "models/${modelId.removePrefix("models/")}:$method"

    override suspend fun fetchModels(): List<AIModel> {
        val path = config.modelsPath ?: return emptyList()
        val models = mutableListOf<AIModel>()
        var pageToken: String? = null
        repeat(10) {
            val query = buildMap { put("pageSize", "1000"); pageToken?.let { put("pageToken", it) } }
            val root = parseJson(get(path, query).body)
            root["models"].arr()?.forEach { item ->
                val methods = item["supportedGenerationMethods"].arr()?.mapNotNull { it.str() }.orEmpty()
                if ("generateContent" !in methods) return@forEach
                val id = item["name"].str()?.removePrefix("models/") ?: return@forEach
                models += AIModel(
                    providerId = config.id,
                    modelId = id,
                    displayName = item["displayName"].str() ?: id,
                    description = item["description"].str(),
                    contextWindow = item["inputTokenLimit"].int(),
                    maxOutputTokens = item["outputTokenLimit"].int(),
                    capabilities = ModelCapabilities.Unknown
                        .with(ModelCapability.STREAMING, if ("streamGenerateContent" in methods) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED)
                        .let { c -> item["thinking"].bool()?.let { c.with(ModelCapability.REASONING, if (it) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED) } ?: c },
                )
            }
            pageToken = root["nextPageToken"].str() ?: return models
        }
        return models
    }

    override suspend fun generate(request: AIRequest): AIResponse {
        val result = postJson(modelPath(request.modelId, "generateContent"), buildBody(request), request.requestId)
        val root = parseJson(result.body)
        val (text, reasoning, toolCalls) = extractParts(root)
        if (text.isEmpty() && toolCalls.isEmpty()) blockError(root)?.let { throw ProviderException(it) }
        return AIResponse(text, reasoning.ifEmpty { null }, parseUsage(root), root["candidates"][0]["finishReason"].str(), toolCalls, result.status)
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> =
        streamSse(request.requestId, modelPath(request.modelId, "streamGenerateContent"), buildBody(request), mapOf("alt" to "sse")) { parseStreamEvent(it) }

    internal fun parseStreamEvent(event: SseEvent): List<AIStreamEvent> {
        val root = runCatching { json.parseToJsonElement(event.data) }.getOrNull() ?: return emptyList()
        root["error"]?.let { return listOf(AIStreamEvent.Failed(ProviderError.Unknown(it["message"].str() ?: "Stream error"))) }
        val out = mutableListOf<AIStreamEvent>()
        val (text, reasoning, toolCalls) = extractParts(root)
        if (reasoning.isNotEmpty()) out += AIStreamEvent.ReasoningDelta(reasoning)
        if (text.isNotEmpty()) out += AIStreamEvent.TextDelta(text)
        toolCalls.forEachIndexed { i, tc -> out += AIStreamEvent.ToolCallDelta(i, tc.id, tc.name, tc.argumentsJson) }
        parseUsage(root)?.let { out += AIStreamEvent.Usage(it) }
        if (text.isEmpty() && toolCalls.isEmpty()) blockError(root)?.let { out += AIStreamEvent.Failed(it) }
        return out
    }

    private fun extractParts(root: JsonElement): Triple<String, String, List<ToolCall>> {
        val parts = root["candidates"][0]["content"]["parts"].arr() ?: return Triple("", "", emptyList())
        val text = StringBuilder()
        val reasoning = StringBuilder()
        val calls = mutableListOf<ToolCall>()
        parts.forEach { part ->
            val t = part["text"].str()
            if (t != null) {
                if (part["thought"].bool() == true) reasoning.append(t) else text.append(t)
            }
            part["functionCall"]?.let { fc ->
                val name = fc["name"].str() ?: return@let
                calls += ToolCall(fc["id"].str() ?: "call_${calls.size}_$name", name, fc["args"]?.toString() ?: "{}")
            }
        }
        return Triple(text.toString(), reasoning.toString(), calls)
    }

    private fun blockError(root: JsonElement): ProviderError? {
        val block = root["promptFeedback"]["blockReason"].str()
        if (block != null) return ProviderError.Unprocessable("Gemini blocked the prompt ($block).")
        val finish = root["candidates"][0]["finishReason"].str()
        if (finish != null && finish !in setOf("STOP", "MAX_TOKENS", "FINISH_REASON_UNSPECIFIED")) {
            return ProviderError.Unprocessable("Gemini stopped without output (finishReason=$finish).")
        }
        return null
    }

    private fun parseUsage(root: JsonElement): TokenUsage? {
        val meta = root["usageMetadata"] ?: return null
        val input = meta["promptTokenCount"].int()
        val output = (meta["candidatesTokenCount"].int() ?: 0) + (meta["thoughtsTokenCount"].int() ?: 0)
        if (input == null && meta["candidatesTokenCount"] == null) return null
        return TokenUsage(input, output, providerReported = true)
    }

    internal fun buildBody(request: AIRequest): JsonObject {
        val p = request.params
        val body = buildJsonObject {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { sys ->
                putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", sys) } } }
            }
            putJsonArray("contents") {
                request.messages.forEach { m ->
                    addJsonObject {
                        put("role", if (m.role == MessageRole.ASSISTANT) "model" else "user")
                        putJsonArray("parts") {
                            if (m.role == MessageRole.TOOL) {
                                addJsonObject {
                                    putJsonObject("functionResponse") {
                                        put("name", m.toolName.orEmpty())
                                        putJsonObject("response") { put("result", m.text) }
                                    }
                                }
                            } else {
                                if (m.text.isNotEmpty()) addJsonObject { put("text", m.text) }
                                m.images.forEach { img ->
                                    addJsonObject {
                                        putJsonObject("inline_data") { put("mime_type", img.mimeType); put("data", img.base64Data) }
                                    }
                                }
                                m.toolCalls.forEach { tc ->
                                    addJsonObject {
                                        putJsonObject("functionCall") {
                                            put("name", tc.name)
                                            put("args", runCatching { json.parseToJsonElement(tc.argumentsJson) }.getOrElse { buildJsonObject { } })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                p.temperature?.let { put("temperature", it) }
                p.topP?.let { put("topP", it) }
                p.topK?.let { put("topK", it) }
                p.maxOutputTokens?.let { put("maxOutputTokens", it) }
                if (p.stopSequences.isNotEmpty()) putJsonArray("stopSequences") { p.stopSequences.forEach { add(it) } }
                p.seed?.let { put("seed", it) }
                p.presencePenalty?.let { put("presencePenalty", it) }
                p.frequencyPenalty?.let { put("frequencyPenalty", it) }
                if (p.jsonMode) put("responseMimeType", "application/json")
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            request.tools.forEach { t ->
                                addJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("parameters", json.parseToJsonElement(t.parametersJsonSchema))
                                }
                            }
                        }
                    }
                }
            }
        }
        return withCustomParams(body)
    }
}
