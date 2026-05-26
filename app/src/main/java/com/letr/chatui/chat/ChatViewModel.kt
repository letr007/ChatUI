@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.letr.chatui.chat

import android.content.res.Resources
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.letr.chatui.data.model.ActiveChatRuntimeConfig
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.data.remote.ChatCompletionRemoteClient
import com.letr.chatui.data.remote.OpenAiChatCompletionRemoteEvent
import com.letr.chatui.data.remote.OpenAiChatCompletionRemoteStreamingSession
import com.letr.chatui.data.repository.ActiveChatConfigSource
import com.letr.chatui.data.repository.AssistantStreamingRepository
import com.letr.chatui.data.repository.ConversationRepository
import com.letr.chatui.network.chatcompletions.OpenAiChatCompletionFailure
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfig
import com.letr.chatui.network.chatcompletions.OpenAiProviderConfigValidator
import com.letr.chatui.network.chatcompletions.toUiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class ChatUiState(
    val selectedConversationId: ConversationId? = null,
    val messages: List<Message> = emptyList(),
    val composerText: String = "",
    val pendingAttachmentUris: List<String> = emptyList(),
    val hasActiveGeneration: Boolean = false,
    val isGenerationLockedByAnotherConversation: Boolean = false,
    val sendEnabled: Boolean = false,
    val canStopGeneration: Boolean = false,
    val canRegenerate: Boolean = false,
    val generationState: ChatGenerationState = ChatGenerationState.Idle,
    val configFailure: OpenAiChatCompletionFailure? = null,
)

private data class ChatBaseState(
    val selectedConversationId: ConversationId?,
    val messages: List<Message>,
    val composerText: String,
    val pendingAttachmentUris: List<String>,
    val hasPersistedActiveGeneration: Boolean,
    val configFailure: OpenAiChatCompletionFailure?,
)

sealed interface ChatGenerationState {
    data object Idle : ChatGenerationState

    data object Sending : ChatGenerationState

    data object Streaming : ChatGenerationState

    data object Complete : ChatGenerationState

    data class Failed(val failure: OpenAiChatCompletionFailure) : ChatGenerationState

    data object Cancelled : ChatGenerationState
}

