package com.omniai.core.provider

import com.omniai.core.model.AIStreamEvent
import com.omniai.core.model.ApiProtocol
import com.omniai.core.model.AuthConfig
import com.omniai.core.model.CustomApiMapping
import com.omniai.core.model.GenerationParams
import com.omniai.core.provider.adapter.AnthropicAdapter
import com.omniai.core.provider.adapter.CustomRestAdapter
import com.omniai.core.provider.adapter.GeminiAdapter
import com.omniai.core.provider.adapter.OpenAiResponsesAdapter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OtherAdaptersTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.close() }

    private val base get() = server.url("/").toString()

    @Test fun `anthropic sends version header and parses stream`() = runTest {
        val sse = """
            event: message_start
            data: {"type":"message_start","message":{"usage":{"input_tokens":9}}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

            event: ping
            data: {"type":"ping"}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":4}}

            event: message_stop
            data: {"type":"message_stop"}

        """.trimIndent() + "\n"
        server.enqueue(MockResponse.Builder().code(200).body(sse).build())
        val cfg = TestSupport.config(base, ApiProtocol.ANTHROPIC_MESSAGES, AuthConfig.header("x-api-key"), modelsPath = "v1/models")
        val events = AnthropicAdapter(cfg, TestSupport.clients.clientFor(10), { TestSupport.KEY }).stream(TestSupport.request()).toList()
        assertEquals("Hi!", events.filterIsInstance<AIStreamEvent.TextDelta>().joinToString("") { it.text })
        val usage = events.filterIsInstance<AIStreamEvent.Usage>().last().usage
        assertEquals(9, usage.inputTokens)
        assertEquals(4, usage.outputTokens)
        val req = server.takeRequest()
        assertEquals("/v1/messages", req.url.encodedPath)
        assertEquals(AnthropicVersions.DEFAULT, req.headers["anthropic-version"])
        assertEquals(TestSupport.KEY, req.headers["x-api-key"])
        val body = req.body!!.utf8()
        assertTrue(body.contains("\"system\":\"Be brief.\""))
        assertTrue(body.contains("\"max_tokens\":100"))
        assertTrue(body.contains("\"temperature\""))
    }

    @Test fun `anthropic latest version drops sampling params`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"content":[{"type":"text","text":"ok"}],"usage":{"input_tokens":1,"output_tokens":1}}""").build())
        val cfg = TestSupport.config(base, ApiProtocol.ANTHROPIC_MESSAGES, AuthConfig.header("x-api-key"), apiVersion = AnthropicVersions.LATEST)
        val r = AnthropicAdapter(cfg, TestSupport.clients.clientFor(10), { TestSupport.KEY }).generate(TestSupport.request(params = GenerationParams(temperature = 0.3, topK = 5)))
        assertEquals("ok", r.text)
        val body = server.takeRequest().body!!.utf8()
        assertFalse(body.contains("temperature"))
        assertFalse(body.contains("top_k"))
    }

    @Test fun `gemini uses model path, api key header and sse alt`() = runTest {
        val sse = "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Sel\"}]}}]}\n\n" +
            "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"amat\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":3,\"candidatesTokenCount\":2}}\n\n"
        server.enqueue(MockResponse.Builder().code(200).body(sse).build())
        val cfg = TestSupport.config(base + "v1beta", ApiProtocol.GEMINI_GENERATE_CONTENT, AuthConfig.header("x-goog-api-key"))
        val events = GeminiAdapter(cfg, TestSupport.clients.clientFor(10), { TestSupport.KEY }).stream(TestSupport.request()).toList()
        assertEquals("Selamat", events.filterIsInstance<AIStreamEvent.TextDelta>().joinToString("") { it.text })
        val req = server.takeRequest()
        assertEquals("/v1beta/models/test-model:streamGenerateContent", req.url.encodedPath)
        assertEquals("sse", req.url.queryParameter("alt"))
        assertEquals(TestSupport.KEY, req.headers["x-goog-api-key"])
        assertTrue(req.body!!.utf8().contains("systemInstruction"))
    }

    @Test fun `gemini model listing filters generateContent models`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"models":[{"name":"models/gemini-x","displayName":"Gemini X","inputTokenLimit":1000000,"outputTokenLimit":8192,"supportedGenerationMethods":["generateContent","streamGenerateContent"]},{"name":"models/embed","supportedGenerationMethods":["embedContent"]}]}""",
        ).build())
        val cfg = TestSupport.config(base + "v1beta", ApiProtocol.GEMINI_GENERATE_CONTENT, AuthConfig.header("x-goog-api-key"))
        val models = GeminiAdapter(cfg, TestSupport.clients.clientFor(10), { TestSupport.KEY }).fetchModels()
        assertEquals(listOf("gemini-x"), models.map { it.modelId })
        assertEquals(1000000, models.single().contextWindow)
    }

    @Test fun `responses api sends store false and parses output text`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(
            """{"status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"Done"}]}],"usage":{"input_tokens":4,"output_tokens":1}}""",
        ).build())
        val cfg = TestSupport.config(base + "v1", ApiProtocol.OPENAI_RESPONSES)
        val r = OpenAiResponsesAdapter(cfg, TestSupport.clients.clientFor(10), { TestSupport.KEY }).generate(TestSupport.request())
        assertEquals("Done", r.text)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"store\":false"))
        assertTrue(body.contains("\"instructions\":\"Be brief.\""))
    }

    @Test fun `custom rest renders template and reads mapped path`() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body("""{"result":{"answer":"42"},"meta":{"in":7,"out":1}}""").build())
        val mapping = CustomApiMapping(
            endpointPath = "ask",
            bodyTemplate = """{"model": {{model}}, "question": {{prompt}}, "history": {{messages}}, "temp": {{temperature}}}""",
            responseTextPath = "result.answer",
            inputTokensPath = "meta.in",
            outputTokensPath = "meta.out",
        )
        val cfg = TestSupport.config(base + "api", ApiProtocol.CUSTOM_REST).copy(customMapping = mapping)
        val r = CustomRestAdapter(cfg, TestSupport.clients.clientFor(10), { TestSupport.KEY }).generate(TestSupport.request("What is \"6x7\"?"))
        assertEquals("42", r.text)
        assertEquals(7, r.usage?.inputTokens)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("\"question\":\"What is \\\"6x7\\\"?\""), body)
        assertTrue(body.contains("\"temp\":0.5"))
    }

    @Test fun `missing credential fails before any request`() = runTest {
        val cfg = TestSupport.config(base + "v1", ApiProtocol.OPENAI_RESPONSES)
        val events = OpenAiResponsesAdapter(cfg, TestSupport.clients.clientFor(10), { null }).stream(TestSupport.request()).toList()
        assertTrue((events.single() as AIStreamEvent.Failed).error is com.omniai.core.model.ProviderError.MissingCredential)
        assertEquals(0, server.requestCount)
    }

    @Test fun `catalog entries have https urls and docs`() {
        ProviderCatalog.definitions.filter { it.category != com.omniai.core.model.ProviderCategory.LOCAL }.forEach {
            assertTrue(it.defaultBaseUrl.startsWith("https://"), it.id)
            assertTrue(it.docsUrl.startsWith("https://"), it.id)
        }
        assertEquals(ProviderCatalog.definitions.size, ProviderCatalog.definitions.map { it.id }.toSet().size)
    }
}
