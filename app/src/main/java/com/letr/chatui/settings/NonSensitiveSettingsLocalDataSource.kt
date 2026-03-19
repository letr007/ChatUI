package com.letr.chatui.settings

import com.letr.chatui.data.model.NonSensitiveChatSettings
import kotlinx.coroutines.flow.Flow

interface NonSensitiveSettingsLocalDataSource {
    fun observeSettings(): Flow<NonSensitiveChatSettings>

    suspend fun getSettings(): NonSensitiveChatSettings

    suspend fun updateSettings(settings: NonSensitiveChatSettings)
}
