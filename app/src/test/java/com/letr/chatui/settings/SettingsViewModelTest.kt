package com.letr.chatui.settings

import android.content.res.Resources
import com.letr.chatui.R
import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.data.repository.SecretSettingsRepository
import com.letr.chatui.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var resources: Resources

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        resources = RuntimeEnvironment.getApplication().resources
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading settings exposes persisted values and save stays disabled`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = ChatSettings(
                apiBaseUrl = "https://api.example.com/v1",
                modelId = "gpt-4o-mini",
                apiKeyState = PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            ),
            initialApiKey = "sk-secret-1234",
        )

        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, FakeModelsCatalogClient(), resources)
        advanceUntilIdle()

        assertEquals("https://api.example.com/v1", viewModel.uiState.value.apiBaseUrl)
        assertEquals("gpt-4o-mini", viewModel.uiState.value.modelId)
        assertEquals(PersistedApiKeyState.Persisted(maskedValue = "••••1234"), viewModel.uiState.value.persistedApiKeyState)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `saving valid settings persists non sensitive values and api key`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, FakeModelsCatalogClient(), resources)
        advanceUntilIdle()

        viewModel.onApiBaseUrlChanged("https://api.example.com/v1")
        viewModel.onModelIdChanged("gpt-4o-mini")
        viewModel.onApiKeyInputChanged("sk-secret")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canSave)

        viewModel.saveSettings()
        advanceUntilIdle()

        assertEquals(
            NonSensitiveChatSettings(
                apiBaseUrl = "https://api.example.com/v1",
                modelId = "gpt-4o-mini",
            ),
            settingsRepository.lastUpdatedNonSensitiveSettings,
        )
        assertEquals("sk-secret", settingsRepository.apiKey)
        assertEquals(PersistedApiKeyState.Persisted(maskedValue = "••••cret"), viewModel.uiState.value.persistedApiKeyState)
        assertEquals("", viewModel.uiState.value.apiKeyInput)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertEquals(resources.getString(R.string.settings_saved_feedback), viewModel.uiState.value.feedback?.message)
    }

    @Test
    fun `missing api key blocks save when none is stored`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, FakeModelsCatalogClient(), resources)
        advanceUntilIdle()

        viewModel.onApiBaseUrlChanged("https://api.example.com/v1")
        viewModel.onModelIdChanged("gpt-4o-mini")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationIssues.contains(SettingsValidationIssue.MissingApiKey))
        assertEquals(null, settingsRepository.lastUpdatedNonSensitiveSettings)
        assertTrue(viewModel.uiState.value.feedback?.isError == true)
        assertEquals(
            resources.getString(R.string.settings_feedback_missing_api_key),
            viewModel.uiState.value.feedback?.message,
        )
    }

    @Test
    fun `invalid base url save attempt returns deterministic recovery feedback`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, FakeModelsCatalogClient(), resources)
        advanceUntilIdle()

        viewModel.onApiBaseUrlChanged("not-a-url")
        viewModel.onModelIdChanged("gpt-4o-mini")
        viewModel.onApiKeyInputChanged("sk-secret")
        advanceUntilIdle()

        viewModel.saveSettings()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.validationIssues.any { it is SettingsValidationIssue.InvalidBaseUrl })
        assertEquals(
            resources.getString(R.string.settings_feedback_invalid_base_url),
            viewModel.uiState.value.feedback?.message,
        )
        assertEquals(null, settingsRepository.lastUpdatedNonSensitiveSettings)
    }

    @Test
    fun `clearing persisted api key updates state and requires replacement key`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = ChatSettings(
                apiBaseUrl = "https://api.example.com/v1",
                modelId = "gpt-4o-mini",
                apiKeyState = PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            ),
            initialApiKey = "sk-secret-1234",
        )
        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, FakeModelsCatalogClient(), resources)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canClearApiKey)

        viewModel.clearPersistedApiKey()
        advanceUntilIdle()

        assertEquals(PersistedApiKeyState.Missing, viewModel.uiState.value.persistedApiKeyState)
        assertTrue(viewModel.uiState.value.validationIssues.contains(SettingsValidationIssue.MissingApiKey))
        assertEquals(resources.getString(R.string.settings_cleared_feedback), viewModel.uiState.value.feedback?.message)
        assertEquals(null, settingsRepository.apiKey)
    }

    @Test
    fun `fetching models imports selectable ids into ui state`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = ChatSettings(
                apiBaseUrl = "https://api.example.com/v1",
                modelId = "gpt-4o-mini",
                apiKeyState = PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            ),
            initialApiKey = "sk-secret-1234",
        )
        val modelsCatalogClient = FakeModelsCatalogClient(
            modelIds = listOf("gpt-5.4", "gpt-4.1"),
        )
        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, modelsCatalogClient, resources)
        advanceUntilIdle()

        viewModel.fetchModels()
        advanceUntilIdle()

        assertEquals(listOf("gpt-4.1", "gpt-5.4"), viewModel.uiState.value.availableModelIds)
        assertEquals(resources.getString(R.string.settings_models_loaded_feedback, 2), viewModel.uiState.value.feedback?.message)

        viewModel.importModelId("gpt-5.4")
        advanceUntilIdle()

        assertEquals("gpt-5.4", viewModel.uiState.value.modelId)
        assertEquals(resources.getString(R.string.settings_model_imported_feedback, "gpt-5.4"), viewModel.uiState.value.feedback?.message)
    }

    @Test
    fun `fetching models without key shows deterministic error`() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository(
            initialSettings = ChatSettings(apiBaseUrl = "https://api.example.com/v1", modelId = "gpt-4o-mini"),
        )
        val viewModel = SettingsViewModel(settingsRepository, settingsRepository, FakeModelsCatalogClient(), resources)
        advanceUntilIdle()

        viewModel.fetchModels()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.feedback?.isError == true)
        assertEquals(
            resources.getString(R.string.openai_failure_message_missing_configuration, resources.getString(R.string.openai_field_api_key)),
            viewModel.uiState.value.feedback?.message,
        )
    }
}

private class FakeSettingsRepository(
    initialSettings: ChatSettings = ChatSettings(apiBaseUrl = "", modelId = "", apiKeyState = PersistedApiKeyState.Missing),
    initialApiKey: String? = null,
) : SettingsRepository, SecretSettingsRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)
    var apiKey: String? = initialApiKey
        private set
    var lastUpdatedNonSensitiveSettings: NonSensitiveChatSettings? = null
        private set

    override fun observeChatSettings(): Flow<ChatSettings> = settingsFlow

    override suspend fun getChatSettings(): ChatSettings = settingsFlow.value
        .copy(
            apiKeyState = if (apiKey.isNullOrBlank()) {
                PersistedApiKeyState.Missing
            } else {
                ApiKeyMaskingPolicy.toPersistedState(apiKey)
            },
        )

    override suspend fun updateNonSensitiveSettings(settings: NonSensitiveChatSettings) {
        lastUpdatedNonSensitiveSettings = settings
        settingsFlow.value = ChatSettings(
            apiBaseUrl = settings.apiBaseUrl.trim(),
            modelId = settings.modelId.trim(),
            apiKeyState = if (apiKey.isNullOrBlank()) {
                PersistedApiKeyState.Missing
            } else {
                ApiKeyMaskingPolicy.toPersistedState(apiKey)
            },
        )
    }

    override fun observePersistedApiKeyState(): Flow<PersistedApiKeyState> {
        return MutableStateFlow(settingsFlow.value.apiKeyState)
    }

    override suspend fun getApiKey(): String? = apiKey

    override suspend fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
        settingsFlow.value = settingsFlow.value.copy(
            apiKeyState = ApiKeyMaskingPolicy.toPersistedState(apiKey)
        )
    }

    override suspend fun clearApiKey() {
        apiKey = null
        settingsFlow.value = settingsFlow.value.copy(apiKeyState = PersistedApiKeyState.Missing)
    }
}

private class FakeModelsCatalogClient(
    private val modelIds: List<String> = emptyList(),
    private val error: Throwable? = null,
) : OpenAiModelsCatalogClient {
    override suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        error?.let { throw it }
        return modelIds
    }
}
