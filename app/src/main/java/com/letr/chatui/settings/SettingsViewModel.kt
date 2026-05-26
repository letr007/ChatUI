package com.letr.chatui.settings

import android.content.res.Resources
import com.letr.chatui.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.data.repository.SecretSettingsRepository
import com.letr.chatui.data.repository.SettingsRepository
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionErrorMapper
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionFailure
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionHttpException
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionProtocolException
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfig
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfigValidator
import com.letr.chatui.network.chatcompletions.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsFeedback(
    val message: String,
    val isError: Boolean,
)

data class SettingsUiState(
    val apiBaseUrl: String = "",
    val modelId: String = "",
    val configuredModelIds: List<String> = emptyList(),
    val apiKeyInput: String = "",
    val persistedApiKeyState: PersistedApiKeyState = PersistedApiKeyState.Missing,
    val validationIssues: List<SettingsValidationIssue> = emptyList(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val canSave: Boolean = false,
    val canClearApiKey: Boolean = false,
    val availableModelIds: List<String> = emptyList(),
    val isFetchingModels: Boolean = false,
    val feedback: SettingsFeedback? = null,
)

private data class SavedSettingsSnapshot(
    val apiBaseUrl: String,
    val modelId: String,
    val configuredModelIds: List<String>,
    val persistedApiKeyState: PersistedApiKeyState,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secretSettingsRepository: SecretSettingsRepository,
    private val modelsCatalogClient: OpenAiModelsCatalogClient,
    private val resources: Resources,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var savedSnapshot = SavedSettingsSnapshot(
        apiBaseUrl = "",
        modelId = "",
        configuredModelIds = emptyList(),
        persistedApiKeyState = PersistedApiKeyState.Missing,
    )

    init {
        refreshFromRepository()
    }

    fun onApiBaseUrlChanged(value: String) {
        updateForm(apiBaseUrl = value)
    }

    fun onModelIdChanged(value: String) {
        updateForm(modelId = value)
    }

    fun onApiKeyInputChanged(value: String) {
        updateForm(apiKeyInput = value)
    }

    fun fetchModels() {
        val state = uiState.value
        if (state.isSaving || state.isFetchingModels) {
            return
        }

        viewModelScope.launch {
            val apiKey = state.apiKeyInput.trim().ifEmpty { secretSettingsRepository.getApiKey().orEmpty() }
            val validationFailure = OpenAiProviderConfigValidator.validateForModelsList(
                OpenAiProviderConfig(
                    baseUrl = state.apiBaseUrl,
                    apiKey = apiKey.ifBlank { null },
                    modelId = "",
                )
            )
            if (validationFailure != null) {
                _uiState.update {
                    it.copy(
                        isFetchingModels = false,
                        feedback = SettingsFeedback(
                            message = validationFailure.toUiMessage(resources),
                            isError = true,
                        ),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isFetchingModels = true, feedback = null) }
            try {
                val modelIds = modelsCatalogClient.fetchModels(
                    baseUrl = state.apiBaseUrl,
                    apiKey = apiKey,
                ).map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()
                _uiState.update {
                    it.copy(
                        availableModelIds = modelIds,
                        isFetchingModels = false,
                        feedback = SettingsFeedback(
                            message = if (modelIds.isEmpty()) {
                                resources.getString(R.string.settings_models_empty_feedback)
                            } else {
                                resources.getString(R.string.settings_models_loaded_feedback, modelIds.size)
                            },
                            isError = false,
                        ),
                    )
                }
            } catch (throwable: Throwable) {
                val failure = throwable.toModelsFetchFailure()
                _uiState.update {
                    it.copy(
                        isFetchingModels = false,
                        feedback = SettingsFeedback(
                            message = failure.toUiMessage(resources),
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    fun importModelId(modelId: String) {
        _uiState.update {
            it.copy(
                configuredModelIds = (it.configuredModelIds + modelId)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .sorted(),
                feedback = null,
            )
        }
    }

    fun addCurrentModelToConfiguredList() {
        val modelId = uiState.value.modelId.trim()
        if (modelId.isEmpty()) {
            return
        }
        importModelId(modelId)
    }

    fun selectConfiguredModel(modelId: String) {
        updateForm(modelId = modelId)
    }

    fun removeConfiguredModel(modelId: String) {
        _uiState.update {
            it.copy(
                configuredModelIds = it.configuredModelIds.filterNot { configuredModelId -> configuredModelId == modelId },
                feedback = null,
            )
        }
    }

    fun switchActiveModel(modelId: String) {
        val state = uiState.value
        if (modelId.isBlank() || modelId == state.modelId || modelId !in state.configuredModelIds) {
            return
        }

        viewModelScope.launch {
            val settings = settingsRepository.getChatSettings()
            settingsRepository.updateNonSensitiveSettings(
                NonSensitiveChatSettings(
                    apiBaseUrl = settings.apiBaseUrl,
                    modelId = modelId,
                    configuredModelIds = settings.configuredModelIds,
                )
            )
            refreshFromRepository()
        }
    }

    fun saveSettings() {
        val state = uiState.value
        val validationIssues = validate(
            apiBaseUrl = state.apiBaseUrl,
            modelId = state.modelId,
            persistedApiKeyState = state.persistedApiKeyState,
            apiKeyInput = state.apiKeyInput,
        )
        if (validationIssues.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    validationIssues = validationIssues,
                    feedback = validationFailureFeedback(validationIssues),
                    canSave = false,
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, feedback = null, canSave = false, canClearApiKey = false) }

            val normalizedApiKey = state.apiKeyInput.trim().takeIf { it.isNotEmpty() }
            val hadPersistedKey = state.persistedApiKeyState is PersistedApiKeyState.Persisted

            try {
                if (!hadPersistedKey && normalizedApiKey != null) {
                    secretSettingsRepository.setApiKey(normalizedApiKey)
                    try {
                        settingsRepository.updateNonSensitiveSettings(
                            NonSensitiveChatSettings(
                                apiBaseUrl = state.apiBaseUrl,
                                modelId = state.modelId,
                                configuredModelIds = state.configuredModelIds,
                            )
                        )
                    } catch (throwable: Throwable) {
                        secretSettingsRepository.clearApiKey()
                        throw throwable
                    }
                } else {
                    settingsRepository.updateNonSensitiveSettings(
                        NonSensitiveChatSettings(
                            apiBaseUrl = state.apiBaseUrl,
                            modelId = state.modelId,
                            configuredModelIds = state.configuredModelIds,
                        )
                    )
                    if (normalizedApiKey != null) {
                        secretSettingsRepository.setApiKey(normalizedApiKey)
                    }
                }

                refreshFromRepository(successMessage = resources.getString(R.string.settings_saved_feedback))
            } catch (throwable: Throwable) {
                _uiState.update {
                    val issues = validate(
                        apiBaseUrl = it.apiBaseUrl,
                        modelId = it.modelId,
                        persistedApiKeyState = it.persistedApiKeyState,
                        apiKeyInput = it.apiKeyInput,
                    )
                    it.copy(
                        isSaving = false,
                        validationIssues = issues,
                        hasUnsavedChanges = hasUnsavedChanges(
                            apiBaseUrl = it.apiBaseUrl,
                            modelId = it.modelId,
                            configuredModelIds = it.configuredModelIds,
                            apiKeyInput = it.apiKeyInput,
                            persistedApiKeyState = it.persistedApiKeyState,
                        ),
                        canSave = issues.isEmpty() && hasUnsavedChanges(
                            apiBaseUrl = it.apiBaseUrl,
                            modelId = it.modelId,
                            configuredModelIds = it.configuredModelIds,
                            apiKeyInput = it.apiKeyInput,
                            persistedApiKeyState = it.persistedApiKeyState,
                        ),
                        canClearApiKey = it.persistedApiKeyState is PersistedApiKeyState.Persisted,
                        feedback = SettingsFeedback(
                            message = throwable.message ?: resources.getString(R.string.settings_save_failed),
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    fun clearPersistedApiKey() {
        if (uiState.value.persistedApiKeyState !is PersistedApiKeyState.Persisted || uiState.value.isSaving) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, feedback = null, canSave = false, canClearApiKey = false) }
            try {
                secretSettingsRepository.clearApiKey()
                refreshFromRepository(successMessage = resources.getString(R.string.settings_cleared_feedback))
            } catch (throwable: Throwable) {
                _uiState.update {
                    val issues = validate(
                        apiBaseUrl = it.apiBaseUrl,
                        modelId = it.modelId,
                        persistedApiKeyState = it.persistedApiKeyState,
                        apiKeyInput = it.apiKeyInput,
                    )
                    it.copy(
                        isSaving = false,
                        validationIssues = issues,
                        hasUnsavedChanges = hasUnsavedChanges(
                            apiBaseUrl = it.apiBaseUrl,
                            modelId = it.modelId,
                            configuredModelIds = it.configuredModelIds,
                            apiKeyInput = it.apiKeyInput,
                            persistedApiKeyState = it.persistedApiKeyState,
                        ),
                        canSave = issues.isEmpty() && hasUnsavedChanges(
                            apiBaseUrl = it.apiBaseUrl,
                            modelId = it.modelId,
                            configuredModelIds = it.configuredModelIds,
                            apiKeyInput = it.apiKeyInput,
                            persistedApiKeyState = it.persistedApiKeyState,
                        ),
                        canClearApiKey = it.persistedApiKeyState is PersistedApiKeyState.Persisted,
                        feedback = SettingsFeedback(
                            message = throwable.message ?: resources.getString(R.string.settings_clear_failed),
                            isError = true,
                        ),
                    )
                }
            }
        }
    }

    private fun updateForm(
        apiBaseUrl: String = uiState.value.apiBaseUrl,
        modelId: String = uiState.value.modelId,
        apiKeyInput: String = uiState.value.apiKeyInput,
    ) {
        val shouldClearImportedModels =
            apiBaseUrl != uiState.value.apiBaseUrl || apiKeyInput != uiState.value.apiKeyInput
        _uiState.update {
            buildUiState(
                apiBaseUrl = apiBaseUrl,
                modelId = modelId,
                configuredModelIds = it.configuredModelIds,
                apiKeyInput = apiKeyInput,
                persistedApiKeyState = it.persistedApiKeyState,
                isSaving = false,
                feedback = null,
            ).copy(
                availableModelIds = if (shouldClearImportedModels) emptyList() else it.availableModelIds,
            )
        }
    }

    private fun refreshFromRepository(successMessage: String? = null) {
        viewModelScope.launch {
            val settings = settingsRepository.getChatSettings()
            savedSnapshot = SavedSettingsSnapshot(
                apiBaseUrl = settings.apiBaseUrl,
                modelId = settings.modelId,
                configuredModelIds = settings.configuredModelIds,
                persistedApiKeyState = settings.apiKeyState,
            )
            _uiState.value = buildUiState(
                apiBaseUrl = settings.apiBaseUrl,
                modelId = settings.modelId,
                configuredModelIds = settings.configuredModelIds,
                apiKeyInput = "",
                persistedApiKeyState = settings.apiKeyState,
                isSaving = false,
                feedback = successMessage?.let { SettingsFeedback(message = it, isError = false) },
            )
        }
    }

    private fun buildUiState(
        apiBaseUrl: String,
        modelId: String,
        configuredModelIds: List<String>,
        apiKeyInput: String,
        persistedApiKeyState: PersistedApiKeyState,
        isSaving: Boolean,
        feedback: SettingsFeedback?,
    ): SettingsUiState {
        val validationIssues = validate(
            apiBaseUrl = apiBaseUrl,
            modelId = modelId,
            persistedApiKeyState = persistedApiKeyState,
            apiKeyInput = apiKeyInput,
        )
        val hasUnsavedChanges = hasUnsavedChanges(
            apiBaseUrl = apiBaseUrl,
            modelId = modelId,
            configuredModelIds = configuredModelIds,
            apiKeyInput = apiKeyInput,
            persistedApiKeyState = persistedApiKeyState,
        )
        return SettingsUiState(
            apiBaseUrl = apiBaseUrl,
            modelId = modelId,
            configuredModelIds = configuredModelIds,
            apiKeyInput = apiKeyInput,
            persistedApiKeyState = persistedApiKeyState,
            validationIssues = validationIssues,
            isSaving = isSaving,
            hasUnsavedChanges = hasUnsavedChanges,
            canSave = !isSaving && hasUnsavedChanges && validationIssues.isEmpty(),
            canClearApiKey = !isSaving && persistedApiKeyState is PersistedApiKeyState.Persisted,
            availableModelIds = uiState.value.availableModelIds,
            isFetchingModels = uiState.value.isFetchingModels,
            feedback = feedback,
        )
    }

    private fun validate(
        apiBaseUrl: String,
        modelId: String,
        persistedApiKeyState: PersistedApiKeyState,
        apiKeyInput: String,
    ): List<SettingsValidationIssue> {
        return ChatSettingsValidator.validate(
            settings = NonSensitiveChatSettings(
                apiBaseUrl = apiBaseUrl,
                modelId = modelId,
            ),
            hasApiKey = persistedApiKeyState is PersistedApiKeyState.Persisted || apiKeyInput.isNotBlank(),
        ).issues
    }

    private fun hasUnsavedChanges(
        apiBaseUrl: String,
        modelId: String,
        configuredModelIds: List<String>,
        apiKeyInput: String,
        persistedApiKeyState: PersistedApiKeyState,
    ): Boolean {
        return apiBaseUrl != savedSnapshot.apiBaseUrl ||
            modelId != savedSnapshot.modelId ||
            configuredModelIds != savedSnapshot.configuredModelIds ||
            apiKeyInput.isNotBlank() ||
            persistedApiKeyState != savedSnapshot.persistedApiKeyState
    }

    private fun validationFailureFeedback(validationIssues: List<SettingsValidationIssue>): SettingsFeedback {
        val message = when (validationIssues.firstOrNull()) {
            SettingsValidationIssue.MissingBaseUrl -> resources.getString(R.string.settings_feedback_missing_base_url)
            is SettingsValidationIssue.InvalidBaseUrl -> {
                resources.getString(R.string.settings_feedback_invalid_base_url)
            }

            is SettingsValidationIssue.InsecureHttpBaseUrl -> {
                resources.getString(R.string.settings_feedback_insecure_base_url)
            }

            SettingsValidationIssue.MissingApiKey -> resources.getString(R.string.settings_feedback_missing_api_key)
            SettingsValidationIssue.MissingModelId -> resources.getString(R.string.settings_feedback_missing_model_id)
            null -> resources.getString(R.string.settings_feedback_fix_before_saving)
        }
        return SettingsFeedback(message = message, isError = true)
    }

    private fun Throwable.toModelsFetchFailure(): OpenAiChatCompletionFailure {
        return when (this) {
            is OpenAiChatCompletionHttpException -> OpenAiChatCompletionErrorMapper.fromHttpStatus(
                statusCode = statusCode,
                retryAfterSeconds = retryAfterSeconds,
            )

            is OpenAiChatCompletionProtocolException -> OpenAiChatCompletionFailure.Protocol(message ?: "Malformed models response.")
            else -> OpenAiChatCompletionErrorMapper.fromThrowable(this)
        }
    }
}
