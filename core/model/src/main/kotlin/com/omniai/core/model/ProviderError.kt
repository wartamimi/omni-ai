package com.omniai.core.model

/**
 * Normalized provider failure. [message] must already be redacted (no credentials).
 */
sealed class ProviderError(open val message: String, open val httpStatus: Int? = null) {
    data class BadRequest(override val message: String, override val httpStatus: Int? = 400) : ProviderError(message, httpStatus)
    data class Unauthorized(override val message: String) : ProviderError(message, 401)
    data class Forbidden(override val message: String) : ProviderError(message, 403)
    data class NotFound(override val message: String) : ProviderError(message, 404)
    data class RequestTimeout(override val message: String) : ProviderError(message, 408)
    data class PayloadTooLarge(override val message: String) : ProviderError(message, 413)
    data class Unprocessable(override val message: String) : ProviderError(message, 422)
    data class RateLimited(override val message: String, val retryAfterSeconds: Long?) : ProviderError(message, 429)
    data class InsufficientCredits(override val message: String, override val httpStatus: Int?) : ProviderError(message, httpStatus)
    data class ContextOverflow(override val message: String, override val httpStatus: Int?) : ProviderError(message, httpStatus)
    data class UnsupportedModel(override val message: String, override val httpStatus: Int?) : ProviderError(message, httpStatus)
    data class Server(override val message: String, override val httpStatus: Int?, val retryAfterSeconds: Long? = null) : ProviderError(message, httpStatus)
    data class Timeout(override val message: String) : ProviderError(message)
    data class Network(override val message: String, val kind: NetworkFailure) : ProviderError(message)
    data class BlockedEndpoint(override val message: String) : ProviderError(message)
    data class MissingCredential(override val message: String) : ProviderError(message)
    data class InvalidResponse(override val message: String) : ProviderError(message)
    data class Cancelled(override val message: String = "Cancelled") : ProviderError(message)
    data class Unknown(override val message: String, override val httpStatus: Int? = null) : ProviderError(message, httpStatus)

    val title: String
        get() = when (this) {
            is BadRequest -> "Bad request"
            is Unauthorized -> "Invalid API key"
            is Forbidden -> "Access denied"
            is NotFound -> "Endpoint or model not found"
            is RequestTimeout -> "Request timeout"
            is PayloadTooLarge -> "Request too large"
            is Unprocessable -> "Request rejected"
            is RateLimited -> "Rate limited"
            is InsufficientCredits -> "Insufficient credits"
            is ContextOverflow -> "Context too long"
            is UnsupportedModel -> "Unsupported model"
            is Server -> "Provider server error"
            is Timeout -> "Timed out"
            is Network -> kind.label
            is BlockedEndpoint -> "Endpoint blocked"
            is MissingCredential -> "No API key"
            is InvalidResponse -> "Unexpected response"
            is Cancelled -> "Cancelled"
            is Unknown -> "Error"
        }

    /** Whether a retry could plausibly succeed without user action. */
    val isTransient: Boolean
        get() = this is RateLimited || this is Server || this is Timeout || this is RequestTimeout ||
            (this is Network && kind != NetworkFailure.SSL)
}

enum class NetworkFailure(val label: String) {
    DNS("Cannot resolve host"),
    SSL("Secure connection failed"),
    CONNECTION("Connection failed"),
    INTERRUPTED("Connection interrupted"),
    OFFLINE("No network connection"),
}
