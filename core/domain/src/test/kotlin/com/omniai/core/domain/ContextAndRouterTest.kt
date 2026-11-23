package com.omniai.core.domain

import com.omniai.core.domain.chat.ContextBuilder
import com.omniai.core.domain.repository.ConversationSummary
import com.omniai.core.domain.router.ModelRouter
import com.omniai.core.domain.router.RoutingRule
import com.omniai.core.domain.router.RoutingTask
import com.omniai.core.domain.tools.CalculatorTool
import com.omniai.core.domain.tools.ToolArgumentException
import com.omniai.core.domain.tools.UnitConverterTool
import com.omniai.core.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContextAndRouterTest {
    private fun msg(i: Int, role: MessageRole = if (i % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT, text: String = "message $i") =
        ChatMessage("m$i", "c", role, text, createdAt = i.toLong())

    @Test fun `sliding window keeps the most recent messages and reports omitted`() {
        val history = (0 until 10).map { msg(it) }
        val ctx = ContextBuilder().build(history, "sys", ContextStrategy.SLIDING_WINDOW, 4, null, 100, null)
        assertEquals(4, ctx.messages.size)
        assertEquals(6, ctx.omittedCount)
        assertEquals("message 9", ctx.messages.last().text)
    }

    @Test fun `token budget limits window`() {
        val history = (0 until 6).map { msg(it, text = "x".repeat(400)) } // ~100 tokens each
        val ctx = ContextBuilder().build(history, null, ContextStrategy.SLIDING_WINDOW, 50, 1_000, 650, null)
        assertTrue(ctx.messages.size in 2..3, "kept ${ctx.messages.size}")
        assertTrue(ctx.omittedCount > 0)
    }

    @Test fun `full strategy never drops messages`() {
        val history = (0 until 50).map { msg(it) }
        val ctx = ContextBuilder().build(history, null, ContextStrategy.FULL, 4, 100, 50, null)
        assertEquals(50, ctx.messages.size)
        assertEquals(0, ctx.omittedCount)
    }

    @Test fun `summarize strategy requests summary then uses it`() {
        val history = (0 until 10).map { msg(it) }
        val first = ContextBuilder().build(history, "sys", ContextStrategy.SUMMARIZE, 4, null, 100, null)
        assertEquals(6, first.needsSummaryOf.size)
        val summary = ConversationSummary("c", "Earlier: greetings", 5, 6)
        val second = ContextBuilder().build(history, "sys", ContextStrategy.SUMMARIZE, 4, null, 100, summary)
        assertTrue(second.usedSummary)
        assertTrue(second.systemPrompt!!.contains("Earlier: greetings"))
    }

    @Test fun `router respects capabilities, cost and user rules without network`() {
        val providers = listOf(provider("a"), provider("b"))
        val cheap = AIModel("a", "cheap", "Cheap", pricing = ModelPricing(0.1, 0.2, PricingSource.USER))
        val pricey = AIModel("b", "pricey", "Pricey", pricing = ModelPricing(10.0, 30.0, PricingSource.USER))
        val noVision = AIModel("a", "text", "Text", capabilities = ModelCapabilities.Unknown.with(ModelCapability.VISION, CapabilityState.UNSUPPORTED))
        val router = ModelRouter()
        val cost = router.recommend(RoutingTask.GENERAL, RoutingMode.COST_OPTIMIZED, listOf(pricey, cheap), providers, emptyMap())
        assertEquals("cheap", cost.first().model.modelId)
        val vision = router.recommend(RoutingTask.IMAGE_UNDERSTANDING, RoutingMode.RECOMMENDED, listOf(noVision, cheap), providers, emptyMap())
        assertTrue(vision.none { it.model.modelId == "text" })
        val rule = router.recommend(RoutingTask.CODING, RoutingMode.USER_DEFINED, listOf(cheap, pricey), providers, emptyMap(), listOf(RoutingRule(RoutingTask.CODING, listOf("b::pricey"))))
        assertEquals("pricey", rule.single().model.modelId)
        assertTrue(router.recommend(RoutingTask.CODING, RoutingMode.MANUAL, listOf(cheap), providers, emptyMap()).isEmpty())
    }

    @Test fun `calculator parses safely`() = runTest {
        val calc = CalculatorTool()
        assertEquals("14", calc.execute("""{"expression":"2 + 3 * 4"}"""))
        assertEquals("20", calc.execute("""{"expression":"(2 + 3) * 4"}"""))
        assertEquals("1024", calc.execute("""{"expression":"2^10"}"""))
        assertEquals("-1.5", calc.execute("""{"expression":"-3/2"}"""))
        assertFailsWith<ToolArgumentException> { calc.execute("""{"expression":"1/0"}""") }
        assertFailsWith<ToolArgumentException> { calc.execute("""{"expression":"java.lang.System.exit(0)"}""") }
    }

    @Test fun `unit converter converts within dimension only`() = runTest {
        val u = UnitConverterTool()
        assertEquals("10 km = 6.213711922 mi", u.execute("""{"value":10,"from":"km","to":"mi"}"""))
        assertEquals("100 C = 212 F", u.execute("""{"value":100,"from":"c","to":"f"}"""))
        assertFailsWith<ToolArgumentException> { u.execute("""{"value":1,"from":"kg","to":"km"}""") }
    }
}
