package com.letr.chatui.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private const val rootScreenLogTag = "RootScreen"

private enum class TranscriptFollowMode {
    FollowingLatest,
    DetachedByUser,
}

@Composable
@Preview
fun RootScreen(
    appShellController: AppShellController,
    conversations: List<Conversation>,
    chatUiState: ChatUiState,
    onConversationSelected: (ConversationId) -> Unit,
    onConversationRenamed: (ConversationId, String) -> Unit,
    onConversationDeleted: (ConversationId) -> Unit,
    onComposerTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onAttachmentUrisSelected: (List<String>) -> Unit,
    onPendingAttachmentRemoved: (String) -> Unit,
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
                onStartNewConversation = onStartNewConversation,
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
                            onAttachmentUrisSelected = onAttachmentUrisSelected,
                            onPendingAttachmentRemoved = onPendingAttachmentRemoved,
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
                    FloatingTopActionButton(onClick = onHistoryClick) {
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
                    FloatingTopActionButton(onClick = onSettingsClick) {
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
private fun FloatingTopActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val corners = LocalChatUiCorners
    var hasSettled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasSettled = true
    }

    val settledAlpha by animateFloatAsState(
        targetValue = if (hasSettled) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "floatingTopActionAlpha",
    )
    val settledScale by animateFloatAsState(
        targetValue = if (hasSettled) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 820f),
        label = "floatingTopActionScale",
    )
    val settledTranslationY by animateFloatAsState(
        targetValue = if (hasSettled) 0f else -8f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "floatingTopActionTranslationY",
    )

    Surface(
        modifier = Modifier.graphicsLayer {
            alpha = settledAlpha
            scaleX = settledScale
            scaleY = settledScale
            translationY = settledTranslationY
        },
        shape = corners.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
        ),
    ) {
        IconButton(
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            content = content,
        )
    }
}

@Composable
private fun ChatHomeSurface(
    chatUiState: ChatUiState,
    onComposerTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onAttachmentUrisSelected: (List<String>) -> Unit,
    onPendingAttachmentRemoved: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateLatestResponse: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val listState = rememberLazyListState()
    val floatingComposerHeight = if (chatUiState.pendingAttachmentUris.isEmpty()) 78.dp else 144.dp
    val floatingComposerBottomPadding = spacing.medium
    val transcriptBottomPadding = floatingComposerHeight + floatingComposerBottomPadding + spacing.large
    val pendingAssistantPlaceholderVisible = chatUiState.generationState == ChatGenerationState.Sending
    val transcriptBottomAnchorIndex by remember(chatUiState.messages.size, pendingAssistantPlaceholderVisible) {
        derivedStateOf {
            chatUiState.messages.size + if (pendingAssistantPlaceholderVisible) 1 else 0
        }
    }
    var transcriptFollowMode by rememberSaveable(chatUiState.selectedConversationId?.value) {
        mutableStateOf(TranscriptFollowMode.FollowingLatest)
    }
    val transcriptNestedScrollConnection = remember(listState, transcriptBottomAnchorIndex) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && !listState.isAtTranscriptBottom(transcriptBottomAnchorIndex)) {
                    transcriptFollowMode = TranscriptFollowMode.DetachedByUser
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(chatUiState.generationState) {
        if (chatUiState.generationState == ChatGenerationState.Sending) {
            transcriptFollowMode = TranscriptFollowMode.FollowingLatest
        }
    }

    LaunchedEffect(listState, transcriptBottomAnchorIndex) {
        snapshotFlow { listState.isAtTranscriptBottom(transcriptBottomAnchorIndex) }
            .collect { isAtBottom ->
                if (isAtBottom) {
                    transcriptFollowMode = TranscriptFollowMode.FollowingLatest
                }
            }
    }

    LaunchedEffect(
        transcriptBottomAnchorIndex,
        chatUiState.messages.lastOrNull()?.content?.length,
        chatUiState.generationState,
        transcriptFollowMode,
    ) {
        if (chatUiState.messages.isNotEmpty() && transcriptFollowMode == TranscriptFollowMode.FollowingLatest) {
            listState.scrollToItem(transcriptBottomAnchorIndex)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(transcriptNestedScrollConnection),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = transcriptBottomPadding),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        items(chatUiState.messages, key = { it.id.value }) { message ->
                            MessageBubble(
                                message = message,
                                isLatestAssistant = message.id == chatUiState.messages.lastOrNull { it.author == MessageAuthor.ASSISTANT }?.id,
                                canRegenerate = chatUiState.canRegenerate,
                                onRegenerateLatestResponse = onRegenerateLatestResponse,
                            )
                        }

                        if (pendingAssistantPlaceholderVisible) {
                            item(key = "pending-assistant-placeholder") {
                                PendingAssistantBubble()
                            }
                        }

                        item(key = "transcript-bottom-anchor") {
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }

        ComposerBar(
            chatUiState = chatUiState,
            onComposerTextChanged = onComposerTextChanged,
            onSubmitPrompt = onSubmitPrompt,
            onAttachmentUrisSelected = onAttachmentUrisSelected,
            onPendingAttachmentRemoved = onPendingAttachmentRemoved,
            onStopGeneration = onStopGeneration,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = floatingComposerBottomPadding),
        )
    }
}

