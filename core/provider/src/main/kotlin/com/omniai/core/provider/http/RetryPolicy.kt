package com.omniai.core.provider.http

import com.omniai.core.model.NetworkFailure
import com.omniai.core.model.ProviderError
import kotlin.math.min
import kotlin.math.pow

/**
 * Conservative retries that cannot duplicate charges or generated content:
 *  - only before any response bytes were consumed,
 *  - only when the provider clearly did not process the request (429, 503, connection refused, DNS),
 *  - Retry-After is respected; long waits are surfaced to the user instead of retried.
 */
class RetryPolicy(
    val maxRetries: Int,
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 20_000,
    private val maxRetryAfterSeconds: Long = 30,
) {
    fun delayBeforeRetry(error: ProviderError, attempt: Int, responseStarted: Boolean): Long? {
        if (responseStarted || attempt >= maxRetries) return null
        val retryAfter = when (error) {
            is ProviderError.RateLimited -> error.retryAfterSeconds
            is ProviderError.Server -> if (error.httpStatus == 503) error.retryAfterSeconds else return null
            is ProviderError.Network -> if (error.kind == NetworkFailure.DNS || error.kind == NetworkFailure.CONNECTION) null else return null
            else -> return null
        }
        if (retryAfter != null) {
            return if (retryAfter <= maxRetryAfterSeconds) retryAfter * 1_000 else null
        }
        return min(maxDelayMs, (baseDelayMs * 2.0.pow(attempt)).toLong())
    }
}
