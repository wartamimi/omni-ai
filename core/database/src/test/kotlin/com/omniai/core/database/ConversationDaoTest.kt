package com.omniai.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.omniai.core.database.entity.ConversationEntity
import com.omniai.core.database.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationDaoTest {
    private lateinit var db: OmniDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OmniDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = db.close()

    private fun conversation(id: String, title: String, updated: Long, archived: Boolean = false, pinned: Boolean = false) =
        ConversationEntity(id, title, null, null, null, null, null, pinned, archived, updated, updated)

    private fun message(id: String, conversationId: String, content: String, at: Long) =
        MessageEntity(id, conversationId, "USER", content, null, "COMPLETE", null, null, null, null, null, null, null, null, null, null, at)

    @Test fun archivedConversationsAreHiddenUnlessRequested() = runTest {
        val dao = db.conversationDao()
        dao.upsert(conversation("a", "Active", 1))
        dao.upsert(conversation("b", "Old", 2, archived = true))
        assertEquals(listOf("a"), dao.observe(false).first().map { it.id })
        assertEquals(setOf("a", "b"), dao.observe(true).first().map { it.id }.toSet())
    }

    @Test fun pinnedConversationsSortFirst() = runTest {
        val dao = db.conversationDao()
        dao.upsert(conversation("a", "Newer", 10))
        dao.upsert(conversation("b", "Pinned", 1, pinned = true))
        assertEquals(listOf("b", "a"), dao.observe(false).first().map { it.id })
    }

    @Test fun searchMatchesMessageContentAndEscapesWildcards() = runTest {
        val dao = db.conversationDao()
        dao.upsert(conversation("a", "Trip", 1))
        dao.upsert(conversation("b", "Other", 2))
        db.messageDao().upsert(message("m1", "a", "Flights to Bandar Seri Begawan cost 100%", 1))
        db.messageDao().upsert(message("m2", "b", "Nothing relevant", 2))

        val hits = dao.search("bandar").first()
        assertEquals(listOf("a"), hits.map { it.conversation.id })
        assertTrue(hits.single().snippet!!.contains("Bandar"))

        // "%" must be matched literally (the repository escapes it as "\%").
        assertEquals(listOf("a"), dao.search("100\\%").first().map { it.conversation.id })
        assertTrue(dao.search("zz\\%zz").first().isEmpty())
    }

    @Test fun deletingConversationCascadesToMessages() = runTest {
        db.conversationDao().upsert(conversation("a", "Trip", 1))
        db.messageDao().upsert(message("m1", "a", "hello", 1))
        db.conversationDao().delete("a")
        assertTrue(db.messageDao().forConversation("a").isEmpty())
    }
}
