package com.omniai.core.data.repository

import androidx.room.withTransaction
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.data.mapper.toEntity
import com.omniai.core.data.mapper.toModel
import com.omniai.core.database.OmniDatabase
import com.omniai.core.database.dao.ProviderDao
import com.omniai.core.domain.repository.ProviderRepository
import com.omniai.core.model.ProviderConfig
import com.omniai.core.model.ProviderHealthEvent
import com.omniai.core.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepositoryImpl @Inject constructor(
    private val db: OmniDatabase,
    private val dao: ProviderDao,
    private val vault: CredentialVault,
) : ProviderRepository {
    private val clock = Clock.System

    override fun observeProviders(): Flow<List<ProviderConfig>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    override fun observeHealth(providerId: String) = dao.observeHealth(providerId).map { list -> list.map { it.toModel() } }
    override suspend fun get(id: String) = dao.get(id)?.toModel()
    override suspend fun all() = dao.all().map { it.toModel() }

    override suspend fun upsert(config: ProviderConfig) {
        db.withTransaction {
            val first = dao.all().none { it.id != config.id }
            dao.upsert(config.copy(isDefault = config.isDefault || first, updatedAt = clock.now()).toEntity())
            if (config.isDefault) dao.setDefault(config.id)
        }
    }

    override suspend fun delete(id: String) {
        val credentialIds = db.credentialDao().forProvider(id).map { it.id }
        db.withTransaction {
            val wasDefault = dao.get(id)?.isDefault == true
            dao.delete(id) // cascades credentials, models, health
            if (wasDefault) dao.all().firstOrNull()?.let { dao.setDefault(it.id) }
        }
        credentialIds.forEach { vault.delete(it) }
    }

    override suspend fun setDefault(id: String) = dao.setDefault(id)
    override suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled, clock.now())

    /** Copies configuration only. API keys are not duplicated; the copy needs its own key. */
    override suspend fun duplicate(id: String): ProviderConfig? {
        val source = get(id) ?: return null
        val now = clock.now()
        val copy = source.copy(id = Ids.new(), name = "${source.name} (copy)", isDefault = false, createdAt = now, updatedAt = now)
        dao.upsert(copy.toEntity())
        return copy
    }

    override suspend fun recordHealth(event: ProviderHealthEvent) {
        if (dao.get(event.providerId) != null) dao.insertHealth(event.toEntity())
    }

    override suspend fun recentHealth(limitPerProvider: Int): Map<String, List<ProviderHealthEvent>> =
        dao.healthSince(clock.now() - 30L * 24 * 3600 * 1000).map { it.toModel() }
            .groupBy { it.providerId }.mapValues { (_, v) -> v.takeLast(limitPerProvider) }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}
