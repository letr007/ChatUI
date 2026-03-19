package com.letr.chatui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    onBackToChat: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val shellDimensions = LocalChatUiShellDimensions
    val corners = LocalChatUiCorners

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = corners.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(spacing.xLarge),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.settings_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsFeedbackCard(uiState = uiState)

            OutlinedTextField(
                value = uiState.apiBaseUrl,
                onValueChange = onApiBaseUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_base_url_label)) },
                supportingText = { Text(stringResource(R.string.settings_base_url_supporting)) },
                singleLine = true,
                enabled = !uiState.isSaving,
                isError = uiState.validationIssues.any {
                    it == SettingsValidationIssue.MissingBaseUrl ||
                        it is SettingsValidationIssue.InvalidBaseUrl ||
                        it is SettingsValidationIssue.InsecureHttpBaseUrl
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            OutlinedTextField(
                value = uiState.modelId,
                onValueChange = onModelIdChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_model_id_label)) },
                supportingText = { Text(stringResource(R.string.settings_model_id_supporting)) },
                singleLine = true,
                enabled = !uiState.isSaving,
                isError = uiState.validationIssues.any { it == SettingsValidationIssue.MissingModelId },
            )

            OutlinedTextField(
                value = uiState.apiKeyInput,
                onValueChange = onApiKeyInputChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_api_key_label)) },
                supportingText = {
                    val helper = when (val apiKeyState = uiState.persistedApiKeyState) {
                        PersistedApiKeyState.Missing -> stringResource(R.string.settings_api_key_required_supporting)
                        is PersistedApiKeyState.Persisted -> stringResource(
                            R.string.settings_api_key_stored_supporting,
                            apiKeyState.maskedValue,
                        )
                    }
                    Text(helper)
                },
                singleLine = true,
                enabled = !uiState.isSaving,
                isError = uiState.validationIssues.any { it == SettingsValidationIssue.MissingApiKey },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            if (uiState.validationIssues.isNotEmpty()) {
                Card(
                    shape = corners.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.large),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_fix_before_saving),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
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
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onSave,
                    enabled = uiState.canSave,
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

            Card(
                shape = corners.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = shellDimensions.sectionCardMinHeight)
                        .padding(spacing.large),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (uiState.hasUnsavedChanges) {
                            stringResource(R.string.settings_unsaved_changes_notice)
                        } else {
                            stringResource(R.string.settings_saved_notice)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            TextButton(onClick = onBackToChat) {
                Text(text = stringResource(R.string.settings_return_to_chat))
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

    Card(
        shape = corners.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Text(
            text = feedback.message,
            modifier = Modifier.padding(spacing.large),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
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
