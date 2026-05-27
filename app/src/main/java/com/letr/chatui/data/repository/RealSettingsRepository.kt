package com.letr.chatui.data.repository

import com.letr.chatui.data.model.ActiveChatRuntimeConfig
import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfig
import com.letr.chatui.settings.ApiKeyMaskingPolicy
import com.letr.chatui.settings.ChatSettingsSanitizer
import com.letr.chatui.settings.ChatSettingsValidator
import com.letr.chatui.settings.InsecureBaseUrlOverride
import com.letr.chatui.settings.NonSensitiveSettingsLocalDataSource
import com.letr.chatui.settings.SecretSettingsLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RealSettingsRepository(
    private val nonSensitiveLocalDataSource: NonSensitiveSettingsLocalDataSource,
    private val secretLocalDataSource: SecretSettingsLocalDataSource,
    private val insecureBaseUrlOverride: InsecureBaseUrlOverride = InsecureBaseUrlOverride.DISALLOWED,
) : SettingsRepository,
    SecretSettingsRepository,
    ActiveChatConfigSource {

    override fun observeChatSettings(): Flow<ChatSettings> {
        return combine(
            nonSensitiveLocalDataSource.observeSettings(),
            observePersistedApiKeyState(),
        ) { settings, apiKeyState ->
            ChatSettings(
                apiBaseUrl = settings.apiBaseUrl,
                modelId = settings.modelId,
                configuredModelIds = settings.configuredModelIds,
                apiKeyState = apiKeyState,
                themeColor = settings.themeColor,
            )
        }
    }

    override suspend fun getChatSettings(): ChatSettings {
        return observeChatSettings().first()
    }

    override suspend fun updateNonSensitiveSettings(settings: NonSensitiveChatSettings) {
        val normalized = ChatSettingsSanitizer.normalizeNonSensitive(settings)
        val hasApiKey = !secretLocalDataSource.getApiKey().isNullOrBlank()
        val baseUrlIssue = ChatSettingsValidator.validateBaseUrl(
            rawValue = normalized.apiBaseUrl,
            insecureBaseUrlOverride = insecureBaseUrlOverride,
        )
        require(baseUrlIssue == null) { "Invalid base URL: ${normalized.apiBaseUrl}" }
        require(normalized.modelId.isNotBlank()) { "Model ID must not be blank." }
        ChatSettingsValidator.validate(
            settings = normalized,
            hasApiKey = hasApiKey,
            insecureBaseUrlOverride = insecureBaseUrlOverride,
        )
        nonSensitiveLocalDataSource.updateSettings(normalized)
    }

    override fun observePersistedApiKeyState(): Flow<PersistedApiKeyState> {
        return secretLocalDataSource.observeApiKey().map(ApiKeyMaskingPolicy::toPersistedState)
    }

    override suspend fun getApiKey(): String? {
        return secretLocalDataSource.getApiKey()
    }

    override suspend fun setApiKey(apiKey: String) {
        secretLocalDataSource.setApiKey(apiKey)
    }

    override suspend fun clearApiKey() {
        secretLocalDataSource.clearApiKey()
    }

    override fun observeActiveConfig(): Flow<ActiveChatRuntimeConfig> {
        return combine(
            nonSensitiveLocalDataSource.observeSettings(),
            secretLocalDataSource.observeApiKey(),
        ) { settings, apiKey ->
            ActiveChatRuntimeConfig(
                apiBaseUrl = settings.apiBaseUrl,
                apiKey = apiKey,
                modelId = settings.modelId,
            )
        }
    }

    override suspend fun getActiveConfig(): ActiveChatRuntimeConfig {
        return observeActiveConfig().first()
    }

    suspend fun getOpenAiProviderConfig(): OpenAiProviderConfig {
        val activeConfig = getActiveConfig()
        return OpenAiProviderConfig(
            baseUrl = activeConfig.apiBaseUrl,
            apiKey = activeConfig.apiKey,
            modelId = activeConfig.modelId,
        )
    }
}
