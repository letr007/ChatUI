package com.letr.chatui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.letr.chatui.R
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.ui.theme.LocalChatUiCorners
import com.letr.chatui.ui.theme.LocalChatUiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiBaseUrlChanged: (String) -> Unit,
    onModelIdChanged: (String) -> Unit,
    onApiKeyInputChanged: (String) -> Unit,
    onFetchModels: () -> Unit,
    onImportModelId: (String) -> Unit,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
) {
    val spacing = LocalChatUiSpacing

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            SettingsHeaderCard()
            SettingsFeedbackCard(uiState = uiState)

            SettingsSectionCard(
                title = stringResource(R.string.settings_connection_section_title),
                subtitle = stringResource(R.string.settings_connection_section_subtitle),
            ) {
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
                    supportingText = stringResource(R.string.settings_base_url_supporting),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }

            SettingsSectionCard(
                title = stringResource(R.string.settings_model_section_title),
                subtitle = stringResource(R.string.settings_model_section_subtitle),
            ) {
                MinimalSettingsField(
                    value = uiState.modelId,
                    onValueChange = onModelIdChanged,
                    label = stringResource(R.string.settings_model_id_label),
                    enabled = !uiState.isSaving,
                    isError = uiState.validationIssues.any { it == SettingsValidationIssue.MissingModelId },
                    supportingText = stringResource(R.string.settings_model_id_supporting),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_models_import_supporting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )

                    OutlinedButton(
                        onClick = onFetchModels,
                        enabled = !uiState.isSaving && !uiState.isFetchingModels,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                        ),
                    ) {
                        if (uiState.isFetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = spacing.small).heightIn(min = 16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = if (uiState.isFetchingModels) {
                                stringResource(R.string.settings_models_loading)
                            } else {
                                stringResource(R.string.settings_models_import_action)
                            }
                        )
                    }
                }

                if (uiState.availableModelIds.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        uiState.availableModelIds.forEach { modelId ->
                            SettingsModelChip(
                                modelId = modelId,
                                selected = uiState.modelId == modelId,
                                onClick = { onImportModelId(modelId) },
                            )
                        }
                    }
                }
            }

            SettingsSectionCard(
                title = stringResource(R.string.settings_access_section_title),
                subtitle = stringResource(R.string.settings_access_section_subtitle),
            ) {
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
            }

            if (uiState.validationIssues.isNotEmpty()) {
                SettingsValidationCard(uiState.validationIssues)
            }

            SettingsActionCard(
                uiState = uiState,
                onSave = onSave,
                onClearApiKey = onClearApiKey,
            )
        }
    }
}

@Composable
private fun SettingsHeaderCard() {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Surface(
        shape = corners.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Surface(
        shape = corners.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            },
        )
    }
}

@Composable
private fun SettingsActionCard(
    uiState: SettingsUiState,
    onSave: () -> Unit,
    onClearApiKey: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Surface(
        shape = corners.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            if (uiState.hasUnsavedChanges) {
                Text(
                    text = stringResource(R.string.settings_unsaved_changes_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                            modifier = Modifier.padding(end = spacing.small).heightIn(min = 16.dp),
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                ) {
                    Text(stringResource(R.string.settings_clear_stored_key))
                }
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
        color = containerColor.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.08f)),
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
private fun SettingsValidationCard(validationIssues: List<SettingsValidationIssue>) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Surface(
        shape = corners.medium,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            validationIssues.forEach { issue ->
                Text(
                    text = validationIssueLabel(issue),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun SettingsModelChip(
    modelId: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Box(
        modifier = Modifier
            .clip(corners.medium)
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
                }
            )
            .padding(horizontal = spacing.medium, vertical = spacing.small),
    ) {
        Text(
            text = modelId,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
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

    Column(
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
            shape = corners.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                },
                unfocusedBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
                },
                disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
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

@Composable
private fun validationIssueLabel(issue: SettingsValidationIssue): String {
    return when (issue) {
        SettingsValidationIssue.MissingBaseUrl -> stringResource(R.string.settings_validation_missing_base_url)
        is SettingsValidationIssue.InvalidBaseUrl -> stringResource(R.string.settings_validation_invalid_base_url)
        is SettingsValidationIssue.InsecureHttpBaseUrl -> stringResource(R.string.settings_validation_insecure_base_url)
        SettingsValidationIssue.MissingApiKey -> stringResource(R.string.settings_validation_missing_api_key)
        SettingsValidationIssue.MissingModelId -> stringResource(R.string.settings_validation_missing_model_id)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        uiState = SettingsUiState(
            apiBaseUrl = "https://api.openai.com/v1",
            modelId = "gpt-4o-mini",
            persistedApiKeyState = PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            availableModelIds = listOf("gpt-4o-mini", "gpt-5.4", "gpt-4.1"),
            canSave = true,
            canClearApiKey = true,
        ),
        onApiBaseUrlChanged = {},
        onModelIdChanged = {},
        onApiKeyInputChanged = {},
        onFetchModels = {},
        onImportModelId = {},
        onSave = {},
        onClearApiKey = {},
    )
}
