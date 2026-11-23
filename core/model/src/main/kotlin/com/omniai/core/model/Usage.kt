package com.omniai.core.model

import kotlinx.serialization.Serializable

@Serializable
data class UsageRecord(
    val id: String,
    val providerId: String,
    val providerName: String,
    val modelId: String,
    val conversationId: String?,
    val inputTokens: Int,
    val outputTokens: Int,
    /** True if the provider response reported the token counts. */
    val providerReported: Boolean,
    /** Estimated cost in USD from known pricing; null when pricing is unknown. */
    val estimatedCostUsd: Double?,
    val success: Boolean,
    val httpStatus: Int?,
    val errorKind: String?,
    val latencyMs: Long?,
    val timestamp: Long,
    /** Manual adjustment entered by the user (not measured). */
    val isManualAdjustment: Boolean = false,
)

@Serializable
enum class CurrencyCode(val symbol: String, val displayName: String) {
    USD("$", "US Dollar"),
    BND("B$", "Brunei Dollar"),
    SGD("S$", "Singapore Dollar"),
    MYR("RM", "Malaysian Ringgit"),
    IDR("Rp", "Indonesian Rupiah"),
    EUR("€", "Euro"),
    GBP("£", "British Pound"),
    JPY("¥", "Japanese Yen"),
}

/** Exchange rates are entered by the user; OmniAI does not fetch rates from the internet. */
@Serializable
data class CurrencySettings(
    val display: CurrencyCode = CurrencyCode.USD,
    /** Units of currency per 1 USD, entered by the user. */
    val usdRates: Map<CurrencyCode, Double> = emptyMap(),
) {
    fun rateFor(code: CurrencyCode): Double? = if (code == CurrencyCode.USD) 1.0 else usdRates[code]
}
