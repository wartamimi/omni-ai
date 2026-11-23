package com.omniai.core.data.repository

import androidx.room.withTransaction
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.data.mapper.toEntity
import com.omniai.core.data.mapper.toModel
import com.omniai.core.database.OmniDatabase
import com.omniai.core.database.dao.ConversationDao
import com.omniai.core.database.dao.MessageDao
import com.omniai.core.domain.repository.ConversationRepository
import com.omniai.core.domain.repository.ConversationSearchHit
import com.omniai.core.domain.repository.ConversationSummary
import com.omniai.core.model.ChatMessage
import com.omniai.core.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val db: OmniDatabase,
    private val conversations: ConversationDao,
    private val messages: MessageDao,
) : ConversationRepository {

    override fun observeConversations(includeArchived: Boolean) = conversations.observe(includeArchived).map { l -> l.map { it.toModel() } }

    override fun search(query: String): Flow<List<ConversationSearchHit>> {
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return conversations.search(escaped).map { rows ->
            rows.map { row -> ConversationSearchHit(row.conversation.toModel(), row.snippet?.let { snippet(it, query) }) }
        }
    }

    override fun observeConversation(id: String) = conversations.observeOne(id).map { it?.toModel() }.distinctUntilChanged()

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        combine(messages.observe(conversationId), messages.observeAttachments(conversationId)) { msgs, atts ->
            val byMessage = atts.groupBy { it.messageId }
            msgs.map { m -> m.toModel(byMessage[m.id].orEmpty().map { it.toModel() }) }
        }

    override suspend fun get(id: String) = conversations.get(id)?.toModel()

    override suspend fun messages(conversationId: String): List<ChatMessage> {
        val atts = messages.attachmentsFor(conversationId).groupBy { it.messageId }
        return messages.forConversation(conversationId).map { m -> m.toModel(atts[m.id].orEmpty().map { it.toModel() }) }
    }

    override suspend fun upsert(conversation: Conversation) {
        conversations.upsert(conversation.toEntity(conversations.get(conversation.id)))
    }

    override suspend fun delete(id: String) {
        val files = messages.attachmentsFor(id).map { it.localPath }
        conversations.delete(id)
        files.forEach { File(it).delete() }
    }

    override suspend fun deleteAll() {
        val files = messages.allAttachments().map { it.localPath }
        conversations.deleteAll()
        files.forEach { File(it).delete() }
    }

    override suspend fun upsertMessage(message: ChatMessage) =
        messages.upsertWithAttachments(message.toEntity(), message.attachments.map { it.toEntity(message.id) })

    override suspend fun deleteMessage(id: String) = messages.delete(id)
    override suspend fun deleteMessagesFrom(conversationId: String, createdAt: Long) = messages.deleteFrom(conversationId, createdAt)

    override suspend fun duplicate(id: String, newTitle: String): Conversation? = db.withTransaction {
        val source = conversations.get(id)?.toModel() ?: return@withTransaction null
        val now = Clock.System.now()
        val copy = source.copy(id = Ids.new(), title = newTitle, isPinned = false, createdAt = now, updatedAt = now)
        conversations.upsert(copy.toEntity())
        val atts = messages.attachmentsFor(id).groupBy { it.messageId }
        messages.forConversation(id).forEach { m ->
            val newId = Ids.new()
            messages.upsertWithAttachments(
                m.copy(id = newId, conversationId = copy.id),
                atts[m.id].orEmpty().map { it.copy(id = Ids.new(), messageId = newId) },
            )
        }
        copy
    }

    override suspend fun summary(conversationId: String): ConversationSummary? {
        val c = conversations.get(conversationId) ?: return null
        val text = c.summary ?: return null
        return ConversationSummary(conversationId, text, c.summaryUntil ?: 0, c.summaryCount ?: 0)
    }

    override suspend fun saveSummary(summary: ConversationSummary) =
        conversations.saveSummary(summary.conversationId, summary.summary, summary.coversUntilCreatedAt, summary.messageCount)

    private fun snippet(text: String, query: String): String {
        val i = text.indexOf(query, ignoreCase = true)
        if (i < 0) return text.take(120)
        val start = (i - 40).coerceAtLeast(0)
        return (if (start > 0) "…" else "") + text.substring(start, (i + query.length + 80).coerceAtMost(text.length))
    }
}
