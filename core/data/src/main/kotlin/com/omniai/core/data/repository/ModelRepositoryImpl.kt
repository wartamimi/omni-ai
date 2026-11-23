package com.omniai.core.data.repository

import androidx.room.withTransaction
import com.omniai.core.data.mapper.toEntity
import com.omniai.core.data.mapper.toModel
import com.omniai.core.database.OmniDatabase
import com.omniai.core.database.dao.ModelDao
import com.omniai.core.domain.repository.ModelRepository
import com.omniai.core.model.AIModel
import com.omniai.core.model.ModelKey
import com.omniai.core.model.ModelSource
import com.omniai.core.model.PricingSource
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    private val db: OmniDatabase,
    private val dao: ModelDao,
) : ModelRepository {
    override fun observeAll() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeForProvider(providerId: String) = dao.observeForProvider(providerId).map { l -> l.map { it.toModel() } }
    override suspend fun get(key: ModelKey) = dao.get(key.providerId, key.modelId)?.toModel()

    override suspend fun replaceDiscovered(providerId: String, models: List<AIModel>) {
        db.withTransaction {
            val existing = dao.forProvider(providerId).associate { it.modelId to it.toModel() }
            val merged = models.map { fresh ->
                val old = existing[fresh.modelId] ?: return@map fresh
                fresh.copy(
                    isFavorite = old.isFavorite,
                    lastUsedAt = old.lastUsedAt,
                    userCategories = old.userCategories,
                    // User-entered pricing wins over provider-reported pricing; unknown never overrides known.
                    pricing = if (old.pricing.source == PricingSource.USER || !fresh.pricing.isKnown) old.pricing else fresh.pricing,
                    capabilities = old.capabilities.mergedWith(fresh.capabilities),
                    contextWindow = fresh.contextWindow ?: old.contextWindow,
                    maxOutputTokens = fresh.maxOutputTokens ?: old.maxOutputTokens,
                    source = if (old.source == ModelSource.MANUAL) ModelSource.MANUAL else fresh.source,
                )
            }
            dao.upsertAll(merged.map { it.toEntity() })
            dao.deleteStaleDiscovered(providerId, merged.map { it.modelId }.ifEmpty { listOf("") })
        }
    }

    override suspend fun upsert(model: AIModel) = dao.upsert(model.toEntity())
    override suspend fun delete(key: ModelKey) = dao.delete(key.providerId, key.modelId)
    override suspend fun setFavorite(key: ModelKey, favorite: Boolean) = dao.setFavorite(key.providerId, key.modelId, favorite)
    override suspend fun markUsed(key: ModelKey, timestamp: Long) = dao.markUsed(key.providerId, key.modelId, timestamp)
}
