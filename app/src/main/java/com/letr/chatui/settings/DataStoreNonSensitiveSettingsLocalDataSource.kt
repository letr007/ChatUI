package com.letr.chatui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.letr.chatui.data.model.NonSensitiveChatSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreNonSensitiveSettingsLocalDataSource(
    private val dataStore: DataStore<Preferences>,
) : NonSensitiveSettingsLocalDataSource {
    override fun observeSettings(): Flow<NonSensitiveChatSettings> {
        return dataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map(::toSettings)
    }

    override suspend fun getSettings(): NonSensitiveChatSettings {
        return observeSettings().first()
    }

    override suspend fun updateSettings(settings: NonSensitiveChatSettings) {
        val normalized = ChatSettingsSanitizer.normalizeNonSensitive(settings)
        dataStore.edit { preferences ->
            preferences[API_BASE_URL_KEY] = normalized.apiBaseUrl
            preferences[MODEL_ID_KEY] = normalized.modelId
            preferences[MODEL_LIST_KEY] = normalized.configuredModelIds.toSet()
        }
    }

    private fun toSettings(preferences: Preferences): NonSensitiveChatSettings {
        return NonSensitiveChatSettings(
            apiBaseUrl = preferences[API_BASE_URL_KEY].orEmpty(),
            modelId = preferences[MODEL_ID_KEY].orEmpty(),
            configuredModelIds = preferences[MODEL_LIST_KEY].orEmpty().toList().sorted(),
        )
    }

    private companion object {
        val API_BASE_URL_KEY = stringPreferencesKey("chat_settings_api_base_url")
        val MODEL_ID_KEY = stringPreferencesKey("chat_settings_model_id")
        val MODEL_LIST_KEY = stringSetPreferencesKey("chat_settings_model_ids")
    }
}
