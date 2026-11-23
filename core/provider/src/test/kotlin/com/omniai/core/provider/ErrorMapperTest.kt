package com.omniai.core.provider

import com.omniai.core.model.ProviderError
import com.omniai.core.provider.http.ErrorMapper
import com.omniai.core.provider.http.RetryPolicy
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ErrorMapperTest {
    @Test fun `maps statuses`() {
        assertIs<ProviderError.Unauthorized>(ErrorMapper.fromHttp(401, """{"error":{"message":"Invalid key sk-abcdefghijklmnopqrstuvwxyz"}}""", null))
        val rl = ErrorMapper.fromHttp(429, "{}", "12")
        assertIs<ProviderError.RateLimited>(rl)
        assertEquals(12, rl.retryAfterSeconds)
        assertIs<ProviderError.Server>(ErrorMapper.fromHttp(503, null, null))
        assertIs<ProviderError.PayloadTooLarge>(ErrorMapper.fromHttp(413, null, null))
        assertIs<ProviderError.Unprocessable>(ErrorMapper.fromHttp(422, null, null))
        assertIs<ProviderError.RequestTimeout>(ErrorMapper.fromHttp(408, null, null))
    }

    @Test fun `detects context overflow and credits`() {
        assertIs<ProviderError.ContextOverflow>(ErrorMapper.fromHttp(400, """{"error":{"code":"context_length_exceeded","message":"This model's maximum context length is 8192"}}""", null))
        assertIs<ProviderError.InsufficientCredits>(ErrorMapper.fromHttp(402, """{"error":{"message":"Insufficient credits"}}""", null))
        assertIs<ProviderError.InsufficientCredits>(ErrorMapper.fromHttp(429, """{"error":{"type":"insufficient_quota","message":"You exceeded your current quota"}}""", null))
    }

    @Test fun `error message is redacted`() {
        val e = ErrorMapper.fromHttp(401, """{"error":{"message":"Incorrect API key provided: sk-proj-abcdefghijklmnopqrstuvwxyz"}}""", null)
        assertFalse(e.message.contains("abcdefghijklmnop"))
    }

    @Test fun `maps exceptions`() {
        assertIs<ProviderError.Network>(ErrorMapper.fromException(UnknownHostException("x")))
        assertIs<ProviderError.Network>(ErrorMapper.fromException(SSLHandshakeException("bad cert")))
    }

    @Test fun `retry policy never retries once a response started or on client errors`() {
        val p = RetryPolicy(maxRetries = 2)
        assertNull(p.delayBeforeRetry(ProviderError.RateLimited("x", 1), 0, responseStarted = true))
        assertNull(p.delayBeforeRetry(ProviderError.BadRequest("x"), 0, false))
        assertNull(p.delayBeforeRetry(ProviderError.Server("x", 500), 0, false))
        assertEquals(1000, p.delayBeforeRetry(ProviderError.RateLimited("x", 1), 0, false))
        assertNull(p.delayBeforeRetry(ProviderError.RateLimited("x", 120), 0, false), "long Retry-After is surfaced, not retried")
        assertNull(p.delayBeforeRetry(ProviderError.RateLimited("x", 1), 2, false), "max retries reached")
    }
}
