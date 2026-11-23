package com.omniai.core.provider.http

import com.omniai.core.common.Redactor
import com.omniai.core.model.NetworkFailure
import com.omniai.core.model.ProviderError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Maps HTTP statuses, provider error bodies and IO exceptions to [ProviderError]. */
object ErrorMapper {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromHttp(status: Int, body: String?, retryAfterHeader: String?, secrets: Collection<String> = emptyList()): ProviderError {
        val message = Redactor.redact(extractMessage(body) ?: "HTTP $status", secrets).take(600)
        val lower = message.lowercase() + " " + (body?.lowercase()?.take(2000) ?: "")
        val retryAfter = parseRetryAfter(retryAfterHeader)
        return when {
            isContextOverflow(lower) -> ProviderError.ContextOverflow(message, status)
            isInsufficientCredits(status, lower) -> ProviderError.InsufficientCredits(message, status)
            status == 400 && isUnsupportedModel(lower) -> ProviderError.UnsupportedModel(message, status)
            status == 404 && lower.contains("model") -> ProviderError.UnsupportedModel(message, status)
            status == 400 -> ProviderError.BadRequest(message)
            status == 401 -> ProviderError.Unauthorized(message)
            status == 403 -> ProviderError.Forbidden(message)
            status == 404 -> ProviderError.NotFound(message)
            status == 408 -> ProviderError.RequestTimeout(message)
            status == 413 -> ProviderError.PayloadTooLarge(message)
            status == 422 -> ProviderError.Unprocessable(message)
            status == 429 -> ProviderError.RateLimited(message, retryAfter)
            status in 500..599 -> ProviderError.Server(message, status, retryAfter)
            else -> ProviderError.Unknown(message, status)
        }
    }

    fun fromException(e: Throwable, secrets: Collection<String> = emptyList()): ProviderError {
        val msg = Redactor.redact(e.message ?: e::class.java.simpleName, secrets)
        return when (e) {
            is BlockedAddressException -> ProviderError.BlockedEndpoint(msg)
            is UnknownHostException -> ProviderError.Network(msg, NetworkFailure.DNS)
            is SSLException -> ProviderError.Network(msg, NetworkFailure.SSL)
            is SocketTimeoutException -> ProviderError.Timeout("The provider did not respond in time.")
            is ConnectException, is NoRouteToHostException -> ProviderError.Network(msg, NetworkFailure.CONNECTION)
            is InterruptedIOException -> if (msg.contains("cancel", ignoreCase = true)) ProviderError.Cancelled() else ProviderError.Timeout(msg)
            is IOException -> if (msg.contains("Canceled", ignoreCase = true)) ProviderError.Cancelled() else ProviderError.Network(msg, NetworkFailure.INTERRUPTED)
            else -> ProviderError.Unknown(msg)
        }
    }

    /** Pulls a human-readable message out of common error shapes (OpenAI, Anthropic, Gemini, generic). */
    fun extractMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return body.trim().take(300)
        val obj = element as? JsonObject ?: return body.trim().take(300)
        val error = obj["error"]
        return when (error) {
            is JsonObject -> (error["message"] as? JsonPrimitive)?.content
            is JsonPrimitive -> error.content
            else -> null
        } ?: (obj["message"] as? JsonPrimitive)?.content
            ?: (obj["detail"] as? JsonPrimitive)?.content
            ?: runCatching { obj["errors"]?.toString() }.getOrNull()
            ?: body.trim().take(300)
    }

    fun parseRetryAfter(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        value.trim().toLongOrNull()?.let { return it.coerceAtLeast(0) }
        return runCatching {
            val date = java.time.ZonedDateTime.parse(value.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            (date.toEpochSecond() - System.currentTimeMillis() / 1000).coerceAtLeast(0)
        }.getOrNull()
    }

    private fun isContextOverflow(text: String) = listOf(
        "context_length_exceeded", "context length", "maximum context", "too many tokens", "prompt is too long",
        "input is too long", "exceeds the context", "token limit",
    ).any { it in text }

    private fun isInsufficientCredits(status: Int, text: String) = status == 402 ||
        listOf("insufficient_quota", "insufficient balance", "insufficient credits", "credit balance", "exceeded your current quota", "billing")
            .any { it in text }

    private fun isUnsupportedModel(text: String) = listOf("model_not_found", "does not exist", "unknown model", "invalid model", "not supported model")
        .any { it in text }
}

