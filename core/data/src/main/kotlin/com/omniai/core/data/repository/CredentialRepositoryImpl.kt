package com.omniai.core.data.repository

import androidx.room.withTransaction
import com.omniai.core.common.Clock
import com.omniai.core.common.Ids
import com.omniai.core.common.Redactor
import com.omniai.core.data.mapper.toModel
import com.omniai.core.database.OmniDatabase
import com.omniai.core.database.dao.CredentialDao
import com.omniai.core.database.entity.CredentialEntity
import com.omniai.core.domain.repository.CredentialRepository
import com.omniai.core.model.CredentialInfo
import com.omniai.core.model.CredentialStatus
import com.omniai.core.security.CredentialVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialRepositoryImpl @Inject constructor(
    private val db: OmniDatabase,
    private val dao: CredentialDao,
    private val vault: CredentialVault,
) : CredentialRepository {
    private val clock = Clock.System

    override fun observeForProvider(providerId: String): Flow<List<CredentialInfo>> = dao.observeForProvider(providerId).map { l -> l.map { it.toModel() } }
    override fun observeAll(): Flow<List<CredentialInfo>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    override suspend fun add(providerId: String, label: String, secret: String): CredentialInfo {
        val clean = secret.trim()
        require(clean.isNotEmpty()) { "API key is empty" }
        val id = Ids.new()
        vault.write(id, clean)
        val entity = CredentialEntity(
            id = id, providerId = providerId, label = label.ifBlank { "Key" }, maskedHint = Redactor.maskKey(clean),
            status = CredentialStatus.UNVERIFIED.name, isActive = false, createdAt = clock.now(), lastValidatedAt = null,
        )
        db.withTransaction {
            val noActive = dao.active(providerId) == null
            dao.upsert(entity.copy(isActive = noActive))
        }
        return dao.get(id)!!.toModel()
    }

    override suspend fun replaceSecret(credentialId: String, secret: String) {
        val existing = dao.get(credentialId) ?: return
        vault.write(credentialId, secret.trim())
        dao.upsert(existing.copy(maskedHint = Redactor.maskKey(secret), status = CredentialStatus.UNVERIFIED.name, lastValidatedAt = null))
    }

    override suspend fun rename(credentialId: String, label: String) {
        dao.get(credentialId)?.let { dao.upsert(it.copy(label = label.ifBlank { it.label })) }
    }

    override suspend fun delete(credentialId: String) {
        val existing = dao.get(credentialId) ?: return
        db.withTransaction {
            dao.delete(credentialId)
            if (existing.isActive) dao.forProvider(existing.providerId).firstOrNull()?.let { dao.markActive(it.id) }
        }
        vault.delete(credentialId)
    }

    override suspend fun setActive(credentialId: String) {
        val existing = dao.get(credentialId) ?: return
        dao.setActive(existing.providerId, credentialId)
    }

    override suspend fun updateStatus(credentialId: String, status: CredentialStatus) {
        dao.get(credentialId)?.let { dao.upsert(it.copy(status = status.name, lastValidatedAt = clock.now())) }
    }

    override suspend fun activeSecret(providerId: String): String? = dao.active(providerId)?.let { vault.read(it.id) }
    override suspend fun activeCredential(providerId: String): CredentialInfo? = dao.active(providerId)?.toModel()

    override suspend fun deleteAll() {
        dao.deleteAll()
        vault.deleteAll()
    }
}
