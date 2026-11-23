package com.omniai.core.data.repository

import com.omniai.core.data.mapper.toEntity
import com.omniai.core.data.mapper.toModel
import com.omniai.core.data.seed.DefaultContent
import com.omniai.core.database.dao.PersonaDao
import com.omniai.core.database.dao.PromptDao
import com.omniai.core.database.dao.ToolDao
import com.omniai.core.database.dao.UsageDao
import com.omniai.core.database.entity.ToolEntity
import com.omniai.core.domain.repository.PersonaRepository
import com.omniai.core.domain.repository.PromptRepository
import com.omniai.core.domain.repository.ToolRepository
import com.omniai.core.domain.repository.UsageRepository
import com.omniai.core.model.Persona
import com.omniai.core.model.PromptTemplate
import com.omniai.core.model.ToolConfig
import com.omniai.core.model.ToolExecutionLog
import com.omniai.core.model.ToolPermission
import com.omniai.core.model.UsageRecord
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(private val dao: UsageDao) : UsageRepository {
    override fun observeSince(since: Long) = dao.observeSince(since).map { l -> l.map { it.toModel() } }
    override suspend fun record(record: UsageRecord) = dao.insert(record.toEntity())
    override suspend fun all() = dao.all().map { it.toModel() }
    override suspend fun deleteAll() = dao.deleteAll()
}

@Singleton
class PromptRepositoryImpl @Inject constructor(private val dao: PromptDao) : PromptRepository {
    override fun observeAll() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun get(id: String) = dao.get(id)?.toModel()
    override suspend fun upsert(prompt: PromptTemplate) = dao.upsert(prompt.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) dao.upsertAll(DefaultContent.prompts().map { it.toEntity() })
    }
}

@Singleton
class PersonaRepositoryImpl @Inject constructor(private val dao: PersonaDao) : PersonaRepository {
    override fun observeAll() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override suspend fun get(id: String) = dao.get(id)?.toModel()
    override suspend fun upsert(persona: Persona) = dao.upsert(persona.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun seedDefaultsIfEmpty() {
        if (dao.count() == 0) dao.upsertAll(DefaultContent.personas().map { it.toEntity() })
    }
}

@Singleton
class ToolRepositoryImpl @Inject constructor(private val dao: ToolDao) : ToolRepository {
    override fun observeTools() = dao.observeAll().map { l -> l.map { it.toModel() } }
    override fun observeLogs() = dao.observeLogs().map { l -> l.map { it.toModel() } }
    override suspend fun permission(toolName: String): ToolPermission =
        dao.get(toolName)?.toModel()?.permission ?: ToolPermission.ASK
    override suspend fun setPermission(toolName: String, permission: ToolPermission) = dao.setPermission(toolName, permission.name)
    override suspend fun log(entry: ToolExecutionLog) = dao.insertLog(entry.toEntity())
    override suspend fun seedDefaultsIfEmpty(defaults: List<ToolConfig>) =
        dao.insertIfMissing(defaults.map { ToolEntity(it.name, it.description, it.isBuiltIn, it.permission.name, it.configJson) })
    override suspend fun clearLogs() = dao.clearLogs()
}
