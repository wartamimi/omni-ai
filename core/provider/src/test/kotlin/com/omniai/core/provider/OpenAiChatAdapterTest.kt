package com.omniai.core.provider

import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.ProviderError
import com.omniai.core.provider.adapter.OpenAiChatAdapter
import com.omniai.core.provider.adapter.OpenAiChatOptions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class OpenAiChatAdapterTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.close() }

    private fun adapter(options: OpenAiChatOptions = OpenAiChatOptions()) = OpenAiChatAdapter(
        TestSupport.config(server.url("/v1").toString(), ApiProtocol.OPENAI_CHAT_COMPLETIONS),
        TestSupport.clients.clientFor(10),
        { TestSupport.KEY },
        options,
    )

    @Test fun `generate sends bearer auth and parses content and usage`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"choices":[{"message":{"role":"assistant","content":"Hi there"},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":3}}""",
        ).build())
        val response = adapter(OpenAiChatOptions(maxTokensField = "max_completion_tokens")).generate(TestSupport.request())
        assertEquals("Hi there", response.text)
        assertEquals(11, response.usage?.inputTokens)
        assertEquals(3, response.usage?.outputTokens)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.url.encodedPath)
        assertEquals("Bearer ${TestSupport.KEY}", recorded.headers["Authorization"])
        val body = Json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
        assertEquals("test-model", body["model"]!!.jsonPrimitive.content)
        assertEquals("100", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertEquals("system", body["messages"].toString().let { if (it.contains("\"system\"")) "system" else "none" })
    }

    @Test fun `stream emits deltas usage and completion`() = runTest {
        val sse = buildString {
            append("data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"Hel\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"lo\"},\"finish_reason\":\"stop\"}]}\n\n")
            append("data: {\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2}}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Type", "text/event-stream").body(sse).build())
        val events = adapter(OpenAiChatOptions(streamUsageOption = true)).stream(TestSupport.request()).toList()
        val text = events.filterIsInstance<AIStreamEvent.TextDelta>().joinToString("") { it.text }
        assertEquals("Hello", text)
        assertEquals(5, events.filterIsInstance<AIStreamEvent.Usage>().single().usage.inputTokens)
        assertIs<AIStreamEvent.Completed>(events.last())
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("include_usage"))
    }

    @Test fun `http errors are mapped and redacted`() = runTest {
        server.enqueue(MockResponse.Builder().code(401).body("""{"error":{"message":"Incorrect API key provided: ${TestSupport.KEY}"}}""").build())
        try {
            adapter().generate(TestSupport.request())
            fail("expected exception")
        } catch (e: ProviderException) {
            assertIs<ProviderError.Unauthorized>(e.error)
            assertTrue(!e.error.message.contains(TestSupport.KEY))
        }
    }

    @Test fun `stream failure is emitted as event`() = runTest {
        server.enqueue(MockResponse.Builder().code(400).body("""{"error":{"message":"maximum context length exceeded"}}""").build())
        val events = adapter().stream(TestSupport.request()).toList()
        val failed = events.single() as AIStreamEvent.Failed
        assertIs<ProviderError.ContextOverflow>(failed.error)
    }

    @Test fun `rate limit with short retry-after is retried once`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).addHeader("Retry-After", "0").body("{}").build())
        server.enqueue(MockResponse.Builder().code(200).body("""{"choices":[{"message":{"content":"ok"}}]}""").build())
        assertEquals("ok", adapter().generate(TestSupport.request()).text)
        assertEquals(2, server.requestCount)
    }

    @Test fun `fetch models parses openrouter style pricing`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"data":[{"id":"a/b","name":"Model B","context_length":128000,"pricing":{"prompt":"0.000002","completion":"0.000008"},"architecture":{"input_modalities":["text","image"]},"supported_parameters":["tools"]}]}""",
        ).build())
        val models = adapter().fetchModels()
        val m = models.single()
        assertEquals(128000, m.contextWindow)
        assertEquals(2.0, m.pricing.inputPerMillion!!, 1e-9)
        assertEquals(8.0, m.pricing.outputPerMillion!!, 1e-9)
        assertTrue(m.capabilities.supports(com.omniai.core.model.ModelCapability.VISION))
    }
}
