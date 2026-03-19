package com.letr.chatui.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import com.letr.chatui.R
import com.letr.chatui.app.AppDestination
import com.letr.chatui.app.AppShellController
import com.letr.chatui.chat.ChatGenerationState
import com.letr.chatui.chat.ChatUiState
import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.history.HistoryDrawer
import com.letr.chatui.network.chatcompletions.toUiMessage
import com.letr.chatui.settings.SettingsScreen
import com.letr.chatui.settings.SettingsUiState
import com.letr.chatui.ui.theme.LocalChatUiCorners
import com.letr.chatui.ui.theme.LocalChatUiSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun RootScreen(
    appShellController: AppShellController,
    conversations: List<Conversation>,
    chatUiState: ChatUiState,
    onConversationSelected: (ConversationId) -> Unit,
    onConversationRenamed: (ConversationId, String) -> Unit,
    onConversationDeleted: (ConversationId) -> Unit,
    onComposerTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onStartNewConversation: () -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLatestResponse: () -> Unit,
    settingsUiState: SettingsUiState,
    onSettingsApiBaseUrlChanged: (String) -> Unit,
    onSettingsModelIdChanged: (String) -> Unit,
    onSettingsApiKeyChanged: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onClearPersistedApiKey: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)

    LaunchedEffect(appShellController.isHistoryDrawerOpen) {
        if (appShellController.isHistoryDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistoryDrawer(
                conversations = conversations,
                selectedConversationId = appShellController.selectedConversationId,
                onConversationSelected = onConversationSelected,
                onConversationRenamed = onConversationRenamed,
                onConversationDeleted = onConversationDeleted,
                onClose = appShellController::closeHistoryDrawer,
            )
        },
        gesturesEnabled = appShellController.currentDestination == AppDestination.CHAT,
    ) {
        Scaffold(
            topBar = {
                RootTopBar(
                    destination = appShellController.currentDestination,
                    onHistoryClick = appShellController::openHistoryDrawer,
                    onSettingsClick = appShellController::navigateToSettings,
                    onBackToChatClick = appShellController::navigateToChat,
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        horizontal = if (appShellController.currentDestination == AppDestination.CHAT) spacing.small else spacing.large,
                        vertical = if (appShellController.currentDestination == AppDestination.CHAT) 0.dp else spacing.medium,
                    ),
            ) {
                when (appShellController.currentDestination) {
                    AppDestination.CHAT -> {
                        ChatHomeSurface(
                            chatUiState = chatUiState,
                            onComposerTextChanged = onComposerTextChanged,
                            onSubmitPrompt = onSubmitPrompt,
                            onStartNewConversation = onStartNewConversation,
                            onStopGeneration = onStopGeneration,
                            onRegenerateLatestResponse = onRegenerateLatestResponse,
                            onOpenHistory = appShellController::openHistoryDrawer,
                            onOpenSettings = appShellController::navigateToSettings,
                        )
                    }

                    AppDestination.SETTINGS -> {
                        SettingsScreen(
                            uiState = settingsUiState,
                            onApiBaseUrlChanged = onSettingsApiBaseUrlChanged,
                            onModelIdChanged = onSettingsModelIdChanged,
                            onApiKeyInputChanged = onSettingsApiKeyChanged,
                            onSave = onSaveSettings,
                            onClearApiKey = onClearPersistedApiKey,
                            onBackToChat = appShellController::navigateToChat,
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed && appShellController.isHistoryDrawerOpen) {
            appShellController.closeHistoryDrawer()
        }
    }
}

@Composable
private fun RootTopBar(
    destination: AppDestination,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBackToChatClick: () -> Unit,
) {
    val spacing = LocalChatUiSpacing

    Surface(
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.small, vertical = spacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            when (destination) {
                AppDestination.CHAT -> {
                    IconButton(onClick = onHistoryClick) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.history_drawer_title),
                        )
                    }
                }

                AppDestination.SETTINGS -> {
                    IconButton(onClick = onBackToChatClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_to_chat),
                        )
                    }
                }
            }

            if (destination == AppDestination.SETTINGS) {
                Text(
                    text = stringResource(R.string.settings_nav_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            when (destination) {
                AppDestination.CHAT -> {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings_button),
                        )
                    }
                }

                AppDestination.SETTINGS -> {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatHomeSurface(
    chatUiState: ChatUiState,
    onComposerTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onStartNewConversation: () -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLatestResponse: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val listState = rememberLazyListState()
    val floatingComposerHeight = 72.dp
    val floatingComposerBottomPadding = spacing.medium
    val transcriptBottomPadding = floatingComposerHeight + floatingComposerBottomPadding + spacing.large

    LaunchedEffect(chatUiState.messages.size, chatUiState.messages.lastOrNull()?.content, chatUiState.generationState) {
        if (chatUiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(chatUiState.messages.lastIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = spacing.small),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
        ) {
            CompactStatusBanner(
                chatUiState = chatUiState,
                onOpenHistory = onOpenHistory,
                onOpenSettings = onOpenSettings,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (chatUiState.messages.isEmpty()) {
                    EmptyTranscriptState(
                        chatUiState = chatUiState,
                        onOpenSettings = onOpenSettings,
                        onOpenHistory = onOpenHistory,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = transcriptBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        items(chatUiState.messages, key = { it.id.value }) { message ->
                            MessageBubble(
                                message = message,
                                isLatestAssistant = message.id == chatUiState.messages.lastOrNull { it.author == MessageAuthor.ASSISTANT }?.id,
                                canRegenerate = chatUiState.canRegenerate,
                                canStopGeneration = chatUiState.canStopGeneration,
                                onRegenerateLatestResponse = onRegenerateLatestResponse,
                                onStopGeneration = onStopGeneration,
                            )
                        }
                    }
                }
            }
        }

        ComposerBar(
            chatUiState = chatUiState,
            onStartNewConversation = onStartNewConversation,
            onComposerTextChanged = onComposerTextChanged,
            onSubmitPrompt = onSubmitPrompt,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = floatingComposerBottomPadding),
        )
    }
}

@Composable
private fun CompactStatusBanner(
    chatUiState: ChatUiState,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val resources = LocalContext.current.resources
    val message = when {
        chatUiState.configFailure != null -> chatUiState.configFailure.toUiMessage(resources)
        chatUiState.isGenerationLockedByAnotherConversation -> stringResource(R.string.composer_locked_message)
        else -> null
    } ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalChatUiCorners.medium,
        color = if (chatUiState.configFailure != null) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium, vertical = spacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (chatUiState.configFailure != null) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (chatUiState.configFailure != null) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.open_settings),
                    )
                }
            }

            if (chatUiState.isGenerationLockedByAnotherConversation) {
                IconButton(onClick = onOpenHistory) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.history_drawer_title),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTranscriptState(
    chatUiState: ChatUiState,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val hasConfigFailure = chatUiState.configFailure != null
    val resources = LocalContext.current.resources

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(
                text = if (hasConfigFailure) {
                    stringResource(R.string.empty_state_setup_title)
                } else {
                    stringResource(R.string.chat_header_new_conversation_title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            if (hasConfigFailure) {
                Text(
                    text = chatUiState.configFailure?.toUiMessage(resources)
                        ?: stringResource(R.string.empty_state_sending_blocked),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.open_settings),
                        )
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = stringResource(R.string.history_drawer_title),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isLatestAssistant: Boolean,
    canRegenerate: Boolean,
    canStopGeneration: Boolean,
    onRegenerateLatestResponse: () -> Unit,
    onStopGeneration: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    val isUser = message.author == MessageAuthor.USER
    val contentColor = when {
        isUser -> MaterialTheme.colorScheme.onSurface
        message.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val clipboardManager = LocalClipboardManager.current
    val shouldRenderAssistantMarkdown = message.author == MessageAuthor.ASSISTANT && message.status == MessageStatus.COMPLETE
    val markdownDocument = remember(message.content, shouldRenderAssistantMarkdown) {
        if (shouldRenderAssistantMarkdown) AssistantMarkdownParser.parse(message.content) else null
    }
    var hasEntered by remember(message.id.value) { mutableStateOf(false) }
    LaunchedEffect(message.id.value) {
        hasEntered = true
    }
    val revealProgress by animateFloatAsState(
        targetValue = if (hasEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "assistantMessageReveal",
    )
    val animatedAlpha = if (message.author == MessageAuthor.ASSISTANT) revealProgress else 1f
    val animatedTranslationY = if (message.author == MessageAuthor.ASSISTANT) (1f - revealProgress) * 18f else 0f
    val visibleAssistantTextLengthState = remember(message.id.value) { MutableStateFlow(message.content.length) }
    val visibleAssistantTextLength by visibleAssistantTextLengthState.collectAsState()

    LaunchedEffect(message.id.value, message.content, message.status, message.author) {
        if (message.author != MessageAuthor.ASSISTANT) {
            visibleAssistantTextLengthState.value = message.content.length
            return@LaunchedEffect
        }

        val targetLength = message.content.length
        if (message.status == MessageStatus.STREAMING) {
            var current = visibleAssistantTextLengthState.value.coerceAtMost(targetLength)
            while (current < targetLength) {
                current = nextAssistantRevealBoundary(
                    text = message.content,
                    startExclusive = current,
                ).coerceAtMost(targetLength)
                visibleAssistantTextLengthState.value = current
                delay(streamingRevealDelayMillis(message.content, current))
            }
        } else {
            visibleAssistantTextLengthState.value = targetLength
        }
    }

    val animatedVisibleLength by animateIntAsState(
        targetValue = if (message.author == MessageAuthor.ASSISTANT) visibleAssistantTextLength else message.content.length,
        animationSpec = tween(
            durationMillis = if (message.status == MessageStatus.STREAMING) 140 else 220,
            easing = FastOutSlowInEasing,
        ),
        label = "assistantVisibleLength",
    )
    val animatedAssistantText = if (message.author == MessageAuthor.ASSISTANT) {
        message.content.take(animatedVisibleLength.coerceIn(0, message.content.length))
    } else {
        message.content
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(animatedAlpha)
            .graphicsLayer { translationY = animatedTranslationY },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
    ) {
        Text(
            text = messageTimeLabel(message),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(if (isUser) corners.large else corners.medium)
                .background(
                    when {
                        isUser -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.26f)
                        message.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)
                        else -> Color.Transparent
                    }
                )
                .border(
                    width = if (isUser || message.status == MessageStatus.FAILED) 1.dp else 0.dp,
                    color = when {
                        isUser -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        message.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)
                        else -> Color.Transparent
                    },
                    shape = if (isUser) corners.large else corners.medium,
                )
                .padding(
                    horizontal = if (isUser || message.status == MessageStatus.FAILED) spacing.medium else 0.dp,
                    vertical = if (isUser || message.status == MessageStatus.FAILED) spacing.small else 0.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            if (markdownDocument != null && markdownDocument.blocks.isNotEmpty()) {
                AssistantMarkdownContent(
                    document = markdownDocument,
                    contentColor = contentColor,
                )
            } else {
                Text(
                    text = animatedAssistantText.ifBlank {
                        if (message.author == MessageAuthor.ASSISTANT && message.status == MessageStatus.STREAMING) {
                            stringResource(R.string.thinking)
                        } else {
                            ""
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
            }
            if (message.failureReason != null) {
                HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
                Text(
                    text = message.failureReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
        }

        if (message.author == MessageAuthor.ASSISTANT) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(horizontal = spacing.xSmall),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.content.isNotBlank()) {
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(message.content)) },
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(
                            text = "⧉",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (isLatestAssistant && canRegenerate) {
                    IconButton(
                        onClick = onRegenerateLatestResponse,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.regenerate),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                if (isLatestAssistant && canStopGeneration) {
                    IconButton(
                        onClick = onStopGeneration,
                        modifier = Modifier.size(32.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.stop),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantMarkdownContent(
    document: MarkdownDocument,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val spacing = LocalChatUiSpacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        document.blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = buildMarkdownParagraph(
                            text = block.text,
                            inlineCodeBackground = MaterialTheme.colorScheme.surface,
                            inlineCodeColor = contentColor,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                }

                is MarkdownBlock.Heading -> {
                    Text(
                        text = buildMarkdownParagraph(
                            text = block.text,
                            inlineCodeBackground = MaterialTheme.colorScheme.surface,
                            inlineCodeColor = contentColor,
                        ),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        color = contentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                is MarkdownBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        block.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                Text(text = "•", color = contentColor)
                                Text(
                                    text = buildMarkdownParagraph(
                                        text = item,
                                        inlineCodeBackground = MaterialTheme.colorScheme.surface,
                                        inlineCodeColor = contentColor,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.OrderedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                        block.items.forEachIndexed { index, item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                Text(text = "${index + 1}.", color = contentColor)
                                Text(
                                    text = buildMarkdownParagraph(
                                        text = item,
                                        inlineCodeBackground = MaterialTheme.colorScheme.surface,
                                        inlineCodeColor = contentColor,
                                    ),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.BlockQuote -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        Spacer(
                            modifier = Modifier
                                .width(3.dp)
                                .heightIn(min = 24.dp)
                                .clip(LocalChatUiCorners.medium)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        )
                        Text(
                            text = buildMarkdownParagraph(
                                text = block.text,
                                inlineCodeBackground = MaterialTheme.colorScheme.surface,
                                inlineCodeColor = contentColor,
                            ),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.88f),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }

                is MarkdownBlock.CodeFence -> {
                    AssistantCodeBlock(
                        block = block,
                        contentColor = contentColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantCodeBlock(
    block: MarkdownBlock.CodeFence,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    val clipboardManager = LocalClipboardManager.current
    val horizontalCodeScrollState = rememberScrollState()
    val verticalCodeScrollState = rememberScrollState()

    Card(
        shape = corners.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium, vertical = spacing.small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.language ?: stringResource(R.string.code_label_default),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
                TextButton(
                    onClick = { clipboardManager.setText(AnnotatedString(block.code)) },
                ) {
                    Text(text = stringResource(R.string.copy_code))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.medium)
                    .clip(corners.medium)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        shape = corners.medium,
                    )
                    .heightIn(max = 240.dp)
                    .horizontalScroll(horizontalCodeScrollState)
                    .verticalScroll(verticalCodeScrollState)
                    .padding(spacing.medium),
            ) {
                Text(
                    text = block.code.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(modifier = Modifier.height(spacing.xSmall))
        }
    }
}

private fun buildMarkdownParagraph(
    text: String,
    inlineCodeBackground: Color,
    inlineCodeColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val token = when {
                text.startsWith("**", index) -> "**"
                text.startsWith("__", index) -> "__"
                text.startsWith("*", index) -> "*"
                text.startsWith("_", index) -> "_"
                text.startsWith("`", index) -> "`"
                else -> null
            }

            if (token == null) {
                append(text[index])
                index += 1
                continue
            }

            val contentStart = index + token.length
            val markerEnd = text.indexOf(token, startIndex = contentStart)
            if (markerEnd == -1) {
                append(token)
                index += token.length
                continue
            }

            val inner = text.substring(contentStart, markerEnd)
            when (token) {
                "`" -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = inlineCodeBackground,
                            color = inlineCodeColor,
                        )
                    )
                    append(inner)
                    pop()
                }

                "**", "__" -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = inlineCodeColor))
                    append(inner)
                    pop()
                }

                "*", "_" -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = inlineCodeColor))
                    append(inner)
                    pop()
                }
            }
            index = markerEnd + token.length
        }
    }
}

@Composable
private fun ComposerBar(
    chatUiState: ChatUiState,
    onStartNewConversation: () -> Unit,
    onComposerTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    val composerControlHeight = 56.dp
    var composerFieldValue by rememberSaveable(
        chatUiState.selectedConversationId?.value,
        stateSaver = TextFieldValue.Saver,
    ) {
        mutableStateOf(TextFieldValue(chatUiState.composerText))
    }
    val shouldSyncExternalComposerText by remember(chatUiState.composerText, composerFieldValue) {
        derivedStateOf {
            chatUiState.composerText != composerFieldValue.text &&
                composerFieldValue.composition == null
        }
    }

    LaunchedEffect(chatUiState.composerText, shouldSyncExternalComposerText) {
        if (shouldSyncExternalComposerText) {
            composerFieldValue = TextFieldValue(
                text = chatUiState.composerText,
                selection = TextRange(
                    chatUiState.composerText.length.coerceAtMost(composerFieldValue.selection.end)
                ),
            )
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .clip(corners.large)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                shape = corners.large,
            )
            .padding(horizontal = spacing.small, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(
            onClick = onStartNewConversation,
            modifier = Modifier.size(composerControlHeight),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.chat_header_new_conversation_title),
            )
        }

        OutlinedTextField(
            value = composerFieldValue,
            onValueChange = {
                composerFieldValue = it
                onComposerTextChanged(it.text)
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = composerControlHeight),
            minLines = 1,
            maxLines = 5,
            enabled = !chatUiState.hasActiveGeneration,
            placeholder = if (chatUiState.configFailure != null) {
                {
                    Text(text = stringResource(R.string.openai_failure_label_settings_required))
                }
            } else {
                null
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
            ),
        )

        IconButton(
            onClick = onSubmitPrompt,
            enabled = chatUiState.sendEnabled,
            modifier = Modifier.size(composerControlHeight),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = stringResource(R.string.send_prompt),
            )
        }
    }
}

private fun messageTimeLabel(message: Message): String {
    val timestamp = when {
        message.updatedAtEpochMillis > 0L -> message.updatedAtEpochMillis
        message.createdAtEpochMillis > 0L -> message.createdAtEpochMillis
        else -> 0L
    }
    if (timestamp <= 0L) return ""

    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun nextAssistantRevealBoundary(
    text: String,
    startExclusive: Int,
): Int {
    if (startExclusive >= text.length) return text.length

    val maxChunkLength = 6
    var candidate = (startExclusive + maxChunkLength).coerceAtMost(text.length)
    while (candidate < text.length) {
        val previousChar = text[candidate - 1]
        if (previousChar.isWhitespace() || previousChar in ASSISTANT_REVEAL_BREAK_CHARS) {
            return candidate
        }
        candidate += 1
    }
    return text.length
}

private fun streamingRevealDelayMillis(
    text: String,
    visibleLength: Int,
): Long {
    if (visibleLength <= 0 || visibleLength > text.length) return 28L
    val lastChar = text[visibleLength - 1]
    return when {
        lastChar == '\n' -> 80L
        lastChar in setOf('。', '！', '？', '.', '!', '?') -> 72L
        lastChar in setOf('，', '、', ',', ';', '；', ':', '：') -> 52L
        lastChar.isWhitespace() -> 36L
        else -> 24L
    }
}

private val ASSISTANT_REVEAL_BREAK_CHARS = setOf(
    '。', '！', '？', '，', '、', '；', '：',
    '.', '!', '?', ',', ';', ':',
    ')', ']', '}', '」', '』', '》', '）',
)
