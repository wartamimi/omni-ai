package com.omniai.core.data.repository

import com.omniai.core.datastore.SettingsDataSource
import com.omniai.core.domain.repository.SettingsRepository
import com.omniai.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(private val source: SettingsDataSource) : SettingsRepository {
    override val settings: Flow<AppSettings> = source.settings
    override suspend fun current() = source.current()
    override suspend fun update(transform: (AppSettings) -> AppSettings) = source.update(transform)
}
