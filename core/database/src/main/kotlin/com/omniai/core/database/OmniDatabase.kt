package com.omniai.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omniai.core.database.dao.AppSettingsDao
import com.omniai.core.database.dao.ConversationDao
import com.omniai.core.database.dao.CredentialDao
import com.omniai.core.database.dao.MessageDao
import com.omniai.core.database.dao.ModelDao
import com.omniai.core.database.dao.PersonaDao
import com.omniai.core.database.dao.PromptDao
import com.omniai.core.database.dao.ProviderDao
import com.omniai.core.database.dao.ToolDao
import com.omniai.core.database.dao.UsageDao
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

/**
 * Version history (add a Migration in [OmniMigrations] for every bump, never destructive):
 *  1 — initial schema.
 */
@Database(
    entities = [
        ProviderEntity::class, CredentialEntity::class, ModelEntity::class, ConversationEntity::class,
        MessageEntity::class, AttachmentEntity::class, UsageEntity::class, PromptEntity::class,
        PersonaEntity::class, ToolEntity::class, ToolLogEntity::class, ProviderHealthEntity::class,
        AppSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao
    abstract fun credentialDao(): CredentialDao
    abstract fun modelDao(): ModelDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun usageDao(): UsageDao
    abstract fun promptDao(): PromptDao
    abstract fun personaDao(): PersonaDao
    abstract fun toolDao(): ToolDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val NAME = "omniai.db"
    }
}

/** Explicit migrations. The database is never rebuilt destructively, so user history is never lost on upgrade. */
object OmniMigrations {
    val ALL: Array<androidx.room.migration.Migration> = emptyArray()
}
