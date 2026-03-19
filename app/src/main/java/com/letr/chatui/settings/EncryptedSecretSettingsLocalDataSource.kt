package com.letr.chatui.settings

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first

class EncryptedSecretSettingsLocalDataSource(
    private val sharedPreferences: SharedPreferences,
) : SecretSettingsLocalDataSource {
    override fun observeApiKey(): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == API_KEY_KEY) {
                trySend(readApiKey())
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(readApiKey())

        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override suspend fun getApiKey(): String? {
        return observeApiKey().first()
    }

    override suspend fun setApiKey(apiKey: String) {
        val normalized = ChatSettingsSanitizer.normalizeApiKey(apiKey)
        require(normalized.isNotEmpty()) { "API key must not be blank." }

        sharedPreferences.edit()
            .putString(API_KEY_KEY, normalized)
            .apply()
    }

    override suspend fun clearApiKey() {
        sharedPreferences.edit()
            .remove(API_KEY_KEY)
            .apply()
    }

    private fun readApiKey(): String? {
        return sharedPreferences.getString(API_KEY_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val API_KEY_KEY = "chat_settings_api_key"
    }
}
