package com.letr.chatui.data.model

sealed interface PersistedApiKeyState {
    data object Missing : PersistedApiKeyState

    data class Persisted(val maskedValue: String) : PersistedApiKeyState
}

data class NonSensitiveChatSettings(
    val apiBaseUrl: String,
    val modelId: String,
)

data class ChatSettings(
    val apiBaseUrl: String,
    val modelId: String,
    val apiKeyState: PersistedApiKeyState = PersistedApiKeyState.Missing,
)