@Composable
private fun PendingAssistantBubble() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onRegenerateLatestResponse: () -> Unit,
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
    if (
        message.author == MessageAuthor.ASSISTANT &&
        (message.status == MessageStatus.PENDING ||
            (message.status == MessageStatus.STREAMING && message.content.isBlank()))
    ) {
        PendingAssistantBubble()
        return
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
                current += 1
                visibleAssistantTextLengthState.value = current
                delay(24)
            }
        } else {
            visibleAssistantTextLengthState.value = targetLength
        }
    }

    val animatedVisibleLength by animateIntAsState(
        targetValue = if (message.author == MessageAuthor.ASSISTANT) visibleAssistantTextLength else message.content.length,
        animationSpec = tween(
            durationMillis = if (message.status == MessageStatus.STREAMING) 120 else 180,
            easing = LinearEasing,
        ),
        label = "assistantVisibleLength",
    )
    val animatedAssistantText = if (message.author == MessageAuthor.ASSISTANT) {
        message.content.take(animatedVisibleLength.coerceIn(0, message.content.length))
    } else {
        message.content
    }
    val shouldRenderAssistantMarkdown = message.author == MessageAuthor.ASSISTANT &&
        message.status != MessageStatus.PENDING &&
        messageLooksLikeStructuredMarkdown(animatedAssistantText)
    val markdownDocument = remember(animatedAssistantText, shouldRenderAssistantMarkdown) {
        if (shouldRenderAssistantMarkdown) {
            AssistantMarkdownParser.parse(animatedAssistantText)
        } else {
            null
        }
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
                .then(
                    if (isUser) {
                        Modifier.widthIn(max = 320.dp)
                    } else {
                        Modifier.fillMaxWidth(0.9f)
                    }
                )
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
                    horizontal = spacing.medium,
                    vertical = spacing.small,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            if (isUser && message.attachedImageUris.isNotEmpty()) {
                UriImageStrip(
                    uriStrings = message.attachedImageUris,
                    removable = false,
                    thumbnailSize = 112.dp,
                )
            }

            if (markdownDocument != null && markdownDocument.blocks.isNotEmpty()) {
                AssistantMarkdownContent(
                    document = markdownDocument,
                    allowCodeFence = message.status == MessageStatus.COMPLETE,
                    contentColor = contentColor,
                )
            } else {
                val fallbackText = animatedAssistantText.ifBlank {
                    if (message.author == MessageAuthor.ASSISTANT && message.status == MessageStatus.STREAMING) {
                        stringResource(R.string.thinking)
                    } else {
                        ""
                    }
                }
                if (fallbackText.isNotBlank()) {
                    Text(
                        text = fallbackText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                    )
                }
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
                        Icon(
                            painter = painterResource(R.drawable.ic_copy),
                            contentDescription = stringResource(R.string.copy_code),
                            modifier = Modifier.size(16.dp),
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

            }
        }
    }
}

