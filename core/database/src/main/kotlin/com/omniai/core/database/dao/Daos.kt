package com.omniai.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.omniai.core.database.entity.AppSettingsEntity
import com.omniai.core.database.entity.AttachmentEntity
import com.omniai.core.database.entity.ConversationEntity
import com.omniai.core.database.entity.CredentialEntity
import com.omniai.core.database.entity.MessageEntity
import com.omniai.core.database.entity.ModelEntity
import com.omniai.core.database.entity.PersonaEntity
import com.omniai.core.database.entity.PromptEntity
import com.omniai.core.database.entity.ProviderEntity
import com.omniai.core.database.entity.ProviderHealthEntity
import com.omniai.core.database.entity.ToolEntity
import com.omniai.core.database.entity.ToolLogEntity
import com.omniai.core.database.entity.UsageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY isDefault DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY isDefault DESC, name COLLATE NOCASE")
    suspend fun all(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Upsert
    suspend fun upsert(entity: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM providers")
    suspend fun deleteAll()

    @Query("UPDATE providers SET isDefault = (id = :id)")
    suspend fun setDefault(id: String)

    @Query("UPDATE providers SET enabled = :enabled, updatedAt = :now WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, now: Long)

    @Insert
    suspend fun insertHealth(entity: ProviderHealthEntity)

    @Query("SELECT * FROM provider_health WHERE providerId = :providerId ORDER BY timestamp DESC LIMIT 50")
    fun observeHealth(providerId: String): Flow<List<ProviderHealthEntity>>

    @Query("SELECT * FROM provider_health WHERE timestamp > :since ORDER BY timestamp ASC")
    suspend fun healthSince(since: Long): List<ProviderHealthEntity>

    @Query("DELETE FROM provider_health WHERE timestamp < :before")
    suspend fun pruneHealth(before: Long)
}

@Dao
interface CredentialDao {
    @Query("SELECT * FROM credentials WHERE providerId = :providerId ORDER BY createdAt")
    fun observeForProvider(providerId: String): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials ORDER BY createdAt")
    fun observeAll(): Flow<List<CredentialEntity>>

    @Query("SELECT * FROM credentials WHERE providerId = :providerId")
    suspend fun forProvider(providerId: String): List<CredentialEntity>

    @Query("SELECT * FROM credentials WHERE id = :id")
    suspend fun get(id: String): CredentialEntity?

    @Query("SELECT * FROM credentials WHERE providerId = :providerId AND isActive = 1 LIMIT 1")
    suspend fun active(providerId: String): CredentialEntity?

    @Query("SELECT id FROM credentials")
    suspend fun allIds(): List<String>

    @Upsert
    suspend fun upsert(entity: CredentialEntity)

    @Query("DELETE FROM credentials WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM credentials")
    suspend fun deleteAll()

    @Transaction
    suspend fun setActive(providerId: String, credentialId: String) {
        clearActive(providerId)
        markActive(credentialId)
    }

    @Query("UPDATE credentials SET isActive = 0 WHERE providerId = :providerId")
    suspend fun clearActive(providerId: String)

    @Query("UPDATE credentials SET isActive = 1 WHERE id = :id")
    suspend fun markActive(id: String)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models ORDER BY isFavorite DESC, displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY isFavorite DESC, displayName COLLATE NOCASE")
    fun observeForProvider(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId")
    suspend fun forProvider(providerId: String): List<ModelEntity>

    @Query("SELECT * FROM models WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun get(providerId: String, modelId: String): ModelEntity?

    @Upsert
    suspend fun upsert(entity: ModelEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ModelEntity>)

    @Query("DELETE FROM models WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun delete(providerId: String, modelId: String)

    @Query("DELETE FROM models WHERE providerId = :providerId AND source = 'DISCOVERED' AND isFavorite = 0 AND modelId NOT IN (:keep)")
    suspend fun deleteStaleDiscovered(providerId: String, keep: List<String>)

    @Query("UPDATE models SET isFavorite = :favorite WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun setFavorite(providerId: String, modelId: String, favorite: Boolean)

    @Query("UPDATE models SET lastUsedAt = :ts WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun markUsed(providerId: String, modelId: String, ts: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 OR :includeArchived ORDER BY isPinned DESC, updatedAt DESC")
    fun observe(includeArchived: Boolean): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeOne(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun all(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(entity: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query(
        """
        SELECT c.*, (SELECT m.content FROM messages m WHERE m.conversationId = c.id AND m.content LIKE '%' || :q || '%' ESCAPE '\' ORDER BY m.createdAt DESC LIMIT 1) AS snippet
        FROM conversations c
        WHERE c.title LIKE '%' || :q || '%' ESCAPE '\'
           OR EXISTS (SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.content LIKE '%' || :q || '%' ESCAPE '\')
        ORDER BY c.updatedAt DESC LIMIT 200
        """,
    )
    fun search(q: String): Flow<List<ConversationSearchRow>>

    @Query("UPDATE conversations SET summary = :summary, summaryUntil = :until, summaryCount = :count WHERE id = :id")
    suspend fun saveSummary(id: String, summary: String, until: Long, count: Int)
}

data class ConversationSearchRow(
    @androidx.room.Embedded val conversation: ConversationEntity,
    val snippet: String?,
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observe(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun forConversation(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM attachments WHERE messageId IN (SELECT id FROM messages WHERE conversationId = :conversationId)")
    fun observeAttachments(conversationId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageId IN (SELECT id FROM messages WHERE conversationId = :conversationId)")
    suspend fun attachmentsFor(conversationId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Upsert
    suspend fun upsert(entity: MessageEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MessageEntity>)

    @Upsert
    suspend fun upsertAttachments(entities: List<AttachmentEntity>)

    @Transaction
    suspend fun upsertWithAttachments(message: MessageEntity, attachments: List<AttachmentEntity>) {
        upsert(message)
        if (attachments.isNotEmpty()) upsertAttachments(attachments)
    }

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND createdAt >= :createdAt")
    suspend fun deleteFrom(conversationId: String, createdAt: Long)
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun observeSince(since: Long): Flow<List<UsageEntity>>

    @Query("SELECT * FROM usage ORDER BY timestamp ASC")
    suspend fun all(): List<UsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UsageEntity)

    @Query("DELETE FROM usage")
    suspend fun deleteAll()
}

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun get(id: String): PromptEntity?

    @Query("SELECT COUNT(*) FROM prompts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: PromptEntity)

    @Upsert
    suspend fun upsertAll(entities: List<PromptEntity>)

    @Query("DELETE FROM prompts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PersonaDao {
    @Query("SELECT * FROM personas ORDER BY isBuiltIn ASC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonaEntity>>

    @Query("SELECT * FROM personas WHERE id = :id")
    suspend fun get(id: String): PersonaEntity?

    @Query("SELECT COUNT(*) FROM personas")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(entity: PersonaEntity)

    @Upsert
    suspend fun upsertAll(entities: List<PersonaEntity>)

    @Query("DELETE FROM personas WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ToolDao {
    @Query("SELECT * FROM tools ORDER BY name")
    fun observeAll(): Flow<List<ToolEntity>>

    @Query("SELECT * FROM tools WHERE name = :name")
    suspend fun get(name: String): ToolEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entities: List<ToolEntity>)

    @Query("UPDATE tools SET permission = :permission WHERE name = :name")
    suspend fun setPermission(name: String, permission: String)

    @Insert
    suspend fun insertLog(entity: ToolLogEntity)

    @Query("SELECT * FROM tool_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeLogs(): Flow<List<ToolLogEntity>>

    @Query("DELETE FROM tool_logs")
    suspend fun clearLogs()
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): AppSettingsEntity?

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<AppSettingsEntity?>

    @Upsert
    suspend fun put(entity: AppSettingsEntity)
}
