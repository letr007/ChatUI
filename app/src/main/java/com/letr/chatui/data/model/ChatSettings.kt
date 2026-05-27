package com.letr.chatui.data.model

sealed interface PersistedApiKeyState {
    data object Missing : PersistedApiKeyState

    data class Persisted(val maskedValue: String) : PersistedApiKeyState
}

enum class ThemeColorOption {
    DEFAULT,
    BLUE,
    GREEN,
    PURPLE,
    AMBER,
}

data class NonSensitiveChatSettings(
    val apiBaseUrl: String = "",
    val modelId: String = "",
    val configuredModelIds: List<String> = emptyList(),
    val themeColor: ThemeColorOption = ThemeColorOption.DEFAULT,
)

data class ChatSettings(
    val apiBaseUrl: String = "",
    val modelId: String = "",
    val configuredModelIds: List<String> = emptyList(),
    val apiKeyState: PersistedApiKeyState = PersistedApiKeyState.Missing,
    val themeColor: ThemeColorOption = ThemeColorOption.DEFAULT,
)
