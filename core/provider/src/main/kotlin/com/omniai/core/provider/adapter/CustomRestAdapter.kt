package com.omniai.core.provider.adapter

import com.omniai.core.common.JsonPath
import com.omniai.core.model.AIModel
import com.omniai.core.model.AIRequest
import com.omniai.core.model.AIResponse
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.CustomApiMapping
import com.omniai.core.model.MessageRole
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.model.TokenUsage
import com.omniai.core.provider.AdapterCapabilities
import com.omniai.core.provider.ProtocolParameters
import com.omniai.core.provider.ProviderException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

/**
 * Fully user-mapped REST JSON API. The request body is a JSON template whose placeholders are
 * replaced with JSON values (strings are quoted and escaped for you):
 * `{{model}} {{messages}} {{prompt}} {{system}} {{temperature}} {{max_tokens}}`.
 * The response is read with simple JSON paths. Only POST/PUT are supported.
 */
class CustomRestAdapter(
    config: ProviderConfig,
    client: OkHttpClient,
    credentials: CredentialSupplier,
) : BaseHttpAdapter(config, client, credentials) {

    private val mapping: CustomApiMapping = config.customMapping
        ?: throw ProviderException(ProviderError.BadRequest("Custom provider has no request/response mapping."))

    override val capabilities = AdapterCapabilities(
        streaming = config.streamingEnabled && mapping.streamDeltaPath != null,
        modelListing = config.modelsPath != null,
        imageInput = false,
        toolCalling = false,
        supportedParameters = ProtocolParameters.supported(config.protocol),
    )

    override suspend fun fetchModels(): List<AIModel> {
        val path = config.modelsPath ?: return emptyList()
        return ModelMetadataParser.parseOpenAiStyle(config.id, parseJson(get(path).body))
    }

    override suspend fun generate(request: AIRequest): AIResponse {
        val result = postJson(mapping.endpointPath, renderBody(request, stream = false), request.requestId, mapping.queryParams.associate { it.name to it.value })
        val root = parseJson(result.body)
        mapping.errorMessagePath?.let { path -> JsonPath.readString(root, path)?.let { throw ProviderException(ProviderError.Unknown(it)) } }
        val text = JsonPath.readString(root, mapping.responseTextPath)
            ?: throw ProviderException(ProviderError.InvalidResponse("Response path '${mapping.responseTextPath}' was not found in the response."))
        val input = mapping.inputTokensPath?.let { JsonPath.readInt(root, it) }
        val output = mapping.outputTokensPath?.let { JsonPath.readInt(root, it) }
        val usage = if (input != null || output != null) TokenUsage(input, output, providerReported = true) else null
        return AIResponse(text, null, usage, null, emptyList(), result.status)
    }

    override fun stream(request: AIRequest): Flow<AIStreamEvent> {
        val deltaPath = mapping.streamDeltaPath
            ?: return flowOf(AIStreamEvent.Failed(ProviderError.BadRequest("Streaming is not configured for this custom provider.")))
        return streamSse(request.requestId, mapping.endpointPath, renderBody(request, stream = true), mapping.queryParams.associate { it.name to it.value }) { event ->
            val data = event.data.trim()
            if (mapping.streamDoneSentinel != null && data == mapping.streamDoneSentinel) return@streamSse listOf(AIStreamEvent.Completed(null))
            val root = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return@streamSse emptyList()
            listOfNotNull(JsonPath.readString(root, deltaPath)?.takeIf { it.isNotEmpty() }?.let { AIStreamEvent.TextDelta(it) })
        }
    }

    internal fun renderBody(request: AIRequest, stream: Boolean): JsonObject {
        val messages = buildJsonArray {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let { s -> addJsonObject { put("role", "system"); put("content", s) } }
            request.messages.forEach { m ->
                addJsonObject {
                    put("role", m.role.name.lowercase())
                    put("content", m.text)
                }
            }
        }
        val lastUser = request.messages.lastOrNull { it.role == MessageRole.USER }?.text.orEmpty()
        val values = mapOf(
            "model" to JsonPrimitive(request.modelId),
            "messages" to messages,
            "prompt" to JsonPrimitive(lastUser),
            "system" to (request.systemPrompt?.let { JsonPrimitive(it) } ?: JsonNull),
            "temperature" to (request.params.temperature?.let { JsonPrimitive(it) } ?: JsonNull),
            "max_tokens" to (request.params.maxOutputTokens?.let { JsonPrimitive(it) } ?: JsonNull),
            "stream" to JsonPrimitive(stream),
        )
        val rendered = TemplateRenderer.render(mapping.bodyTemplate, values)
        val element = runCatching { json.parseToJsonElement(rendered) }.getOrElse {
            throw ProviderException(ProviderError.BadRequest("The request body template does not produce valid JSON."))
        }
        return withCustomParams(element as? JsonObject ?: throw ProviderException(ProviderError.BadRequest("The request body template must be a JSON object.")))
    }
}

object TemplateRenderer {
    private val placeholder = Regex("\\{\\{\\s*([a-z_]+)\\s*\\}\\}")

    fun render(template: String, values: Map<String, kotlinx.serialization.json.JsonElement>): String =
        placeholder.replace(template) { m -> values[m.groupValues[1]]?.toString() ?: m.value }

    fun placeholders(template: String): Set<String> = placeholder.findAll(template).map { it.groupValues[1] }.toSet()

    val known = setOf("model", "messages", "prompt", "system", "temperature", "max_tokens", "stream")
}
