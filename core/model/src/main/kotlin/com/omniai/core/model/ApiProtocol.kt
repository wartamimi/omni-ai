package com.omniai.core.model

import kotlinx.serialization.Serializable

/**
 * Wire protocols OmniAI can speak. A provider is bound to exactly one protocol;
 * "OpenAI-compatible" services still get their own base URL, paths and auth config.
 */
@Serializable
enum class ApiProtocol(val displayName: String) {
    OPENAI_CHAT_COMPLETIONS("OpenAI-compatible Chat Completions"),
    OPENAI_RESPONSES("OpenAI Responses API"),
    ANTHROPIC_MESSAGES("Anthropic Messages API"),
    GEMINI_GENERATE_CONTENT("Gemini generateContent"),
    CUSTOM_REST("Custom REST JSON (mapped)"),
}

/** How the API key is attached to a request. */
@Serializable
enum class AuthType {
    /** `Authorization: Bearer <key>` */
    BEARER,
    /** A named header carries the raw key, e.g. `x-api-key` or `x-goog-api-key`. */
    HEADER,
    /** A named query parameter carries the key. Discouraged: URLs end up in logs. */
    QUERY,
    /** No authentication (local servers). */
    NONE,
}

@Serializable
data class AuthConfig(
    val type: AuthType = AuthType.BEARER,
    /** Header or query parameter name for [AuthType.HEADER] / [AuthType.QUERY]. */
    val name: String? = null,
) {
    companion object {
        val Bearer = AuthConfig(AuthType.BEARER)
        val None = AuthConfig(AuthType.NONE)
        fun header(name: String) = AuthConfig(AuthType.HEADER, name)
    }
}

@Serializable
data class HeaderEntry(val name: String, val value: String)
