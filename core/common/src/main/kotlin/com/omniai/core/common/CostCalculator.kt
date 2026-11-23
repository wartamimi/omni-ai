package com.omniai.core.common

import com.omniai.core.model.CurrencyCode
import com.omniai.core.model.CurrencySettings
import com.omniai.core.model.ModelPricing
import java.math.BigDecimal
import java.math.RoundingMode

/** Estimated cost math. Results are estimates; the provider's invoice is authoritative. */
object CostCalculator {

    /** Cost in USD, or null when pricing for the used direction is unknown. */
    fun estimateUsd(pricing: ModelPricing, inputTokens: Int, outputTokens: Int): Double? {
        if (!pricing.isKnown) return null
        val inPrice = pricing.inputPerMillion
        val outPrice = pricing.outputPerMillion
        if (inputTokens > 0 && inPrice == null) return null
        if (outputTokens > 0 && outPrice == null) return null
        val million = BigDecimal(1_000_000)
        val inCost = BigDecimal.valueOf(inPrice ?: 0.0).multiply(BigDecimal(inputTokens)).divide(million, 10, RoundingMode.HALF_UP)
        val outCost = BigDecimal.valueOf(outPrice ?: 0.0).multiply(BigDecimal(outputTokens)).divide(million, 10, RoundingMode.HALF_UP)
        return inCost.add(outCost).toDouble()
    }

    /** Converts USD with a user-entered rate. Returns null if no rate was entered for [target]. */
    fun convert(usd: Double, target: CurrencyCode, settings: CurrencySettings): Double? {
        val rate = settings.rateFor(target) ?: return null
        return BigDecimal.valueOf(usd).multiply(BigDecimal.valueOf(rate)).setScale(6, RoundingMode.HALF_UP).toDouble()
    }

    fun format(amount: Double, currency: CurrencyCode): String {
        val decimals = when {
            currency == CurrencyCode.JPY || currency == CurrencyCode.IDR -> 0
            amount != 0.0 && kotlin.math.abs(amount) < 0.01 -> 4
            else -> 2
        }
        val value = BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
        return "${currency.symbol}$value"
    }

    /** Formats a USD amount in the user's display currency, falling back to USD if no rate is set. */
    fun formatInDisplayCurrency(usd: Double, settings: CurrencySettings): String {
        val converted = convert(usd, settings.display, settings)
        return if (converted != null) format(converted, settings.display) else format(usd, CurrencyCode.USD)
    }
}
