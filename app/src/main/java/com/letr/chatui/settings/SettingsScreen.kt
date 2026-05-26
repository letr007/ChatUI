package com.letr.chatui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.letr.chatui.R
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.ui.theme.LocalChatUiCorners
import com.letr.chatui.ui.theme.LocalChatUiShellDimensions
import com.letr.chatui.ui.theme.LocalChatUiSpacing

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiBaseUrlChanged: (String) -> Unit,
    onModelIdChanged: (String) -> Unit,
    onApiKeyInputChanged: (String) -> Unit,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        SettingsFeedbackCard(uiState = uiState)

        MinimalSettingsField(
            value = uiState.apiBaseUrl,
            onValueChange = onApiBaseUrlChanged,
            label = stringResource(R.string.settings_base_url_label),
            enabled = !uiState.isSaving,
            isError = uiState.validationIssues.any {
                it == SettingsValidationIssue.MissingBaseUrl ||
                    it is SettingsValidationIssue.InvalidBaseUrl ||
                    it is SettingsValidationIssue.InsecureHttpBaseUrl
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        MinimalSettingsField(
            value = uiState.modelId,
            onValueChange = onModelIdChanged,
            label = stringResource(R.string.settings_model_id_label),
            enabled = !uiState.isSaving,
            isError = uiState.validationIssues.any { it == SettingsValidationIssue.MissingModelId },
        )

        MinimalSettingsField(
            value = uiState.apiKeyInput,
            onValueChange = onApiKeyInputChanged,
            label = stringResource(R.string.settings_api_key_label),
            enabled = !uiState.isSaving,
            isError = uiState.validationIssues.any { it == SettingsValidationIssue.MissingApiKey },
            supportingText = when (val persistedState = uiState.persistedApiKeyState) {
                is PersistedApiKeyState.Persisted -> {
                    stringResource(
                        R.string.settings_api_key_stored_supporting,
                        persistedState.maskedValue,
                    )
                }

                PersistedApiKeyState.Missing -> stringResource(R.string.settings_api_key_required_supporting)
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        if (uiState.validationIssues.isNotEmpty()) {
            Surface(
                shape = corners.medium,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    uiState.validationIssues.forEach { issue ->
                        Text(
                            text = validationIssueLabel(issue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onSave,
                enabled = uiState.canSave,
                modifier = Modifier.weight(1f),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = spacing.small),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Text(
                    if (uiState.isSaving) {
                        stringResource(R.string.settings_saving)
                    } else {
                        stringResource(R.string.settings_save)
                    }
                )
            }

            OutlinedButton(
                onClick = onClearApiKey,
                enabled = uiState.canClearApiKey,
            ) {
                Text(stringResource(R.string.settings_clear_stored_key))
            }
        }

    }
}

@Composable
private fun SettingsFeedbackCard(uiState: SettingsUiState) {
    val feedback = uiState.feedback ?: return
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    val containerColor = if (feedback.isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (feedback.isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        shape = corners.medium,
        color = containerColor.copy(alpha = 0.5f),
    ) {
        Text(
            text = feedback.message,
            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun MinimalSettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners

    Surface(
        shape = corners.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
                isError = isError,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            supportingText?.let { supporting ->
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun validationIssueLabel(issue: SettingsValidationIssue): String {
    return when (issue) {
        SettingsValidationIssue.MissingBaseUrl -> stringResource(R.string.settings_validation_missing_base_url)
        is SettingsValidationIssue.InvalidBaseUrl -> {
            stringResource(R.string.settings_validation_invalid_base_url)
        }

        is SettingsValidationIssue.InsecureHttpBaseUrl -> stringResource(R.string.settings_validation_insecure_base_url)
        SettingsValidationIssue.MissingApiKey -> stringResource(R.string.settings_validation_missing_api_key)
        SettingsValidationIssue.MissingModelId -> stringResource(R.string.settings_validation_missing_model_id)
    }
}