class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val streamingRepository: AssistantStreamingRepository,
    private val activeChatConfigSource: ActiveChatConfigSource,
    private val remoteClient: ChatCompletionRemoteClient,
    private val resources: Resources,
) : ViewModel() {
    private companion object {
        const val TAG = "ChatUI-Submit"
    }

    private val pendingComposerText = MutableStateFlow("")
    private val selectedConversationDraftOverride = MutableStateFlow<String?>(null)
    private val selectedConversationId = conversationRepository.observeSelectedConversationId()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val messages = selectedConversationId
        .flatMapLatest { conversationId ->
            observeMessagesOrEmpty(conversationId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val persistedDraftText = selectedConversationId
        .flatMapLatest { conversationId ->
            observeDraftTextOrEmpty(conversationId)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private val pendingAttachmentUris = MutableStateFlow<List<String>>(emptyList())

    private val activeConfig = activeChatConfigSource.observeActiveConfig()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            ActiveChatRuntimeConfig(apiBaseUrl = "", apiKey = null, modelId = ""),
        )

    private val hasPersistedActiveGeneration = conversationRepository.observeHasActiveGeneration()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val transientGenerationOverride = MutableStateFlow<ChatGenerationState?>(null)
    private val inFlightGeneration = MutableStateFlow<InFlightGeneration?>(null)
    private var launchInProgress = false
    private val composerText = combine(
        selectedConversationId,
        persistedDraftText,
        pendingComposerText,
        selectedConversationDraftOverride,
    ) { selectedId, persistedDraft, pendingComposer, draftOverride ->
        if (selectedId == null) pendingComposer else draftOverride ?: persistedDraft
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val baseState = combine(
        combine(activeConfig, hasPersistedActiveGeneration) { config, persistedActive ->
            config to persistedActive
        },
        selectedConversationId,
        messages,
        composerText,
        pendingAttachmentUris,
    ) { configAndGeneration, selectedId, messageList, composer, attachments ->
        val (config, persistedActive) = configAndGeneration
        ChatBaseState(
            selectedConversationId = selectedId,
            messages = messageList,
            composerText = composer,
            pendingAttachmentUris = attachments,
            hasPersistedActiveGeneration = persistedActive,
            configFailure = config.validationFailureOrNull(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ChatBaseState(
            selectedConversationId = null,
            messages = emptyList(),
            composerText = "",
            pendingAttachmentUris = emptyList(),
            hasPersistedActiveGeneration = false,
            configFailure = null,
        ),
    )

    val uiState: StateFlow<ChatUiState> = combine(
        baseState,
        transientGenerationOverride,
        inFlightGeneration,
    ) { baseState, transientOverride, inFlight ->
        val selectedId = baseState.selectedConversationId
        val messageList = baseState.messages
        val composerText = baseState.composerText
        val pendingAttachmentUris = baseState.pendingAttachmentUris
        val configFailure = baseState.configFailure
        val hasInFlightGeneration = inFlight != null
        val isGenerationLockedByAnotherConversation = inFlight?.conversationId != null && inFlight.conversationId != selectedId
        val hasAnyActiveGeneration = baseState.hasPersistedActiveGeneration || hasInFlightGeneration
        val generationState = transientOverride
            ?: deriveGenerationState(
                selectedConversationId = selectedId,
                messages = messageList,
                inFlightGeneration = inFlight,
            )

        ChatUiState(
            selectedConversationId = selectedId,
            messages = messageList,
            composerText = composerText,
            pendingAttachmentUris = pendingAttachmentUris,
            hasActiveGeneration = hasAnyActiveGeneration,
            isGenerationLockedByAnotherConversation = isGenerationLockedByAnotherConversation,
            sendEnabled = (composerText.isNotBlank() || pendingAttachmentUris.isNotEmpty()) &&
                !hasAnyActiveGeneration &&
                configFailure == null,
            canStopGeneration = inFlight?.conversationId == selectedId,
            canRegenerate = selectedId != null && !hasAnyActiveGeneration && configFailure == null && canRegenerate(messageList),
            generationState = generationState,
            configFailure = configFailure,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    init {
        viewModelScope.launch {
            var initialized = false
            selectedConversationId.collect {
                if (initialized) {
                    pendingComposerText.value = ""
                }
                selectedConversationDraftOverride.value = null
                pendingAttachmentUris.value = emptyList()
                transientGenerationOverride.value = null
                initialized = true
            }
        }
    }

    fun onAttachmentUrisSelected(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        pendingAttachmentUris.value = (pendingAttachmentUris.value + uriStrings).distinct()
    }

    fun removePendingAttachment(uriString: String) {
        pendingAttachmentUris.value = pendingAttachmentUris.value.filterNot { it == uriString }
    }

    fun onComposerTextChanged(text: String) {
        val conversationId = selectedConversationId.value
        viewModelScope.launch {
            transientGenerationOverride.value = null
            if (conversationId == null) {
                pendingComposerText.value = text
                return@launch
            }

            selectedConversationDraftOverride.value = text
        }
    }

    fun selectConversation(conversationId: ConversationId?) {
        viewModelScope.launch {
            persistSelectedConversationDraftIfNeeded()
            conversationRepository.selectConversation(conversationId)
        }
    }

    fun renameConversation(conversationId: ConversationId, newTitle: String) {
        val normalizedTitle = newTitle.trim()
        if (normalizedTitle.isEmpty()) {
            return
        }

        viewModelScope.launch {
            conversationRepository.renameConversation(conversationId, normalizedTitle)
        }
    }

    fun deleteConversation(conversationId: ConversationId) {
        viewModelScope.launch {
            val inFlight = inFlightGeneration.value
            if (inFlight?.conversationId == conversationId) {
                inFlight.session.cancel()
                inFlight.collectionJob?.cancel()
                inFlightGeneration.value = null
                transientGenerationOverride.value = null
            }

            conversationRepository.deleteConversation(conversationId)
        }
    }

    fun submitPrompt() {
        val composerText = uiState.value.composerText
        val composerAttachments = uiState.value.pendingAttachmentUris
        val currentConversationId = uiState.value.selectedConversationId
        val configFailure = uiState.value.configFailure
        Log.d(TAG, "submitPrompt textLength=${composerText.length} attachments=${composerAttachments.size} conversationId=${currentConversationId?.value}")
        if (composerText.isBlank() && composerAttachments.isEmpty()) {
            Log.d(TAG, "submitPrompt ignored because text and attachments are empty")
            return
        }
        if (uiState.value.hasActiveGeneration) {
            Log.d(TAG, "submitPrompt ignored because generation is active")
            return
        }
        if (launchInProgress) {
            Log.d(TAG, "submitPrompt ignored because launch is already in progress")
            return
        }
        if (configFailure != null) {
            Log.e(TAG, "submitPrompt blocked by config failure: $configFailure")
            transientGenerationOverride.value = ChatGenerationState.Failed(configFailure)
            return
        }
        launchInProgress = true

        viewModelScope.launch {
            try {
                transientGenerationOverride.value = null
                val conversationId = conversationRepository.sendMessage(
                    conversationId = currentConversationId,
                    content = composerText,
                    attachedImageUris = composerAttachments,
                )
                pendingComposerText.value = ""
                selectedConversationDraftOverride.value = ""
                pendingAttachmentUris.value = emptyList()
                val messagesForRemote = conversationRepository.getMessages(conversationId)
                Log.d(TAG, "startStreaming conversationId=${conversationId.value} messages=${messagesForRemote.size}")
                startStreaming(
                    conversationId = conversationId,
                    messagesForRemote = messagesForRemote,
                    precreatedAssistantMessageId = null,
                )
            } finally {
                launchInProgress = false
            }
        }
    }

    fun stopGeneration() {
        val inFlight = inFlightGeneration.value
        if (inFlight != null && inFlight.conversationId == uiState.value.selectedConversationId) {
            inFlight.session.cancel()
            return
        }

        val selectedConversationId = uiState.value.selectedConversationId ?: return
        val streamingAssistantMessage = uiState.value.messages.lastOrNull {
            it.author == MessageAuthor.ASSISTANT && it.status == MessageStatus.STREAMING
        } ?: return

        viewModelScope.launch {
            streamingRepository.stopGeneration(
                conversationId = selectedConversationId,
                messageId = streamingAssistantMessage.id,
            )
        }
    }

    fun regenerateLatestResponse() {
        val conversationId = uiState.value.selectedConversationId ?: return
        val configFailure = uiState.value.configFailure
        if (uiState.value.hasActiveGeneration) {
            return
        }
        if (launchInProgress) {
            return
        }
        if (configFailure != null) {
            transientGenerationOverride.value = ChatGenerationState.Failed(configFailure)
            return
        }
        if (!canRegenerate(uiState.value.messages)) {
            return
        }
        launchInProgress = true

        viewModelScope.launch {
            try {
                transientGenerationOverride.value = null
                conversationRepository.regenerateLatestResponse(conversationId)
                val messagesForRemote = conversationRepository.getMessages(conversationId)
                startStreaming(
                    conversationId = conversationId,
                    messagesForRemote = messagesForRemote,
                    precreatedAssistantMessageId = null,
                )
            } finally {
                launchInProgress = false
            }
        }
    }

    override fun onCleared() {
        inFlightGeneration.value?.session?.cancel()
        super.onCleared()
    }

    private suspend fun startStreaming(
        conversationId: ConversationId,
        messagesForRemote: List<Message>,
        precreatedAssistantMessageId: MessageId?,
    ) {
        try {
            val session = remoteClient.streamChatCompletion(messagesForRemote)
            val generation = InFlightGeneration(
                conversationId = conversationId,
                assistantMessageId = precreatedAssistantMessageId,
                session = session,
            )
            inFlightGeneration.value = generation
            generation.collectionJob = collectRemoteEvents(generation)
        } catch (throwable: Throwable) {
            handleStreamingFailure(
                conversationId = conversationId,
                assistantMessageId = precreatedAssistantMessageId,
                failure = throwable.toFailureOrCancelled(),
            )
        }
    }

    private fun collectRemoteEvents(generation: InFlightGeneration): Job {
        return viewModelScope.launch {
            try {
                generation.session.events.collect { event ->
                    when (event) {
                        OpenAiChatCompletionRemoteEvent.AssistantMessageStarted -> {
                            if (generation.assistantMessageId == null) {
                                generation.assistantMessageId = streamingRepository.startAssistantStreaming(generation.conversationId)
                            }
                        }

                        is OpenAiChatCompletionRemoteEvent.AssistantMessageDelta -> {
                            val assistantMessageId = ensureAssistantMessageId(generation)
                            streamingRepository.appendAssistantDelta(
                                conversationId = generation.conversationId,
                                messageId = assistantMessageId,
                                delta = event.deltaText,
                            )
                        }

                        is OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted -> {
                            transientGenerationOverride.value = null
                            val assistantMessageId = ensureAssistantMessageId(generation)
                            streamingRepository.completeAssistantMessage(
                                conversationId = generation.conversationId,
                                messageId = assistantMessageId,
                            )
                            completeGeneration(generation)
                        }

                        is OpenAiChatCompletionRemoteEvent.AssistantMessageFailed -> {
                            val assistantMessageId = generation.assistantMessageId
                            if (assistantMessageId != null) {
                                streamingRepository.failAssistantMessage(
                                    conversationId = generation.conversationId,
                                    messageId = assistantMessageId,
                                    failureReason = event.failure.toUiMessage(resources),
                                )
                                transientGenerationOverride.value = null
                            } else {
                                transientGenerationOverride.value = ChatGenerationState.Failed(event.failure)
                            }
                            completeGeneration(generation)
                        }

                        is OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled -> {
                            val assistantMessageId = generation.assistantMessageId
                            if (assistantMessageId != null) {
                                streamingRepository.stopGeneration(
                                    conversationId = generation.conversationId,
                                    messageId = assistantMessageId,
                                )
                                transientGenerationOverride.value = null
                            } else {
                                transientGenerationOverride.value = ChatGenerationState.Cancelled
                            }
                            completeGeneration(generation)
                        }
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException && !isActive) {
                    throw throwable
                }
                handleStreamingFailure(
                    conversationId = generation.conversationId,
                    assistantMessageId = generation.assistantMessageId,
                    failure = throwable.toFailureOrCancelled(),
                )
            } finally {
                completeGeneration(generation)
            }
        }
    }

    private suspend fun handleStreamingFailure(
        conversationId: ConversationId,
        assistantMessageId: MessageId?,
        failure: OpenAiChatCompletionFailure,
    ) {
        currentCoroutineContext().ensureActive()
        if (assistantMessageId != null) {
            when (failure) {
                OpenAiChatCompletionFailure.Cancelled -> {
                    streamingRepository.stopGeneration(conversationId, assistantMessageId)
                    transientGenerationOverride.value = null
                }

                else -> {
                    streamingRepository.failAssistantMessage(
                        conversationId = conversationId,
                        messageId = assistantMessageId,
                        failureReason = failure.toUiMessage(resources),
                    )
                    transientGenerationOverride.value = null
                }
            }
        } else {
            transientGenerationOverride.value = when (failure) {
                OpenAiChatCompletionFailure.Cancelled -> ChatGenerationState.Cancelled
                else -> ChatGenerationState.Failed(failure)
            }
        }
    }

    private suspend fun ensureAssistantMessageId(generation: InFlightGeneration): MessageId {
        val existing = generation.assistantMessageId
        if (existing != null) {
            return existing
        }

        val generatedId = streamingRepository.startAssistantStreaming(generation.conversationId)
        generation.assistantMessageId = generatedId
        return generatedId
    }

    private fun completeGeneration(generation: InFlightGeneration) {
        if (inFlightGeneration.value === generation) {
            inFlightGeneration.value = null
        }
    }

    private fun observeMessagesOrEmpty(conversationId: ConversationId?): Flow<List<Message>> {
        return if (conversationId == null) {
            MutableStateFlow(emptyList())
        } else {
            conversationRepository.observeMessages(conversationId)
        }
    }

    private fun observeDraftTextOrEmpty(conversationId: ConversationId?): Flow<String> {
        return if (conversationId == null) {
            MutableStateFlow("")
        } else {
            conversationRepository.observeDraft(conversationId).map { it?.content.orEmpty() }
        }
    }

    private suspend fun persistSelectedConversationDraftIfNeeded() {
        val conversationId = selectedConversationId.value ?: return
        val overrideText = selectedConversationDraftOverride.value ?: return
        if (overrideText.isBlank()) {
            conversationRepository.clearDraft(conversationId)
        } else {
            conversationRepository.saveDraft(conversationId, overrideText)
        }
    }

    private fun deriveGenerationState(
        selectedConversationId: ConversationId?,
        messages: List<Message>,
        inFlightGeneration: InFlightGeneration?,
    ): ChatGenerationState {
        if (selectedConversationId != null && inFlightGeneration?.conversationId == selectedConversationId) {
            return if (inFlightGeneration.assistantMessageId == null) {
                ChatGenerationState.Sending
            } else {
                ChatGenerationState.Streaming
            }
        }

        val lastMessage = messages.lastOrNull() ?: return ChatGenerationState.Idle
        if (lastMessage.author != MessageAuthor.ASSISTANT) {
            return ChatGenerationState.Idle
        }

        return when (lastMessage.status) {
            MessageStatus.PENDING -> ChatGenerationState.Sending
            MessageStatus.STREAMING -> ChatGenerationState.Streaming
            MessageStatus.COMPLETE -> ChatGenerationState.Complete
            MessageStatus.FAILED -> ChatGenerationState.Failed(
                OpenAiChatCompletionFailure.Unknown(lastMessage.failureReason)
            )
            MessageStatus.CANCELLED -> ChatGenerationState.Cancelled
        }
    }

    private fun canRegenerate(messages: List<Message>): Boolean {
        val latestUserIndex = messages.indexOfLast { it.author == MessageAuthor.USER }
        if (latestUserIndex < 0) {
            return false
        }

        return messages.drop(latestUserIndex + 1).any { it.author == MessageAuthor.ASSISTANT }
    }

    private fun ActiveChatRuntimeConfig.validationFailureOrNull(): OpenAiChatCompletionFailure? {
        return OpenAiProviderConfigValidator.validate(
            OpenAiProviderConfig(
                baseUrl = apiBaseUrl,
                apiKey = apiKey,
                modelId = modelId,
            )
        )
    }

    private fun Throwable.toFailureOrCancelled(): OpenAiChatCompletionFailure {
        return when (this) {
            is CancellationException -> OpenAiChatCompletionFailure.Cancelled
            else -> OpenAiChatCompletionFailure.Unknown(message)
        }
    }

    private class InFlightGeneration(
        val conversationId: ConversationId,
        var assistantMessageId: MessageId?,
        val session: OpenAiChatCompletionRemoteStreamingSession,
        var collectionJob: Job? = null,
    )
}
