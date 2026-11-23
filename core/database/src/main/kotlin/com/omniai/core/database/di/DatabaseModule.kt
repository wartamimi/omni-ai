package com.omniai.core.database.di

import android.content.Context
import androidx.room.Room
import com.omniai.core.database.OmniDatabase
import com.omniai.core.database.OmniMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OmniDatabase =
        Room.databaseBuilder(context, OmniDatabase::class.java, OmniDatabase.NAME)
            .addMigrations(*OmniMigrations.ALL)
            .build()

    @Provides fun providerDao(db: OmniDatabase) = db.providerDao()
    @Provides fun credentialDao(db: OmniDatabase) = db.credentialDao()
    @Provides fun modelDao(db: OmniDatabase) = db.modelDao()
    @Provides fun conversationDao(db: OmniDatabase) = db.conversationDao()
    @Provides fun messageDao(db: OmniDatabase) = db.messageDao()
    @Provides fun usageDao(db: OmniDatabase) = db.usageDao()
    @Provides fun promptDao(db: OmniDatabase) = db.promptDao()
    @Provides fun personaDao(db: OmniDatabase) = db.personaDao()
    @Provides fun toolDao(db: OmniDatabase) = db.toolDao()
    @Provides fun appSettingsDao(db: OmniDatabase) = db.appSettingsDao()
}