private fun LazyListState.isAtTranscriptBottom(bottomAnchorIndex: Int): Boolean {
    if (bottomAnchorIndex < 0) return true
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return true
    val bottomAnchorItem = visibleItems.firstOrNull { it.index == bottomAnchorIndex } ?: return false
    val viewportBottom = layoutInfo.viewportEndOffset
    val bottomGap = viewportBottom - (bottomAnchorItem.offset + bottomAnchorItem.size)
    return bottomGap >= -24
}

private fun messageLooksLikeStructuredMarkdown(content: String): Boolean {
    if (content.isBlank()) return false
    val trimmed = content.trim()
    val hasFenceLine = trimmed.lineSequence().any { line ->
        line.trimStart().startsWith("```")
    }
    if (hasFenceLine && trimmed.count { it == '`' } >= 3) {
        return true
    }

    return trimmed.lineSequence().any { line ->
        val candidate = line.trimStart()
        candidate.startsWith("#") ||
            candidate.startsWith(">") ||
            candidate.startsWith("- ") ||
            candidate.startsWith("* ") ||
            candidate.matches(Regex("\\d+\\.\\s+.+"))
    }
}

@Composable
private fun AssistantMarkdownContent(
    document: MarkdownDocument,
    allowCodeFence: Boolean,
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
                    if (allowCodeFence) {
                        AssistantCodeBlock(
                            block = block,
                            contentColor = contentColor,
                        )
                    } else {
                        Text(
                            text = buildString {
                                append("```")
                                if (!block.language.isNullOrBlank()) {
                                    append(block.language)
                                }
                                appendLine()
                                append(block.code)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
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
@OptIn(ExperimentalLayoutApi::class)
private fun ComposerBar(
    chatUiState: ChatUiState,
    onComposerTextChanged: (String) -> Unit,
    onSubmitPrompt: () -> Unit,
    onAttachmentUrisSelected: (List<String>) -> Unit,
    onPendingAttachmentRemoved: (String) -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    val isComposerFocused by interactionSource.collectIsFocusedAsState()
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure { error ->
                Log.w(rootScreenLogTag, "Failed to persist read permission for $uri", error)
            }
        }
        onAttachmentUrisSelected(uris.map(Uri::toString))
    }
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
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    val isComposerExpanded = isComposerFocused || isImeVisible
    val composerWidthFraction by animateFloatAsState(
        targetValue = if (isComposerExpanded) 0.96f else 0.82f,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 420f,
        ),
        label = "composerWidthFraction",
    )
    val composerControlHeight = 34.dp
    val composerVerticalPadding by animateDpAsState(
        targetValue = 3.dp,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = 760f,
        ),
        label = "composerVerticalPadding",
    )
    val composerHorizontalPadding by animateDpAsState(
        targetValue = if (isComposerExpanded) spacing.large else spacing.medium,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 420f,
        ),
        label = "composerHorizontalPadding",
    )
    val composerSideGap by animateDpAsState(
        targetValue = if (isComposerExpanded) spacing.medium else spacing.xSmall,
        animationSpec = spring(
            dampingRatio = 0.84f,
            stiffness = 460f,
        ),
        label = "composerSideGap",
    )
    val composerCardScale by animateFloatAsState(
        targetValue = if (isComposerExpanded) 1f else 0.996f,
        animationSpec = spring(
            dampingRatio = 0.9f,
            stiffness = 700f,
        ),
        label = "composerCardScale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(composerWidthFraction),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            AnimatedVisibility(
                visible = attachmentMenuExpanded,
                enter = fadeIn(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ) + slideInVertically(
                    initialOffsetY = { it / 3 },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                ),
                exit = fadeOut(
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                ) + slideOutVertically(
                    targetOffsetY = { it / 4 },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ) + scaleOut(
                    targetScale = 0.98f,
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                AttachmentChooserPanel(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !chatUiState.hasActiveGeneration,
                    onCameraClick = {
                        attachmentMenuExpanded = false
                    },
                    onGalleryClick = {
                        attachmentMenuExpanded = false
                        attachmentPickerLauncher.launch(arrayOf("image/*"))
                    },
                )
            }

            Column(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = composerCardScale
                        scaleY = composerCardScale
                    }
                    .clip(corners.large)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                        shape = corners.large,
                    )
                    .padding(vertical = composerVerticalPadding),
                verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
            ) {
                if (chatUiState.pendingAttachmentUris.isNotEmpty()) {
                    Box(
                        modifier = Modifier.padding(horizontal = composerHorizontalPadding),
                    ) {
                        UriImageStrip(
                            uriStrings = chatUiState.pendingAttachmentUris,
                            removable = true,
                            thumbnailSize = 56.dp,
                            onRemove = onPendingAttachmentRemoved,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { attachmentMenuExpanded = !attachmentMenuExpanded },
                        enabled = !chatUiState.hasActiveGeneration,
                        modifier = Modifier.size(composerControlHeight),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = if (attachmentMenuExpanded) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.attach_image),
                        )
                    }

                    Spacer(modifier = Modifier.width(composerSideGap))

                    BasicTextField(
                        value = composerFieldValue,
                        onValueChange = {
                            composerFieldValue = it
                            onComposerTextChanged(it.text)
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = composerControlHeight),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = if (chatUiState.hasActiveGeneration) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        minLines = 1,
                        maxLines = 5,
                        enabled = !chatUiState.hasActiveGeneration,
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (composerFieldValue.text.isEmpty() && chatUiState.configFailure != null) {
                                    Text(
                                        text = stringResource(R.string.openai_failure_label_settings_required),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    Spacer(modifier = Modifier.width(composerSideGap))

                    val showStopButton = chatUiState.canStopGeneration
                    val stopContentDescription = stringResource(R.string.stop)
                    IconButton(
                        onClick = if (showStopButton) onStopGeneration else onSubmitPrompt,
                        enabled = if (showStopButton) true else chatUiState.sendEnabled,
                        modifier = Modifier.size(composerControlHeight),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showStopButton) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                            contentColor = if (showStopButton) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                        ),
                    ) {
                        if (showStopButton) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                                    .background(MaterialTheme.colorScheme.error)
                                    .semantics {
                                        contentDescription = stopContentDescription
                                    }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.send_prompt),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChooserPanel(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners

    Surface(
        modifier = modifier,
        shape = corners.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.small, vertical = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            AttachmentChooserOption(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.attachment_menu_camera),
                enabled = false,
                onClick = onCameraClick,
            )
            AttachmentChooserOption(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Menu,
                label = stringResource(R.string.attachment_menu_gallery),
                enabled = enabled,
                onClick = onGalleryClick,
            )
        }
    }
}

