package com.omniai.core.common

/**
 * Removes credentials from any text before it is logged, displayed in error details or exported.
 * Applied defensively: callers also avoid passing secrets in the first place.
 */
object Redactor {
    private const val MASK = "[REDACTED]"

    private val patterns: List<Regex> = listOf(
        // Authorization headers / bearer tokens
        Regex("(?i)(authorization\\s*[:=]\\s*\"?\\s*(?:bearer|basic|token)?\\s*)[^\\s\",}]+"),
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]{8,}"),
        // Common API key headers / JSON fields / query params
        Regex("(?i)((?:x-api-key|x-goog-api-key|api[-_]?key|apikey|access[-_]?token|secret|password)\"?\\s*[:=]\\s*\"?)[^\\s\"&,}]+"),
        Regex("(?i)([?&](?:key|api_key|apikey|token|access_token)=)[^&\\s\"]+"),
        // Well-known key formats
        Regex("\\bsk-(?:ant-|proj-|or-v1-)?[A-Za-z0-9_-]{16,}"),
        Regex("\\bAIza[0-9A-Za-z_-]{30,}"),
        Regex("\\bgsk_[A-Za-z0-9]{20,}"),
        Regex("\\bxai-[A-Za-z0-9]{20,}"),
        Regex("\\bhf_[A-Za-z0-9]{20,}"),
        Regex("\\bnvapi-[A-Za-z0-9_-]{20,}"),
        Regex("\\br8_[A-Za-z0-9]{20,}"),
    )

    fun redact(text: String?, extraSecrets: Collection<String> = emptyList()): String {
        if (text.isNullOrEmpty()) return text.orEmpty()
        var out: String = text
        for (secret in extraSecrets) {
            if (secret.length >= 4) out = out.replace(secret, MASK)
        }
        for (pattern in patterns) {
            out = pattern.replace(out) { match ->
                if (match.groupValues.size > 1 && match.groupValues[1].isNotEmpty()) match.groupValues[1] + MASK else MASK
            }
        }
        return out
    }

    /** `sk-proj-abc…wxyz` style hint: never reveals more than the first 3 and last 4 characters. */
    fun maskKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "•".repeat(trimmed.length.coerceAtLeast(4))
        val prefixLen = when {
            trimmed.startsWith("sk-") -> 3
            else -> 2
        }
        return trimmed.take(prefixLen) + "…" + trimmed.takeLast(4)
    }

    /** Header names whose values must never be logged. */
    val sensitiveHeaders: Set<String> = setOf(
        "authorization", "proxy-authorization", "x-api-key", "x-goog-api-key", "api-key", "cookie", "set-cookie",
    )
}
