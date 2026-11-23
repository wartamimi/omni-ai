package com.omniai.core.domain.chat

import com.omniai.core.common.TokenEstimator
import com.omniai.core.domain.repository.ConversationSummary
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.ContextStrategy
import com.omniai.core.model.MessageRole
import com.omniai.core.model.MessageStatus
import com.omniai.core.model.PromptMessage

data class BuiltContext(
    val messages: List<PromptMessage>,
    val systemPrompt: String?,
    /** Messages not sent to the model. Always shown to the user, never silently dropped. */
    val omittedCount: Int,
    val usedSummary: Boolean,
    val estimatedInputTokens: Int,
    /** Older messages that should be summarized (SUMMARIZE strategy only). */
    val needsSummaryOf: List<ChatMessage> = emptyList(),
)

/**
 * Chooses which history goes to the model. Token counts are local estimates; the budget leaves room
 * for the expected output so the provider doesn't reject the request for context overflow.
 */
class ContextBuilder {

    fun build(
        history: List<ChatMessage>,
        systemPrompt: String?,
        strategy: ContextStrategy,
        maxMessages: Int,
        contextWindow: Int?,
        reservedOutputTokens: Int,
        summary: ConversationSummary?,
        imageLoader: (ChatMessage) -> List<com.omniai.core.model.ImagePart> = { emptyList() },
    ): BuiltContext {
        val usable = history.filter { it.status == MessageStatus.COMPLETE || it.role == MessageRole.USER }
            .filter { it.role != MessageRole.SYSTEM }
            .filterNot { it.role == MessageRole.ASSISTANT && it.content.isBlank() && it.toolCallsJson == null }
        val systemTokens = TokenEstimator.estimate(systemPrompt.orEmpty())

        if (strategy == ContextStrategy.FULL) {
            val msgs = usable.map { toPrompt(it, imageLoader) }
            return BuiltContext(msgs, systemPrompt, 0, false, systemTokens + estimate(usable))
        }

        val budget = contextWindow?.let { (it - reservedOutputTokens - systemTokens).coerceAtLeast(it / 4) } ?: Int.MAX_VALUE
        val kept = ArrayDeque<ChatMessage>()
        var tokens = 0
        for (msg in usable.asReversed()) {
            val cost = TokenEstimator.estimate(msg.content) + 4 + msg.attachments.sumOf { (it.extractedText?.let(TokenEstimator::estimate) ?: 0) }
            if (kept.size >= maxMessages || tokens + cost > budget) break
            kept.addFirst(msg)
            tokens += cost
        }
        // Never start the window with a dangling tool result or assistant tool call.
        while (kept.isNotEmpty() && (kept.first().role == MessageRole.TOOL)) {
            tokens -= TokenEstimator.estimate(kept.removeFirst().content) + 4
        }
        if (kept.isEmpty() && usable.isNotEmpty()) {
            // Always send at least the latest message; the provider will report overflow if it is too large.
            kept.addFirst(usable.last())
        }
        val omitted = usable.size - kept.size
        if (strategy == ContextStrategy.SUMMARIZE && omitted > 0) {
            val olderMessages = usable.take(omitted)
            val upToDate = summary != null && summary.coversUntilCreatedAt >= olderMessages.last().createdAt
            if (upToDate) {
                val sys = listOfNotNull(systemPrompt?.takeIf { it.isNotBlank() }, "Summary of the earlier conversation:\n${summary!!.summary}")
                    .joinToString("\n\n")
                return BuiltContext(kept.map { toPrompt(it, imageLoader) }, sys, omitted, true, TokenEstimator.estimate(sys) + tokens)
            }
            return BuiltContext(kept.map { toPrompt(it, imageLoader) }, systemPrompt, omitted, false, systemTokens + tokens, needsSummaryOf = olderMessages)
        }
        return BuiltContext(kept.map { toPrompt(it, imageLoader) }, systemPrompt, omitted, false, systemTokens + tokens)
    }

    private fun estimate(messages: List<ChatMessage>) = TokenEstimator.estimateMessages(messages.map { it.content })

    private fun toPrompt(m: ChatMessage, imageLoader: (ChatMessage) -> List<com.omniai.core.model.ImagePart>): PromptMessage {
        val docText = m.attachments.mapNotNull { a -> a.extractedText?.let { "\n\n--- Attached file: ${a.displayName} ---\n$it" } }.joinToString("")
        return PromptMessage(
            role = m.role,
            text = m.content + docText,
            images = if (m.role == MessageRole.USER) imageLoader(m) else emptyList(),
            toolCalls = ToolCallCodec.decode(m.toolCallsJson),
            toolCallId = m.toolCallId,
            toolName = if (m.role == MessageRole.TOOL) m.modelId else null,
        )
    }
}
