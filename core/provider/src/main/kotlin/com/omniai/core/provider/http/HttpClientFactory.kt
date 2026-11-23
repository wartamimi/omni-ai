package com.omniai.core.provider.http

import com.omniai.core.common.Redactor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Debug log sink. Receives already-redacted lines only. */
fun interface ApiLogSink {
    fun log(line: String)
}

/**
 * Builds OkHttp clients. Certificate validation is never relaxed. A single base client is
 * shared (connection pool), and per-provider timeouts are derived with newBuilder().
 */
class HttpClientFactory(
    allowLocalNetwork: () -> Boolean,
    private val logSink: ApiLogSink? = null,
    loggingEnabled: () -> Boolean = { false },
) {
    private val base: OkHttpClient = OkHttpClient.Builder()
        .dns(SafeDns(allowLocalNetwork))
        .followRedirects(false) // redirects could leak auth headers to another host
        .followSslRedirects(false)
        .retryOnConnectionFailure(false) // retries are decided by RetryPolicy, never silently
        .connectTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(RedactingLogInterceptor({ logSink }, loggingEnabled))
        .build()

    fun clientFor(timeoutSeconds: Int): OkHttpClient = base.newBuilder()
        .readTimeout(timeoutSeconds.toLong().coerceIn(5, 600), TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS) // streams may legitimately run long; read timeout guards stalls
        .build()
}

/** Logs method, redacted URL, status and timing. Never headers or bodies. */
class RedactingLogInterceptor(
    private val sink: () -> ApiLogSink?,
    private val enabled: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val logger = sink()
        if (logger == null || !enabled()) return chain.proceed(request)
        val start = System.nanoTime()
        val url = Redactor.redact(request.url.newBuilder().query(null).build().toString())
        return try {
            val response = chain.proceed(request)
            logger.log("${request.method} $url -> ${response.code} (${(System.nanoTime() - start) / 1_000_000} ms)")
            response
        } catch (e: Exception) {
            logger.log("${request.method} $url -> ${e::class.java.simpleName}: ${Redactor.redact(e.message)}")
            throw e
        }
    }
}
