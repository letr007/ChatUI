package com.letr.chatui.settings

import kotlinx.coroutines.flow.Flow

interface SecretSettingsLocalDataSource {
    fun observeApiKey(): Flow<String?>

    suspend fun getApiKey(): String?

    suspend fun setApiKey(apiKey: String)

    suspend fun clearApiKey()
}
