package com.omniai.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omniai.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists [AppSettings] as one JSON document in Preferences DataStore. Unknown/missing fields fall
 * back to defaults, so adding settings in later versions needs no migration. No secrets are stored here.
 */
@Singleton
class SettingsDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true }
    private val key = stringPreferencesKey("app_settings_v1")

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[key]) }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(decode(prefs[key]))
            prefs[key] = json.encodeToString(AppSettings.serializer(), sanitize(updated))
        }
    }

    fun decode(raw: String?): AppSettings =
        raw?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()

    private fun sanitize(s: AppSettings) = s.copy(
        textScale = s.textScale.coerceIn(0.8f, 1.6f),
        maxContextMessages = s.maxContextMessages.coerceIn(2, 500),
        requestTimeoutSeconds = s.requestTimeoutSeconds.coerceIn(10, 600),
        maxRetries = s.maxRetries.coerceIn(0, 5),
        compareLimit = s.compareLimit.coerceIn(2, 12),
        maxToolRounds = s.maxToolRounds.coerceIn(0, 10),
    )
}
