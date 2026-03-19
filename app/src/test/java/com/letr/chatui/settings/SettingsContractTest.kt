package com.letr.chatui.settings

import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsContractTest {
    @Test
    fun `storage contract keeps base url and model non-sensitive while api key is encrypted only`() {
        assertEquals(
            SettingsStorageBackend.DATA_STORE,
            SettingsStorageContract.backendFor(SettingsStorageTarget.BASE_URL),
        )
        assertEquals(
            SettingsStorageBackend.DATA_STORE,
            SettingsStorageContract.backendFor(SettingsStorageTarget.MODEL),
        )
        assertEquals(
            SettingsStorageBackend.ENCRYPTED_LOCAL_STORAGE,
            SettingsStorageContract.backendFor(SettingsStorageTarget.API_KEY),
        )
        assertFalse(
            SettingsStorageContract.allowsPlaintextPreferenceStyleStorage(
                SettingsStorageTarget.API_KEY,
            )
        )
    }

    @Test
    fun `base url validator rejects abc and accepts the locked https example`() {
        assertEquals(
            SettingsValidationIssue.InvalidBaseUrl(rawValue = "abc"),
            ChatSettingsValidator.validateBaseUrl(rawValue = "abc"),
        )
        assertEquals(
            SettingsValidationIssue.InvalidBaseUrl(rawValue = ":"),
            ChatSettingsValidator.validateBaseUrl(rawValue = ":"),
        )
        assertEquals(
            null,
            ChatSettingsValidator.validateBaseUrl(rawValue = "https://api.example.com/v1/"),
        )
    }

    @Test
    fun `http urls require an explicit debug only override`() {
        assertEquals(
            SettingsValidationIssue.InsecureHttpBaseUrl(rawValue = "http://localhost:8080/v1/"),
            ChatSettingsValidator.validateBaseUrl(rawValue = "http://localhost:8080/v1/"),
        )
        assertEquals(
            null,
            ChatSettingsValidator.validateBaseUrl(
                rawValue = "http://localhost:8080/v1/",
                insecureBaseUrlOverride = InsecureBaseUrlOverride.DEBUG_ONLY,
            ),
        )
    }

    @Test
    fun `configuration validator reports missing api key and model`() {
        val validation = ChatSettingsValidator.validate(
            settings = NonSensitiveChatSettings(
                apiBaseUrl = "https://api.example.com/v1/",
                modelId = "   ",
            ),
            hasApiKey = false,
        )

        assertFalse(validation.isValid)
        assertTrue(validation.issues.contains(SettingsValidationIssue.MissingApiKey))
        assertTrue(validation.issues.contains(SettingsValidationIssue.MissingModelId))
    }

    @Test
    fun `persisted api key display is masked after persistence`() {
        assertEquals(
            PersistedApiKeyState.Missing,
            ApiKeyMaskingPolicy.toPersistedState(null),
        )
        assertEquals(
            PersistedApiKeyState.Persisted(maskedValue = "••••cdef"),
            ApiKeyMaskingPolicy.toPersistedState("abcdef"),
        )
        assertEquals("••••", ApiKeyMaskingPolicy.mask("abcd"))
    }

    @Test
    fun `display snapshot never exposes the plaintext api key`() {
        val snapshot: ChatSettings = ChatSettingsValidator.mergeForDisplay(
            settings = NonSensitiveChatSettings(
                apiBaseUrl = "https://api.example.com/v1/",
                modelId = "gpt-4o-mini",
            ),
            apiKey = "sk-secret-1234",
        )

        assertEquals("https://api.example.com/v1/", snapshot.apiBaseUrl)
        assertEquals("gpt-4o-mini", snapshot.modelId)
        assertEquals(
            PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            snapshot.apiKeyState,
        )
    }

    @Test
    fun `privacy policy forbids logging api keys and auth headers`() {
        assertFalse(SettingsPrivacyPolicy.isSafeToLogField("apiKey"))
        assertFalse(SettingsPrivacyPolicy.isSafeToLogHeader("Authorization"))
        assertFalse(SettingsPrivacyPolicy.isSafeToLogHeader("x-api-key"))
        assertTrue(SettingsPrivacyPolicy.isSafeToLogField("modelId"))
        assertTrue(SettingsPrivacyPolicy.isSafeToLogHeader("Content-Type"))
    }
}