@Composable
private fun AttachmentChooserOption(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val spacing = LocalChatUiSpacing
    val corners = LocalChatUiCorners
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    }
    val iconContainerColor = if (enabled) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.54f)
    }

    Surface(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        shape = corners.medium,
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.1f else 0.06f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.small, vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = corners.medium,
                color = iconContainerColor,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun UriImageStrip(
    uriStrings: List<String>,
    removable: Boolean,
    thumbnailSize: Dp,
    onRemove: (String) -> Unit = {},
) {
    val spacing = LocalChatUiSpacing
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        uriStrings.forEach { uriString ->
            UriImageThumbnail(
                uriString = uriString,
                size = thumbnailSize,
                removable = removable,
                onRemove = { onRemove(uriString) },
            )
        }
    }
}

@Composable
private fun UriImageThumbnail(
    uriString: String,
    size: Dp,
    removable: Boolean,
    onRemove: () -> Unit,
) {
    val corners = LocalChatUiCorners

    Box(
        modifier = Modifier
            .size(size)
            .clip(corners.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                shape = corners.medium,
            ),
    ) {
        UriPreviewImage(
            uriString = uriString,
            modifier = Modifier.fillMaxSize(),
        )

        if (removable) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                shape = LocalChatUiCorners.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.remove_attachment),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UriPreviewImage(
    uriString: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap!!,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
