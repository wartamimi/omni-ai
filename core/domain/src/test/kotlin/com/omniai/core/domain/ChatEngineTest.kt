package com.omniai.core.domain

import com.omniai.core.domain.chat.ChatEngine
import com.omniai.core.domain.tools.BuiltInTools
import com.omniai.core.domain.tools.ToolExecutor
import com.omniai.core.model.*
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatEngineTest {
    private val pricing = ModelPricing(1.0, 2.0, PricingSource.USER)

    private fun TestScope.engine(
        adapter: ScriptedAdapter,
        settings: FakeSettings = FakeSettings(),
        conversations: FakeConversations = FakeConversations(),
        usage: FakeUsage = FakeUsage(),
        providers: FakeProviders = FakeProviders(provider()),
    ) = ChatEngine(
        conversations = conversations, providers = providers,
        models = FakeModels(AIModel("p1", "m1", "M1", pricing = pricing)), personas = FakePersonas(), settings = settings,
        adapters = FakeAdapterProvider(adapter), usage = usage, toolExecutor = ToolExecutor(BuiltInTools.all(), FakeTools()),
        imageLoader = { null }, scope = backgroundScope,
    )

    @Test fun `streams answer, persists it and records provider usage and cost`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = ScriptedAdapter(ArrayDeque(listOf(listOf(
            AIStreamEvent.Started, AIStreamEvent.TextDelta("Hel"), AIStreamEvent.TextDelta("lo"),
            AIStreamEvent.Usage(TokenUsage(1000, 500, true)), AIStreamEvent.Completed("stop"),
        ))))
        val convs = FakeConversations()
        val usage = FakeUsage()
        val e = engine(adapter, conversations = convs, usage = usage)
        val c = e.createConversation()
        assertEquals("p1", c.providerId)
        assertEquals("m1", c.modelId)
        e.send(c.id, "Hi there")!!.join()
        val messages = convs.messages(c.id)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), messages.map { it.role })
        val answer = messages.last()
        assertEquals("Hello", answer.content)
        assertEquals(MessageStatus.COMPLETE, answer.status)
        assertTrue(answer.usage!!.providerReported)
        assertEquals(0.002, usage.records.single().estimatedCostUsd!!, 1e-9)
        assertEquals("Hi there", convs.get(c.id)!!.title)
        assertTrue(adapter.requests.single().params.stream)
    }

    @Test fun `failed stream keeps partial text and marks error`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = ScriptedAdapter(ArrayDeque(listOf(listOf(
            AIStreamEvent.TextDelta("Partial "), AIStreamEvent.Failed(ProviderError.Server("boom", 502)),
        ))))
        val convs = FakeConversations()
        val e = engine(adapter, conversations = convs)
        val c = e.createConversation()
        e.send(c.id, "Question")!!.join()
        val answer = convs.messages(c.id).last()
        assertEquals(MessageStatus.ERROR, answer.status)
        assertEquals("Partial ", answer.content)
        assertTrue(answer.errorMessage!!.contains("boom"))
        assertFalse(answer.usage!!.providerReported, "falls back to labelled estimates")
    }

    @Test fun `regenerate replaces last answer`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = ScriptedAdapter(ArrayDeque(listOf(
            listOf(AIStreamEvent.TextDelta("First"), AIStreamEvent.Completed(null)),
            listOf(AIStreamEvent.TextDelta("Second"), AIStreamEvent.Completed(null)),
        )))
        val convs = FakeConversations()
        val e = engine(adapter, conversations = convs)
        val c = e.createConversation()
        e.send(c.id, "Q")!!.join()
        e.regenerate(c.id)!!.join()
        val msgs = convs.messages(c.id)
        assertEquals(2, msgs.size)
        assertEquals("Second", msgs.last().content)
    }

    @Test fun `non streaming path used when disabled in settings`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = ScriptedAdapter(ArrayDeque(listOf(listOf(AIStreamEvent.TextDelta("Whole")))))
        val convs = FakeConversations()
        val e = engine(adapter, settings = FakeSettings(AppSettings(streaming = false)), conversations = convs)
        val c = e.createConversation()
        e.send(c.id, "Q")!!.join()
        assertFalse(adapter.requests.single().params.stream)
        assertEquals("Whole", convs.messages(c.id).last().content)
    }

    @Test fun `tool calls run locally and the model is asked again`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = ScriptedAdapter(ArrayDeque(listOf(
            listOf(AIStreamEvent.ToolCallDelta(0, "call_1", "calculator", "{\"expression\":"), AIStreamEvent.ToolCallDelta(0, null, null, "\"6*7\"}"), AIStreamEvent.Completed("tool_calls")),
            listOf(AIStreamEvent.TextDelta("The answer is 42."), AIStreamEvent.Completed("stop")),
        )))
        val convs = FakeConversations()
        val e = engine(adapter, settings = FakeSettings(AppSettings(toolsEnabled = true)), conversations = convs)
        val c = e.createConversation()
        e.send(c.id, "What is 6*7?")!!.join()
        advanceUntilIdle()
        val msgs = convs.messages(c.id)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL, MessageRole.ASSISTANT), msgs.map { it.role })
        assertEquals("42", msgs[2].content)
        assertEquals("The answer is 42.", msgs.last().content)
        val second = adapter.requests[1]
        assertEquals(MessageRole.TOOL, second.messages.last().role)
        assertEquals("call_1", second.messages.last().toolCallId)
    }

    @Test fun `disabled provider produces a clear error message`() = runTest(UnconfinedTestDispatcher()) {
        val adapter = ScriptedAdapter(ArrayDeque())
        val convs = FakeConversations()
        val e = engine(adapter, conversations = convs, providers = FakeProviders(provider(enabled = false)))
        val c = e.createConversation(providerId = "p1", modelId = "m1")
        e.send(c.id, "Q")!!.join()
        assertTrue(convs.messages(c.id).last().errorMessage!!.contains("disabled"))
        assertTrue(adapter.requests.isEmpty())
    }
}
