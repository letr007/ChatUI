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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.letr.chatui.R
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.data.model.ThemeColorOption
import com.letr.chatui.ui.theme.LocalChatUiCorners
import com.letr.chatui.ui.theme.LocalChatUiShellDimensions
import com.letr.chatui.ui.theme.LocalChatUiSpacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiBaseUrlChanged: (String) -> Unit,
    onModelIdChanged: (String) -> Unit,
    onThemeColorChanged: (ThemeColorOption) -> Unit,
    onApiKeyInputChanged: (String) -> Unit,
    onFetchModels: () -> Unit,
    onImportModelId: (String) -> Unit,
    onAddCurrentModelToConfiguredList: () -> Unit,
    onSelectConfiguredModel: (String) -> Unit,
    onRemoveConfiguredModel: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val shellDimensions = LocalChatUiShellDimensions

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
                .widthIn(max = shellDimensions.settingsMaxWidth),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            SettingsFeedbackCard(uiState = uiState)

            SettingsSectionCard(
                title = stringResource(R.string.settings_theme_section_title),
                subtitle = stringResource(R.string.settings_theme_section_subtitle),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                ) {
                    ThemeColorOption.entries.forEach { themeColor ->
                        SettingsThemeColorChip(
                            themeColor = themeColor,
                            selected = uiState.themeColor == themeColor,
                            onClick = { onThemeColorChanged(themeColor) },
                        )
                    }
                }
            }

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
                    supportingText = null,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onAddCurrentModelToConfiguredList,
                        enabled = !uiState.isSaving && uiState.modelId.isNotBlank(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_models_add_local_action))
                    }

                    OutlinedButton(
                        onClick = onFetchModels,
                        enabled = !uiState.isSaving && !uiState.isFetchingModels,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f),
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

                if (uiState.configuredModelIds.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        Text(
                            text = stringResource(R.string.settings_models_local_section_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            uiState.configuredModelIds.forEach { modelId ->
                                SettingsConfiguredModelChip(
                                    modelId = modelId,
                                    selected = uiState.modelId == modelId,
                                    onSelect = { onSelectConfiguredModel(modelId) },
                                    onRemove = { onRemoveConfiguredModel(modelId) },
                                )
                            }
                        }
                    }
                }

                if (uiState.availableModelIds.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        Text(
                            text = stringResource(R.string.settings_models_remote_section_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            uiState.availableModelIds.forEach { modelId ->
                                SettingsImportModelChip(
                                    modelId = modelId,
                                    imported = modelId in uiState.configuredModelIds,
                                    onClick = { onImportModelId(modelId) },
                                )
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(
                title = stringResource(R.string.settings_access_section_title),
                subtitle = stringResource(R.string.settings_access_section_subtitle),
            ) {
                PersistedSecretSettingsField(
                    rawValue = uiState.apiKeyInput,
                    onValueChange = onApiKeyInputChanged,
                    label = stringResource(R.string.settings_api_key_label),
                    enabled = !uiState.isSaving,
                    isError = uiState.validationIssues.any { it == SettingsValidationIssue.MissingApiKey },
                    persistedApiKeyState = uiState.persistedApiKeyState,
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
            )
        }
    }
}

@Composable
private fun SettingsThemeColorChip(
    themeColor: ThemeColorOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = corners.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(themeColorSwatch(themeColor)),
            )
            Text(
                text = themeColorLabel(themeColor),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun PersistedSecretSettingsField(
    rawValue: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean,
    persistedApiKeyState: PersistedApiKeyState,
    visualTransformation: VisualTransformation,
    keyboardOptions: KeyboardOptions,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val displayedValue = when {
        rawValue.isNotEmpty() -> rawValue
        persistedApiKeyState is PersistedApiKeyState.Persisted && !isFocused -> persistedApiKeyState.maskedValue
        else -> ""
    }

    MinimalSettingsField(
        value = displayedValue,
        onValueChange = { updatedValue ->
            if (persistedApiKeyState is PersistedApiKeyState.Persisted && rawValue.isEmpty() && !isFocused) {
                onValueChange("")
            } else {
                onValueChange(updatedValue)
            }
        },
        label = label,
        enabled = enabled,
        isError = isError,
        supportingText = null,
        visualTransformation = if (rawValue.isEmpty() && persistedApiKeyState is PersistedApiKeyState.Persisted && !isFocused) {
            VisualTransformation.None
        } else {
            visualTransformation
        },
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
    )
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
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.04f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SettingsActionCard(
    uiState: SettingsUiState,
    onSave: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Surface(
        shape = corners.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.04f)),
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
            Button(
                onClick = onSave,
                enabled = uiState.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
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
private fun SettingsImportModelChip(
    modelId: String,
    imported: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Box(
        modifier = Modifier
            .clip(corners.medium)
            .clickable(onClick = onClick)
            .background(
                if (imported) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .heightIn(min = 44.dp)
            .padding(horizontal = spacing.medium, vertical = spacing.small),
    ) {
        Text(
            text = modelId,
            style = MaterialTheme.typography.labelLarge,
            color = if (imported) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SettingsConfiguredModelChip(
    modelId: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    Row(
        modifier = Modifier
            .clip(corners.medium)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onSelect)
            .heightIn(min = 44.dp)
            .padding(start = spacing.medium, end = spacing.small, top = spacing.small, bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
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
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onRemove)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    interactionSource: MutableInteractionSource? = null,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            singleLine = true,
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
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

@Composable
private fun themeColorLabel(themeColor: ThemeColorOption): String {
    return when (themeColor) {
        ThemeColorOption.DEFAULT -> stringResource(R.string.settings_theme_color_default)
        ThemeColorOption.BLUE -> stringResource(R.string.settings_theme_color_blue)
        ThemeColorOption.GREEN -> stringResource(R.string.settings_theme_color_green)
        ThemeColorOption.PURPLE -> stringResource(R.string.settings_theme_color_purple)
        ThemeColorOption.AMBER -> stringResource(R.string.settings_theme_color_amber)
    }
}

private fun themeColorSwatch(themeColor: ThemeColorOption): Color {
    return when (themeColor) {
        ThemeColorOption.DEFAULT -> Color(0xFF27403A)
        ThemeColorOption.BLUE -> Color(0xFF2057A6)
        ThemeColorOption.GREEN -> Color(0xFF256C44)
        ThemeColorOption.PURPLE -> Color(0xFF6A46B9)
        ThemeColorOption.AMBER -> Color(0xFF8A5200)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        uiState = SettingsUiState(
            apiBaseUrl = "https://api.openai.com/v1",
            modelId = "gpt-5.4",
            configuredModelIds = listOf("gpt-4.1", "gpt-5.4"),
            themeColor = ThemeColorOption.BLUE,
            persistedApiKeyState = PersistedApiKeyState.Persisted(maskedValue = "••••1234"),
            availableModelIds = listOf("gpt-4o-mini", "gpt-4.1", "gpt-5.4"),
            canSave = true,
        ),
        onApiBaseUrlChanged = {},
        onModelIdChanged = {},
        onThemeColorChanged = {},
        onApiKeyInputChanged = {},
        onFetchModels = {},
        onImportModelId = {},
        onAddCurrentModelToConfiguredList = {},
        onSelectConfiguredModel = {},
        onRemoveConfiguredModel = {},
        onSave = {},
    )
}
