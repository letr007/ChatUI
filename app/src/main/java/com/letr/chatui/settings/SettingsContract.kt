package com.letr.chatui.settings

import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import java.net.URI
import java.net.URISyntaxException

enum class SettingsStorageBackend {
    DATA_STORE,
    ENCRYPTED_LOCAL_STORAGE,
}

enum class SettingsStorageTarget {
    BASE_URL,
    MODEL,
    API_KEY,
}

data class SettingsStorageRule(
    val target: SettingsStorageTarget,
    val backend: SettingsStorageBackend,
    val plaintextPreferenceStyleAllowed: Boolean,
)

enum class InsecureBaseUrlOverride {
    DISALLOWED,
    DEBUG_ONLY,
}

sealed interface SettingsValidationIssue {
    data object MissingBaseUrl : SettingsValidationIssue

    data class InvalidBaseUrl(val rawValue: String) : SettingsValidationIssue

    data class InsecureHttpBaseUrl(val rawValue: String) : SettingsValidationIssue

    data object MissingApiKey : SettingsValidationIssue

    data object MissingModelId : SettingsValidationIssue
}

data class SettingsValidationResult(
    val issues: List<SettingsValidationIssue>,
) {
    val isValid: Boolean = issues.isEmpty()
}

object SettingsStorageContract {
    val rules: List<SettingsStorageRule> = listOf(
        SettingsStorageRule(
            target = SettingsStorageTarget.BASE_URL,
            backend = SettingsStorageBackend.DATA_STORE,
            plaintextPreferenceStyleAllowed = true,
        ),
        SettingsStorageRule(
            target = SettingsStorageTarget.MODEL,
            backend = SettingsStorageBackend.DATA_STORE,
            plaintextPreferenceStyleAllowed = true,
        ),
        SettingsStorageRule(
            target = SettingsStorageTarget.API_KEY,
            backend = SettingsStorageBackend.ENCRYPTED_LOCAL_STORAGE,
            plaintextPreferenceStyleAllowed = false,
        ),
    )

    fun backendFor(target: SettingsStorageTarget): SettingsStorageBackend {
        return rules.first { it.target == target }.backend
    }

    fun allowsPlaintextPreferenceStyleStorage(target: SettingsStorageTarget): Boolean {
        return rules.first { it.target == target }.plaintextPreferenceStyleAllowed
    }
}

object ApiKeyMaskingPolicy {
    fun toPersistedState(apiKey: String?): PersistedApiKeyState {
        if (apiKey.isNullOrBlank()) {
            return PersistedApiKeyState.Missing
        }

        return PersistedApiKeyState.Persisted(maskedValue = mask(apiKey))
    }

    fun mask(apiKey: String): String {
        val normalized = apiKey.trim()
        require(normalized.isNotEmpty()) { "API key must not be blank when masking a persisted secret." }

        return if (normalized.length <= 4) {
            "••••"
        } else {
            "••••${normalized.takeLast(4)}"
        }
    }
}

object ChatSettingsSanitizer {
    fun normalizeNonSensitive(settings: NonSensitiveChatSettings): NonSensitiveChatSettings {
        return NonSensitiveChatSettings(
            apiBaseUrl = settings.apiBaseUrl.trim(),
            modelId = settings.modelId.trim(),
        )
    }

    fun normalizeApiKey(apiKey: String): String {
        return apiKey.trim()
    }
}

object SettingsPrivacyPolicy {
    private val forbiddenFieldNames = setOf("apiKey")
    private val forbiddenHeaderNames = setOf(
        "authorization",
        "proxy-authorization",
        "x-api-key",
    )

    fun isSafeToLogField(fieldName: String): Boolean {
        return fieldName.trim() !in forbiddenFieldNames
    }

    fun isSafeToLogHeader(headerName: String): Boolean {
        return headerName.trim().lowercase() !in forbiddenHeaderNames
    }
}

object ChatSettingsValidator {
    fun validate(
        settings: NonSensitiveChatSettings,
        hasApiKey: Boolean,
        insecureBaseUrlOverride: InsecureBaseUrlOverride = InsecureBaseUrlOverride.DISALLOWED,
    ): SettingsValidationResult {
        val issues = buildList {
            validateBaseUrl(
                rawValue = settings.apiBaseUrl,
                insecureBaseUrlOverride = insecureBaseUrlOverride,
            )?.let(::add)

            if (!hasApiKey) {
                add(SettingsValidationIssue.MissingApiKey)
            }

            if (settings.modelId.isBlank()) {
                add(SettingsValidationIssue.MissingModelId)
            }
        }

        return SettingsValidationResult(issues = issues)
    }

    fun validateBaseUrl(
        rawValue: String,
        insecureBaseUrlOverride: InsecureBaseUrlOverride = InsecureBaseUrlOverride.DISALLOWED,
    ): SettingsValidationIssue? {
        if (rawValue.isBlank()) {
            return SettingsValidationIssue.MissingBaseUrl
        }

        val normalizedBaseUrl = try {
            URI(rawValue.trim())
        } catch (_: IllegalArgumentException) {
            return SettingsValidationIssue.InvalidBaseUrl(rawValue = rawValue)
        } catch (_: URISyntaxException) {
            return SettingsValidationIssue.InvalidBaseUrl(rawValue = rawValue)
        }

        if (!normalizedBaseUrl.isAbsolute || normalizedBaseUrl.host.isNullOrBlank()) {
            return SettingsValidationIssue.InvalidBaseUrl(rawValue = rawValue)
        }

        return when (normalizedBaseUrl.scheme?.lowercase()) {
            "https" -> null
            "http" -> {
                if (insecureBaseUrlOverride == InsecureBaseUrlOverride.DEBUG_ONLY) {
                    null
                } else {
                    SettingsValidationIssue.InsecureHttpBaseUrl(rawValue = rawValue)
                }
            }
            else -> SettingsValidationIssue.InvalidBaseUrl(rawValue = rawValue)
        }
    }

    fun mergeForDisplay(
        settings: NonSensitiveChatSettings,
        apiKey: String?,
    ): ChatSettings {
        return ChatSettings(
            apiBaseUrl = settings.apiBaseUrl,
            modelId = settings.modelId,
            apiKeyState = ApiKeyMaskingPolicy.toPersistedState(apiKey),
        )
    }
}
