package com.omniai.core.provider.adapter

import com.omniai.core.common.EndpointValidator
import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.AuthType
import com.omniai.core.model.ConnectionResult
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderError
import com.omniai.core.provider.AIProviderAdapter
import com.omniai.core.provider.ProviderException
import com.omniai.core.provider.http.ErrorMapper
import com.omniai.core.provider.http.RetryPolicy
import com.omniai.core.provider.http.SseEvent
import com.omniai.core.provider.http.SseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Supplies the decrypted API key just-in-time. Never cache the result in fields. */
fun interface CredentialSupplier {
    suspend fun apiKey(): String?
}

data class HttpResult(val status: Int, val body: String, val latencyMs: Long)

/**
 * Shared HTTP plumbing for all adapters: auth, custom headers, cancellation, retries and SSE.
 */
abstract class BaseHttpAdapter(
    protected val config: ProviderConfig,
    protected val client: OkHttpClient,
    private val credentials: CredentialSupplier,
) : AIProviderAdapter {

    protected val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val retryPolicy = RetryPolicy(config.maxRetries)

    protected fun url(path: String?, query: Map<String, String> = emptyMap()): HttpUrl {
        val builder = EndpointValidator.join(config.baseUrl, path).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    /** Extra protocol-specific headers (e.g. anthropic-version). */
    protected open fun protocolHeaders(): Map<String, String> = emptyMap()

    protected suspend fun requestBuilder(url: HttpUrl): Pair<Request.Builder, String?> {
        val key = if (config.auth.type == AuthType.NONE) null else credentials.apiKey()
        if (config.auth.type != AuthType.NONE && key.isNullOrBlank()) {
            throw ProviderException(ProviderError.MissingCredential("Add an API key for ${config.name} first."))
        }
        var finalUrl = url
        val builder = Request.Builder()
        protocolHeaders().forEach { (k, v) -> builder.header(k, v) }
        config.customHeaders.forEach { builder.header(it.name, it.value) }
        when (config.auth.type) {
            AuthType.BEARER -> builder.header("Authorization", "Bearer $key")
            AuthType.HEADER -> builder.header(config.auth.name ?: "x-api-key", key!!)
            AuthType.QUERY -> finalUrl = url.newBuilder().addQueryParameter(config.auth.name ?: "key", key).build()
            AuthType.NONE -> Unit
        }
        builder.url(finalUrl)
        return builder to key
    }

    protected suspend fun postJson(path: String?, body: JsonObject, requestId: String? = null, query: Map<String, String> = emptyMap()): HttpResult {
        val (builder, key) = requestBuilder(url(path, query))
        val request = builder.post(body.toString().toRequestBody(JSON_MEDIA)).build()
        return executeWithRetry(request, requestId, key)
    }

    protected suspend fun get(path: String?, query: Map<String, String> = emptyMap(), absoluteUrl: String? = null): HttpResult {
        val target = absoluteUrl?.toHttpUrl() ?: url(path, query)
        val (builder, key) = requestBuilder(target)
        return executeWithRetry(builder.get().build(), null, key)
    }

    private suspend fun executeWithRetry(request: Request, requestId: String?, key: String?): HttpResult {
        var attempt = 0
        while (true) {
            val start = System.nanoTime()
            val outcome: Result<HttpResult> = runCatching {
                val call = client.newCall(request)
                if (requestId != null) activeCalls[requestId] = call
                try {
                    call.await().use { response ->
                        val text = withContext(Dispatchers.IO) { response.body.string() }
                        val latency = (System.nanoTime() - start) / 1_000_000
                        if (!response.isSuccessful) {
                            throw ProviderException(ErrorMapper.fromHttp(response.code, text, response.header("Retry-After"), listOfNotNull(key)))
                        }
                        HttpResult(response.code, text, latency)
                    }
                } finally {
                    if (requestId != null) activeCalls.remove(requestId)
                }
            }
            val failure = outcome.exceptionOrNull() ?: return outcome.getOrThrow()
            val error = (failure as? ProviderException)?.error ?: ErrorMapper.fromException(failure, listOfNotNull(key))
            if (error is ProviderError.Cancelled) throw ProviderException(error)
            val wait = retryPolicy.delayBeforeRetry(error, attempt, responseStarted = false) ?: throw ProviderException(error)
            attempt++
            delay(wait)
        }
    }

    /**
     * Runs a streaming POST and converts each SSE event with [onEvent]. Never retried after the
     * first byte arrives, so partial output is preserved and nothing is generated twice.
     */
    protected fun streamSse(
        requestId: String,
        path: String?,
        body: JsonObject,
        query: Map<String, String> = emptyMap(),
        onEvent: (SseEvent) -> List<AIStreamEvent>,
    ): Flow<AIStreamEvent> = flow {
        var key: String? = null
        try {
            val (builder, k) = requestBuilder(url(path, query))
            key = k
            val request = builder.header("Accept", "text/event-stream").post(body.toString().toRequestBody(JSON_MEDIA)).build()
            val response = openStreamWithRetry(request, requestId, k)
            emit(AIStreamEvent.Started)
            response.use {
                val parser = SseParser(response.body.source())
                var completed = false
                while (true) {
                    val event = parser.next() ?: break
                    for (e in onEvent(event)) {
                        emit(e)
                        if (e is AIStreamEvent.Completed || e is AIStreamEvent.Failed) completed = true
                    }
                    if (completed) break
                }
                if (!completed) emit(AIStreamEvent.Completed(finishReason = null))
            }
        } catch (e: ProviderException) {
            emit(AIStreamEvent.Failed(e.error))
        } catch (e: IOException) {
            emit(AIStreamEvent.Failed(ErrorMapper.fromException(e, listOfNotNull(key))))
        } finally {
            activeCalls.remove(requestId)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun openStreamWithRetry(request: Request, requestId: String, key: String?): Response {
        var attempt = 0
        while (true) {
            val call = client.newCall(request)
            activeCalls[requestId] = call
            currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
            val error: ProviderError = try {
                val response = call.execute()
                if (response.isSuccessful) return response
                val text = response.use { it.body.string() }
                ErrorMapper.fromHttp(response.code, text, response.header("Retry-After"), listOfNotNull(key))
            } catch (e: IOException) {
                ErrorMapper.fromException(e, listOfNotNull(key))
            }
            val wait = retryPolicy.delayBeforeRetry(error, attempt, responseStarted = false) ?: throw ProviderException(error)
            attempt++
            delay(wait)
        }
    }

    override suspend fun cancelRequest(requestId: String) {
        activeCalls.remove(requestId)?.cancel()
    }

    override suspend fun validateConnection(): ConnectionResult {
        val start = System.nanoTime()
        return try {
            val count = if (capabilities.modelListing) fetchModels().size else null
            val latency = (System.nanoTime() - start) / 1_000_000
            if (count == null) {
                ConnectionResult(true, null, latency, "Configuration looks valid. This provider has no model list endpoint, so send a test message to fully verify.")
            } else {
                ConnectionResult(true, 200, latency, "Connected. $count models available.", modelCount = count)
            }
        } catch (e: ProviderException) {
            ConnectionResult(false, e.error.httpStatus, (System.nanoTime() - start) / 1_000_000, e.error.message, e.error)
        }
    }

    protected fun parseJson(text: String): JsonElement = try {
        json.parseToJsonElement(text)
    } catch (e: Exception) {
        throw ProviderException(ProviderError.InvalidResponse("The provider returned a response that is not valid JSON."))
    }

    /** Merges validated custom params (JSON object) into a request body; explicit fields win. */
    protected fun withCustomParams(body: JsonObject): JsonObject {
        val raw = config.customParamsJson?.takeIf { it.isNotBlank() } ?: return body
        val extra = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return body
        return JsonObject(extra + body)
    }

    companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** Suspends on an OkHttp call with coroutine cancellation wired to Call.cancel(). */
internal suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
