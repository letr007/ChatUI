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
    val apiKeyInput: String = "",
    val persistedApiKeyState: PersistedApiKeyState = PersistedApiKeyState.Missing,
    val validationIssues: List<SettingsValidationIssue> = emptyList(),
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val canSave: Boolean = false,
    val canClearApiKey: Boolean = false,
    val feedback: SettingsFeedback? = null,
)

private data class SavedSettingsSnapshot(
    val apiBaseUrl: String,
    val modelId: String,
    val persistedApiKeyState: PersistedApiKeyState,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val secretSettingsRepository: SecretSettingsRepository,
    private val resources: Resources,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var savedSnapshot = SavedSettingsSnapshot(
        apiBaseUrl = "",
        modelId = "",
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
                            apiKeyInput = it.apiKeyInput,
                            persistedApiKeyState = it.persistedApiKeyState,
                        ),
                        canSave = issues.isEmpty() && hasUnsavedChanges(
                            apiBaseUrl = it.apiBaseUrl,
                            modelId = it.modelId,
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
                            apiKeyInput = it.apiKeyInput,
                            persistedApiKeyState = it.persistedApiKeyState,
                        ),
                        canSave = issues.isEmpty() && hasUnsavedChanges(
                            apiBaseUrl = it.apiBaseUrl,
                            modelId = it.modelId,
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
        _uiState.update {
            buildUiState(
                apiBaseUrl = apiBaseUrl,
                modelId = modelId,
                apiKeyInput = apiKeyInput,
                persistedApiKeyState = it.persistedApiKeyState,
                isSaving = false,
                feedback = null,
            )
        }
    }

    private fun refreshFromRepository(successMessage: String? = null) {
        viewModelScope.launch {
            val settings = settingsRepository.getChatSettings()
            savedSnapshot = SavedSettingsSnapshot(
                apiBaseUrl = settings.apiBaseUrl,
                modelId = settings.modelId,
                persistedApiKeyState = settings.apiKeyState,
            )
            _uiState.value = buildUiState(
                apiBaseUrl = settings.apiBaseUrl,
                modelId = settings.modelId,
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
            apiKeyInput = apiKeyInput,
            persistedApiKeyState = persistedApiKeyState,
        )
        return SettingsUiState(
            apiBaseUrl = apiBaseUrl,
            modelId = modelId,
            apiKeyInput = apiKeyInput,
            persistedApiKeyState = persistedApiKeyState,
            validationIssues = validationIssues,
            isSaving = isSaving,
            hasUnsavedChanges = hasUnsavedChanges,
            canSave = !isSaving && hasUnsavedChanges && validationIssues.isEmpty(),
            canClearApiKey = !isSaving && persistedApiKeyState is PersistedApiKeyState.Persisted,
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
        apiKeyInput: String,
        persistedApiKeyState: PersistedApiKeyState,
    ): Boolean {
        return apiBaseUrl != savedSnapshot.apiBaseUrl ||
            modelId != savedSnapshot.modelId ||
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
}
