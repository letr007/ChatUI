package com.letr.chatui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.letr.chatui.R
import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.ui.theme.LocalChatUiCorners
import com.letr.chatui.ui.theme.LocalChatUiSpacing

@Composable
fun HistoryDrawer(
    conversations: List<Conversation>,
    selectedConversationId: ConversationId?,
    onConversationSelected: (ConversationId) -> Unit,
    onConversationRenamed: (ConversationId, String) -> Unit,
    onConversationDeleted: (ConversationId) -> Unit,
    onStartNewConversation: () -> Unit,
    onClose: () -> Unit,
) {
    val corners = LocalChatUiCorners
    val spacing = LocalChatUiSpacing
    var renameTarget by remember { mutableStateOf<HistoryRenameTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }
    var pendingDeletedConversationId by remember { mutableStateOf<ConversationId?>(null) }
    val visibleConversations = conversations.filterNot { it.id == pendingDeletedConversationId }

    LaunchedEffect(conversations, pendingDeletedConversationId) {
        if (pendingDeletedConversationId != null && conversations.none { it.id == pendingDeletedConversationId }) {
            pendingDeletedConversationId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.medium, vertical = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_drawer_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onClose) {
                Text(text = stringResource(R.string.close))
            }
        }

        Surface(
            onClick = {
                onStartNewConversation()
                onClose()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            shape = corners.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = corners.medium,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
                ) {
                    Text(
                        text = stringResource(R.string.chat_header_new_conversation_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.xSmall)) {
            items(visibleConversations, key = { it.id.value }) { conversation ->
                val selected = conversation.id == selectedConversationId
                HistoryConversationRow(
                    conversation = conversation,
                    selected = selected,
                    onSelect = { onConversationSelected(conversation.id) },
                    onRename = {
                        renameTarget = HistoryRenameTarget(
                            conversationId = conversation.id,
                            originalTitle = conversation.title,
                        )
                    },
                    onDelete = { deleteTarget = conversation },
                )
            }
        }
    }

    renameTarget?.let { target ->
        RenameConversationDialog(
            target = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                onConversationRenamed(target.conversationId, newTitle)
                renameTarget = null
            },
        )
    }

    deleteTarget?.let { conversation ->
        DeleteConversationDialog(
            conversation = conversation,
            isSelectedConversation = conversation.id == selectedConversationId,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                pendingDeletedConversationId = conversation.id
                onConversationDeleted(conversation.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HistoryConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    var menuExpanded by remember { mutableStateOf(false) }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelect,
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = spacing.small, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.widthIn(min = 140.dp),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_rename)) },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RenameConversationDialog(
    target: HistoryRenameTarget,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var titleInput by rememberSaveable(target.conversationId.value, target.originalTitle) {
        mutableStateOf(target.originalTitle)
    }
    val normalizedTitle = normalizeHistoryConversationTitle(titleInput)
    val canConfirm = normalizedTitle.isNotEmpty() && normalizedTitle != target.originalTitle

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.history_rename_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalChatUiSpacing.small)) {
                Text(
                    text = stringResource(R.string.history_rename_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.history_title_label)) },
                    supportingText = {
                        val supportingLabel = when {
                            normalizedTitle.isEmpty() -> stringResource(R.string.history_title_blank)
                            normalizedTitle == target.originalTitle -> stringResource(R.string.history_title_unchanged)
                            else -> stringResource(R.string.history_title_replace)
                        }
                        Text(supportingLabel)
                    },
                    isError = normalizedTitle.isEmpty(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizedTitle) },
                enabled = canConfirm,
            ) {
                Text(text = stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteConversationDialog(
    conversation: Conversation,
    isSelectedConversation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val message = if (isSelectedConversation) {
        stringResource(R.string.history_delete_selected_message, conversation.title)
    } else {
        stringResource(R.string.history_delete_message, conversation.title)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.history_delete_dialog_title)) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}
