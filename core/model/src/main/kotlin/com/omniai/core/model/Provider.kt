package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderCategory(val label: String) {
    OFFICIAL("Official providers"),
    GATEWAY("API gateways"),
    CUSTOM("Custom"),
    LOCAL("Local"),
}

@Serializable
enum class SupportLevel(val label: String) {
    /** Built-in template whose endpoints come from the provider's official docs. */
    SUPPORTED("Supported"),
    /** Works through a generic protocol; some features may differ. */
    COMPATIBLE("Compatible protocol"),
    /** Not usable in this version; [ProviderDefinition.notes] explains why. */
    UNSUPPORTED("Not supported yet"),
}

/**
 * Static, documented description of a known provider (the "catalog").
 * The user creates [ProviderConfig] instances from these templates.
 */
@Serializable
data class ProviderDefinition(
    val id: String,
    val displayName: String,
    val category: ProviderCategory,
    val protocol: ApiProtocol,
    val defaultBaseUrl: String,
    val auth: AuthConfig,
    val chatPath: String? = null,
    val modelsPath: String? = null,
    val apiVersion: String? = null,
    val defaultHeaders: List<HeaderEntry> = emptyList(),
    val docsUrl: String,
    val supportLevel: SupportLevel,
    val notes: String = "",
    val supportsStreaming: Boolean = true,
    val keyPrefixHint: String? = null,
)

/** A user-configured provider instance (one per account/endpoint). */
@Serializable
data class ProviderConfig(
    val id: String,
    val definitionId: String?,
    val name: String,
    val description: String = "",
    val iconEmoji: String? = null,
    val category: ProviderCategory,
    val protocol: ApiProtocol,
    val baseUrl: String,
    val auth: AuthConfig,
    val chatPath: String? = null,
    val modelsPath: String? = null,
    val apiVersion: String? = null,
    val customHeaders: List<HeaderEntry> = emptyList(),
    /** Extra top-level JSON fields merged into every request body (validated JSON object). */
    val customParamsJson: String? = null,
    val timeoutSeconds: Int = 120,
    val streamingEnabled: Boolean = true,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val defaultModelId: String? = null,
    val customMapping: CustomApiMapping? = null,
    val maxRetries: Int = 2,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Request/response mapping for [ApiProtocol.CUSTOM_REST].
 * The body template is JSON with placeholders: {{model}}, {{messages}}, {{prompt}},
 * {{system}}, {{temperature}}, {{max_tokens}}. Response paths use dot/bracket notation,
 * e.g. `choices[0].message.content`.
 */
@Serializable
data class CustomApiMapping(
    val httpMethod: String = "POST",
    val endpointPath: String,
    val queryParams: List<HeaderEntry> = emptyList(),
    val bodyTemplate: String,
    val responseTextPath: String,
    val inputTokensPath: String? = null,
    val outputTokensPath: String? = null,
    val errorMessagePath: String? = null,
    /** SSE streaming: JSON path of the text delta inside each `data:` event. Null = no streaming. */
    val streamDeltaPath: String? = null,
    val streamDoneSentinel: String? = "[DONE]",
)

@Serializable
enum class CredentialStatus { UNVERIFIED, VALID, INVALID, ERROR }

/** Metadata for a stored API key. The secret itself lives only in encrypted storage. */
@Serializable
data class CredentialInfo(
    val id: String,
    val providerId: String,
    val label: String,
    /** Masked hint such as `sk-…3f9a`. Never the full key. */
    val maskedHint: String,
    val status: CredentialStatus,
    val isActive: Boolean,
    val createdAt: Long,
    val lastValidatedAt: Long?,
)

@Serializable
data class ProviderHealthEvent(
    val providerId: String,
    val timestamp: Long,
    val success: Boolean,
    val httpStatus: Int?,
    val latencyMs: Long?,
    val errorKind: String?,
)
