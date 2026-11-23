package com.omniai.core.common

import com.omniai.core.model.CurrencyCode
import com.omniai.core.model.CurrencySettings
import com.omniai.core.model.ModelPricing
import com.omniai.core.model.PricingSource
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalculationsTest {
    @Test fun `cost uses per million pricing`() {
        val pricing = ModelPricing(2.5, 10.0, PricingSource.USER)
        assertEquals(0.0125, CostCalculator.estimateUsd(pricing, 1_000, 1_000)!!, 1e-9)
    }

    @Test fun `cost is null when needed price is unknown`() {
        assertNull(CostCalculator.estimateUsd(ModelPricing.Unknown, 10, 10))
        assertNull(CostCalculator.estimateUsd(ModelPricing(1.0, null, PricingSource.USER), 10, 10))
    }

    @Test fun `converts to BND only with user rate`() {
        val noRate = CurrencySettings(display = CurrencyCode.BND)
        assertNull(CostCalculator.convert(1.0, CurrencyCode.BND, noRate))
        assertEquals("$1.00", CostCalculator.formatInDisplayCurrency(1.0, noRate))
        val withRate = noRate.copy(usdRates = mapOf(CurrencyCode.BND to 1.35))
        assertEquals("B$1.35", CostCalculator.formatInDisplayCurrency(1.0, withRate))
        assertEquals("B$0.0014", CostCalculator.formatInDisplayCurrency(0.001, withRate))
    }

    @Test fun `token estimate is roughly four chars per token`() {
        assertEquals(3, TokenEstimator.estimate("hello world!"))
        assertEquals(0, TokenEstimator.estimate(""))
        assertTrue(TokenEstimator.estimate("你好世界") >= 4)
    }

    @Test fun `prompt variables fill and report missing`() {
        val t = "Translate from {{source_language}} to {{ target_language }}: {{text}}"
        assertEquals(listOf("source_language", "target_language", "text"), PromptTemplateEngine.variables(t))
        val filled = PromptTemplateEngine.fill(t, mapOf("source_language" to "Malay", "target_language" to "English"))
        assertEquals("Translate from Malay to English: {{text}}", filled)
        assertEquals(listOf("text"), PromptTemplateEngine.missing(t, mapOf("source_language" to "a", "target_language" to "b")))
    }

    @Test fun `json path reads nested values`() {
        val root = Json.parseToJsonElement("""{"choices":[{"message":{"content":"hi"}}],"usage":{"in":12}}""")
        assertEquals("hi", JsonPath.readString(root, "choices[0].message.content"))
        assertEquals(12, JsonPath.readInt(root, "usage.in"))
        assertNull(JsonPath.readString(root, "choices[1].message.content"))
        assertTrue(JsonPath.isValid("choices[0].delta.content"))
    }
}
