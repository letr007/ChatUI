package com.letr.chatui.data.repository

import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeChatSettings(): Flow<ChatSettings>

    suspend fun getChatSettings(): ChatSettings

    suspend fun updateNonSensitiveSettings(settings: NonSensitiveChatSettings)
}

interface SecretSettingsRepository {
    fun observePersistedApiKeyState(): Flow<PersistedApiKeyState>

    suspend fun getApiKey(): String?

    suspend fun setApiKey(apiKey: String)

    suspend fun clearApiKey()
}
