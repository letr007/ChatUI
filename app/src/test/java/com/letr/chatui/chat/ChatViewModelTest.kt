package com.letr.chatui.chat

import org.robolectric.RuntimeEnvironment
import com.letr.chatui.data.model.ActiveChatRuntimeConfig
import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `first send creates conversation clears pending draft and completes stream`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Hello world", "Hello world"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted("Hello world", "stop"),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("First prompt")
        advanceUntilIdle()

        assertEquals("First prompt", viewModel.uiState.value.composerText)
        assertTrue(viewModel.uiState.value.sendEnabled)

        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value
        assertEquals(ConversationId("conversation-1"), conversationId)
        assertTrue(repository.savedDraftEvents.isEmpty())
        assertEquals(listOf("First prompt"), remoteClient.streamRequests.single().map { it.content })
        assertEquals(ChatGenerationState.Complete, viewModel.uiState.value.generationState)
        assertEquals("", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.hasActiveGeneration)
        assertEquals(MessageStatus.COMPLETE, repository.messagesFlow(conversationId!!).value.last().status)
        assertEquals("Hello world", repository.messagesFlow(conversationId).value.last().content)

    }

    @Test
    fun `selected conversation restoration loads persisted messages and draft`() = runTest(dispatcher) {
        val repository = FakeConversationRepository(
            initialSelectedConversationId = ConversationId("conversation-2"),
            initialConversations = listOf(
                Conversation(
                    id = ConversationId("conversation-2"),
                    title = "Restored",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                )
            ),
            initialMessages = mapOf(
                ConversationId("conversation-2") to mutableListOf(
                    userMessage(ConversationId("conversation-2"), "user-1", "Saved prompt", 1L),
                    assistantMessage(ConversationId("conversation-2"), "assistant-1", "Saved reply", MessageStatus.COMPLETE, 2L),
                )
            ),
            initialDrafts = mapOf(
                ConversationId("conversation-2") to Draft(
                    conversationId = ConversationId("conversation-2"),
                    content = "Saved draft",
                    updatedAtEpochMillis = 3L,
                )
            ),
        )
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            remoteClient = FakeRemoteClient(),
        )

        advanceUntilIdle()

        assertEquals(ConversationId("conversation-2"), viewModel.uiState.value.selectedConversationId)
        assertEquals(listOf("Saved prompt", "Saved reply"), viewModel.uiState.value.messages.map { it.content })
        assertEquals("Saved draft", viewModel.uiState.value.composerText)
        assertTrue(viewModel.uiState.value.canRegenerate)
        assertEquals(ChatGenerationState.Complete, viewModel.uiState.value.generationState)

    }

    @Test
    fun `ui state exposes current model id from active config`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val viewModel = createViewModel(
            repository = repository,
            streamingRepository = FakeAssistantStreamingRepository(repository),
            configSource = FakeActiveChatConfigSource(
                initialConfig = ActiveChatRuntimeConfig(
                    apiBaseUrl = "https://api.example.com/v1",
                    apiKey = "sk-test",
                    modelId = "gpt-5.4",
                )
            ),
            remoteClient = FakeRemoteClient(),
        )

        advanceUntilIdle()

        assertEquals("gpt-5.4", viewModel.uiState.value.currentModelId)
    }

    @Test
    fun `switching selected conversation restores each conversation draft`() = runTest(dispatcher) {
        val firstId = ConversationId("conversation-1")
        val secondId = ConversationId("conversation-2")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = firstId,
            initialConversations = listOf(
                Conversation(firstId, "First", 1L, 1L),
                Conversation(secondId, "Second", 2L, 2L),
            ),
            initialDrafts = mapOf(
                firstId to Draft(firstId, "draft one", 3L),
                secondId to Draft(secondId, "draft two", 4L),
            ),
        )
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            remoteClient = FakeRemoteClient(),
        )

        advanceUntilIdle()
        assertEquals("draft one", viewModel.uiState.value.composerText)

        viewModel.selectConversation(secondId)
        advanceUntilIdle()
        assertEquals("draft two", viewModel.uiState.value.composerText)

        viewModel.selectConversation(firstId)
        advanceUntilIdle()
        assertEquals("draft one", viewModel.uiState.value.composerText)

    }

    @Test
    fun `typing in selected conversation defers draft persistence until conversation switch`() = runTest(dispatcher) {
        val firstId = ConversationId("conversation-1")
        val secondId = ConversationId("conversation-2")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = firstId,
            initialConversations = listOf(
                Conversation(firstId, "First", 1L, 1L),
                Conversation(secondId, "Second", 2L, 2L),
            ),
            initialDrafts = mapOf(
                firstId to Draft(firstId, "draft one", 3L),
                secondId to Draft(secondId, "draft two", 4L),
            ),
        )
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            remoteClient = FakeRemoteClient(),
        )

        advanceUntilIdle()
        viewModel.onComposerTextChanged("edited draft")
        advanceUntilIdle()

        assertEquals("edited draft", viewModel.uiState.value.composerText)
        assertTrue(repository.savedDraftEvents.isEmpty())

        viewModel.selectConversation(secondId)
        advanceUntilIdle()

        assertEquals(listOf(firstId to "edited draft"), repository.savedDraftEvents)
        assertEquals("draft two", viewModel.uiState.value.composerText)
    }

    @Test
    fun `stop generation preserves partial text and exposes cancelled terminal state`() = runTest(dispatcher) {
        val conversationId = ConversationId("conversation-1")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = conversationId,
            initialConversations = listOf(Conversation(conversationId, "Chat", 1L, 1L)),
            initialMessages = mapOf(
                conversationId to mutableListOf(
                    userMessage(conversationId, "user-1", "Prompt", 1L),
                    assistantMessage(conversationId, "assistant-1", "Part", MessageStatus.STREAMING, 2L),
                )
            ),
        )
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient()
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        advanceUntilIdle()

        viewModel.stopGeneration()
        advanceUntilIdle()

        val assistantMessage = repository.messagesFlow(conversationId).value.last { it.author == MessageAuthor.ASSISTANT }
        assertEquals("Part", assistantMessage.content)
        assertEquals(MessageStatus.CANCELLED, assistantMessage.status)
        assertEquals(ChatGenerationState.Cancelled, viewModel.uiState.value.generationState)
        assertFalse(viewModel.uiState.value.hasActiveGeneration)

    }

    @Test
    fun `stop generation cancels in flight remote stream and keeps partial assistant text`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasActiveGeneration)
        viewModel.stopGeneration()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        val assistantMessage = repository.messagesFlow(conversationId).value.last { it.author == MessageAuthor.ASSISTANT }
        assertTrue(remoteClient.lastSession?.cancelled == true)
        assertEquals("Part", assistantMessage.content)
        assertEquals(MessageStatus.CANCELLED, assistantMessage.status)
        assertEquals(ChatGenerationState.Cancelled, viewModel.uiState.value.generationState)
        assertFalse(viewModel.uiState.value.hasActiveGeneration)
    }

    @Test
    fun `regenerate latest assistant response replaces latest assistant row and streams replacement`() = runTest(dispatcher) {
        val conversationId = ConversationId("conversation-1")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = conversationId,
            initialConversations = listOf(Conversation(conversationId, "Chat", 1L, 1L)),
            initialMessages = mapOf(
                conversationId to mutableListOf(
                    userMessage(conversationId, "user-1", "Prompt", 1L),
                    assistantMessage(conversationId, "assistant-1", "Old reply", MessageStatus.COMPLETE, 2L),
                )
            ),
        )
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient()
        remoteClient.enqueue(
            OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("New", "New"),
            OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted("New reply", "stop"),
        )
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        advanceUntilIdle()
        viewModel.regenerateLatestResponse()
        advanceUntilIdle()

        val messages = repository.messagesFlow(conversationId).value
        val assistantMessages = messages.filter { it.author == MessageAuthor.ASSISTANT }
        assertEquals(1, assistantMessages.size)
        assertEquals(MessageId("assistant-stream-1"), assistantMessages.single().id)
        assertEquals("New", assistantMessages.single().content)
        assertEquals(MessageStatus.COMPLETE, assistantMessages.single().status)
        assertEquals(listOf("Prompt"), remoteClient.streamRequests.single().map { it.content })
        assertEquals(ChatGenerationState.Complete, viewModel.uiState.value.generationState)

    }

    @Test
    fun `terminal remote failure preserves partial text in repository and failed state in ui`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Almost", "Almost"),
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "Almost",
                    failure = OpenAiChatCompletionFailure.Timeout,
                ),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        val assistantMessage = repository.messagesFlow(conversationId).value.last()
        assertEquals("Almost", assistantMessage.content)
        assertEquals(MessageStatus.FAILED, assistantMessage.status)
        assertEquals("请求超时。请检查网络连接后重试。", assistantMessage.failureReason)
        assertEquals(
            ChatGenerationState.Failed(OpenAiChatCompletionFailure.Unknown("请求超时。请检查网络连接后重试。")),
            viewModel.uiState.value.generationState,
        )

    }

    @Test
    fun `unauthorized and rate limit failures surface deterministic recovery copy`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "",
                    failure = OpenAiChatCompletionFailure.Unauthorized,
                ),
            )
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "",
                    failure = OpenAiChatCompletionFailure.RateLimited(retryAfterSeconds = 8),
                ),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt 1")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        assertEquals(
            "未授权 (401)。请在设置中更新 API 密钥，保存后重试。",
            repository.messagesFlow(conversationId).value.last().failureReason,
        )

        viewModel.onComposerTextChanged("Prompt 2")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        assertEquals(
            "请求过于频繁 (429)。请等待 8 秒后重试。",
            repository.messagesFlow(conversationId).value.last().failureReason,
        )
    }

    @Test
    fun `protocol failures surface malformed stream recovery copy`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageFailed(
                    accumulatedText = "Part",
                    failure = OpenAiChatCompletionFailure.Protocol("Stream interrupted before terminal event."),
                ),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        assertEquals(
            "流式响应格式错误：Stream interrupted before terminal event. 请重试；如果问题持续，请检查服务提供方是否兼容。",
            repository.messagesFlow(conversationId).value.last().failureReason,
        )
    }

    @Test
    fun `missing config blocks send cleanly and does not start remote request`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val remoteClient = FakeRemoteClient()
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            configSource = FakeActiveChatConfigSource(
                ActiveChatRuntimeConfig(apiBaseUrl = "", apiKey = null, modelId = "")
            ),
            remoteClient = remoteClient,
        )

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.sendEnabled)
        viewModel.submitPrompt()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.generationState is ChatGenerationState.Failed)
        assertEquals(
            OpenAiChatCompletionFailure.MissingConfiguration("baseUrl"),
            (viewModel.uiState.value.generationState as ChatGenerationState.Failed).failure,
        )
        assertTrue(repository.conversationsFlow.value.isEmpty())
        assertTrue(remoteClient.streamRequests.isEmpty())

    }

    @Test
    fun `one active generation at a time blocks concurrent sends`() = runTest(dispatcher) {
        val repository = FakeConversationRepository(
            initialSelectedConversationId = ConversationId("conversation-existing"),
            initialConversations = listOf(Conversation(ConversationId("conversation-existing"), "Existing", 1L, 1L)),
            initialMessages = mapOf(
                ConversationId("conversation-existing") to mutableListOf(
                    userMessage(ConversationId("conversation-existing"), "user-1", "Existing prompt", 1L),
                    assistantMessage(ConversationId("conversation-existing"), "assistant-1", "Typing", MessageStatus.STREAMING, 2L),
                )
            ),
        )
        val remoteClient = FakeRemoteClient()
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            remoteClient = remoteClient,
        )

        advanceUntilIdle()
        viewModel.onComposerTextChanged("Should not send")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasActiveGeneration)
        assertFalse(viewModel.uiState.value.sendEnabled)

        viewModel.submitPrompt()
        advanceUntilIdle()

        assertEquals(2, repository.messagesFlow(ConversationId("conversation-existing")).value.size)
        assertTrue(remoteClient.streamRequests.isEmpty())

    }

    @Test
    fun `rapid duplicate submit only starts one outbound stream`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()

        viewModel.submitPrompt()
        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        val userMessages = repository.messagesFlow(conversationId).value.filter { it.author == MessageAuthor.USER }
        assertEquals(1, userMessages.size)
        assertEquals(1, remoteClient.streamRequests.size)
    }

    @Test
    fun `switching conversations during active stream exposes locked state and blocks send`() = runTest(dispatcher) {
        val firstConversationId = ConversationId("conversation-1")
        val secondConversationId = ConversationId("conversation-2")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = secondConversationId,
            initialConversations = listOf(
                Conversation(firstConversationId, "First", 1L, 1L),
                Conversation(secondConversationId, "Second", 2L, 2L),
            ),
        )
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
            )
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.selectConversation(firstConversationId)
        advanceUntilIdle()
        viewModel.onComposerTextChanged("Prompt in first")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        viewModel.selectConversation(secondConversationId)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isGenerationLockedByAnotherConversation)
        assertFalse(viewModel.uiState.value.sendEnabled)

        viewModel.onComposerTextChanged("should stay blocked")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        assertEquals(1, remoteClient.streamRequests.size)
    }

    @Test
    fun `stream startup exception fails cleanly without leaving active generation locked`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            streamThrowable = IOException("adapter boom")
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        assertFalse(viewModel.uiState.value.hasActiveGeneration)
        assertTrue(repository.conversationsFlow.value.isNotEmpty())
        assertEquals(
            listOf(MessageAuthor.USER),
            repository.messagesFlow(conversationId).value.map { it.author },
        )
    }

    @Test
    fun `stream collection exception marks assistant failed and clears active generation`() = runTest(dispatcher) {
        val repository = FakeConversationRepository()
        val streamingRepository = FakeAssistantStreamingRepository(repository)
        val remoteClient = FakeRemoteClient().apply {
            enqueue(
                OpenAiChatCompletionRemoteEvent.AssistantMessageStarted,
                OpenAiChatCompletionRemoteEvent.AssistantMessageDelta("Part", "Part"),
            )
            eventsThrowable = IOException("collector boom")
        }
        val viewModel = createViewModel(repository, streamingRepository, remoteClient = remoteClient)

        viewModel.onComposerTextChanged("Prompt")
        advanceUntilIdle()
        viewModel.submitPrompt()
        advanceUntilIdle()

        val conversationId = repository.selectedConversationIdFlow.value!!
        val assistantMessage = repository.messagesFlow(conversationId).value.last()
        assertEquals(MessageStatus.FAILED, assistantMessage.status)
        assertEquals("请求失败：collector boom。请重试。", assistantMessage.failureReason)
        assertFalse(viewModel.uiState.value.hasActiveGeneration)
    }

    @Test
    fun `rename conversation trims title through repository`() = runTest(dispatcher) {
        val conversationId = ConversationId("conversation-1")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = conversationId,
            initialConversations = listOf(Conversation(conversationId, "Original", 1L, 1L)),
        )
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            remoteClient = FakeRemoteClient(),
        )

        advanceUntilIdle()
        viewModel.renameConversation(conversationId, "  Renamed title  ")
        advanceUntilIdle()

        assertEquals("Renamed title", repository.conversationsFlow.value.single().title)
    }

    @Test
    fun `delete selected conversation clears selection and returns ui to empty chat state`() = runTest(dispatcher) {
        val conversationId = ConversationId("conversation-1")
        val repository = FakeConversationRepository(
            initialSelectedConversationId = conversationId,
            initialConversations = listOf(Conversation(conversationId, "Chat", 1L, 1L)),
            initialMessages = mapOf(
                conversationId to mutableListOf(
                    userMessage(conversationId, "user-1", "Prompt", 1L),
                    assistantMessage(conversationId, "assistant-1", "Reply", MessageStatus.COMPLETE, 2L),
                )
            ),
            initialDrafts = mapOf(
                conversationId to Draft(conversationId, "Saved draft", 3L)
            ),
        )
        val viewModel = createViewModel(
            repository,
            FakeAssistantStreamingRepository(repository),
            remoteClient = FakeRemoteClient(),
        )

        advanceUntilIdle()
        viewModel.deleteConversation(conversationId)
        advanceUntilIdle()

        assertTrue(repository.conversationsFlow.value.isEmpty())
        assertEquals(null, viewModel.uiState.value.selectedConversationId)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertEquals("", viewModel.uiState.value.composerText)
        assertFalse(viewModel.uiState.value.hasActiveGeneration)
        assertEquals(ChatGenerationState.Idle, viewModel.uiState.value.generationState)
    }

    private fun createViewModel(
        repository: FakeConversationRepository,
        streamingRepository: FakeAssistantStreamingRepository,
        configSource: FakeActiveChatConfigSource = FakeActiveChatConfigSource(),
        remoteClient: FakeRemoteClient,
    ): ChatViewModel {
        return ChatViewModel(
            conversationRepository = repository,
            streamingRepository = streamingRepository,
            activeChatConfigSource = configSource,
            remoteClient = remoteClient,
            resources = RuntimeEnvironment.getApplication().resources,
        )
    }

    private class FakeActiveChatConfigSource(
        initialConfig: ActiveChatRuntimeConfig = ActiveChatRuntimeConfig(
            apiBaseUrl = "https://api.example.com/v1",
            apiKey = "sk-test",
            modelId = "gpt-4o-mini",
        )
    ) : ActiveChatConfigSource {
        private val flow = MutableStateFlow(initialConfig)

        override fun observeActiveConfig(): Flow<ActiveChatRuntimeConfig> = flow

        override suspend fun getActiveConfig(): ActiveChatRuntimeConfig = flow.value
    }

    private class FakeRemoteClient : ChatCompletionRemoteClient {
        val streamRequests = mutableListOf<List<Message>>()
        private val queuedEvents = ArrayDeque<List<OpenAiChatCompletionRemoteEvent>>()
        var lastSession: FakeStreamingSession? = null
        var streamThrowable: Throwable? = null
        var eventsThrowable: Throwable? = null

        fun enqueue(vararg events: OpenAiChatCompletionRemoteEvent) {
            queuedEvents.addLast(events.toList())
        }

        override fun streamChatCompletion(messages: List<Message>): OpenAiChatCompletionRemoteStreamingSession {
            streamThrowable?.let { throw it }
            streamRequests += messages
            val session = FakeStreamingSession(
                scriptedEvents = queuedEvents.removeFirstOrNull().orEmpty(),
                terminalThrowable = eventsThrowable,
            )
            lastSession = session
            return session
        }
    }

    private class FakeStreamingSession(
        private val scriptedEvents: List<OpenAiChatCompletionRemoteEvent>,
        private val terminalThrowable: Throwable? = null,
    ) : OpenAiChatCompletionRemoteStreamingSession {
        var cancelled = false
        private val channel = Channel<OpenAiChatCompletionRemoteEvent>(Channel.UNLIMITED)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        override val events: Flow<OpenAiChatCompletionRemoteEvent> = channel.receiveAsFlow()

        init {
            scope.launch {
                val hasTerminalEvent = scriptedEvents.any {
                    it is OpenAiChatCompletionRemoteEvent.AssistantMessageCompleted ||
                        it is OpenAiChatCompletionRemoteEvent.AssistantMessageFailed ||
                        it is OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled
                }
                for (event in scriptedEvents) {
                    if (cancelled) {
                        channel.trySend(OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "Part"))
                        channel.close()
                        return@launch
                    }
                    channel.send(event)
                }
                if (terminalThrowable != null) {
                    channel.close(terminalThrowable)
                    return@launch
                }
                if (hasTerminalEvent) {
                    channel.close()
                }
            }
        }

        override fun cancel() {
            cancelled = true
            channel.trySend(OpenAiChatCompletionRemoteEvent.AssistantMessageCancelled(accumulatedText = "Part"))
            channel.close()
            scope.cancel()
        }
    }

    private class FakeAssistantStreamingRepository(
        private val conversationRepository: FakeConversationRepository,
    ) : AssistantStreamingRepository {
        private var streamCounter = 0

        override suspend fun startAssistantStreaming(conversationId: ConversationId): MessageId {
            val messageId = MessageId("assistant-stream-${++streamCounter}")
            conversationRepository.upsertAssistant(
                Message(
                    id = messageId,
                    conversationId = conversationId,
                    author = MessageAuthor.ASSISTANT,
                    content = "",
                    status = MessageStatus.STREAMING,
                    createdAtEpochMillis = streamCounter.toLong(),
                    updatedAtEpochMillis = streamCounter.toLong(),
                )
            )
            return messageId
        }

        override suspend fun appendAssistantDelta(conversationId: ConversationId, messageId: MessageId, delta: String) {
            val current = conversationRepository.getMessage(conversationId, messageId)
            conversationRepository.upsertAssistant(
                current.copy(content = current.content + delta, updatedAtEpochMillis = current.updatedAtEpochMillis + 1)
            )
        }

        override suspend fun completeAssistantMessage(conversationId: ConversationId, messageId: MessageId) {
            val current = conversationRepository.getMessage(conversationId, messageId)
            conversationRepository.upsertAssistant(
                current.copy(status = MessageStatus.COMPLETE, updatedAtEpochMillis = current.updatedAtEpochMillis + 1)
            )
        }

        override suspend fun failAssistantMessage(conversationId: ConversationId, messageId: MessageId, failureReason: String?) {
            val current = conversationRepository.getMessage(conversationId, messageId)
            conversationRepository.upsertAssistant(
                current.copy(
                    status = MessageStatus.FAILED,
                    failureReason = failureReason,
                    updatedAtEpochMillis = current.updatedAtEpochMillis + 1,
                )
            )
        }

        override suspend fun stopGeneration(conversationId: ConversationId, messageId: MessageId) {
            val current = conversationRepository.getMessage(conversationId, messageId)
            conversationRepository.upsertAssistant(
                current.copy(status = MessageStatus.CANCELLED, updatedAtEpochMillis = current.updatedAtEpochMillis + 1)
            )
        }
    }

    private class FakeConversationRepository(
        initialSelectedConversationId: ConversationId? = null,
        initialConversations: List<Conversation> = emptyList(),
        initialMessages: Map<ConversationId, MutableList<Message>> = emptyMap(),
        initialDrafts: Map<ConversationId, Draft> = emptyMap(),
    ) : ConversationRepository {
        val conversationsFlow = MutableStateFlow(initialConversations)
        val selectedConversationIdFlow = MutableStateFlow(initialSelectedConversationId)
        private val messageFlows = initialMessages.mapValues { MutableStateFlow(it.value.toList()) }.toMutableMap()
        private val draftFlows = initialDrafts
            .mapValues<ConversationId, Draft, MutableStateFlow<Draft?>> { (_, draft) -> MutableStateFlow<Draft?>(draft) }
            .toMutableMap()
        val savedDraftEvents = mutableListOf<Pair<ConversationId, String>>()

        private var conversationCounter = initialConversations.size
        private var userMessageCounter = initialMessages.values.sumOf { it.size }

        override fun observeConversations(): Flow<List<Conversation>> = conversationsFlow

        override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> = messagesFlow(conversationId)

        override suspend fun getMessages(conversationId: ConversationId): List<Message> = messagesFlow(conversationId).value

        override fun observeDraft(conversationId: ConversationId): Flow<Draft?> = draftFlow(conversationId)

        override fun observeSelectedConversationId(): Flow<ConversationId?> = selectedConversationIdFlow

        override fun observeHasActiveGeneration(): Flow<Boolean> {
            return activeGenerationFlow
        }

        override suspend fun createConversation(firstUserMessage: String): Conversation {
            val conversationId = ConversationId("conversation-${++conversationCounter}")
            return Conversation(conversationId, firstUserMessage.trim(), conversationCounter.toLong(), conversationCounter.toLong())
        }

        override suspend fun sendMessage(
            conversationId: ConversationId?,
            content: String,
            attachedImageUris: List<String>,
        ): ConversationId {
            val trimmed = content.trim()
            val targetConversationId = conversationId ?: ConversationId("conversation-${++conversationCounter}").also { createdId ->
                conversationsFlow.value = conversationsFlow.value + Conversation(createdId, trimmed, conversationCounter.toLong(), conversationCounter.toLong())
            }
            val updatedMessages = messagesFlow(targetConversationId).value.toMutableList().apply {
                add(
                    buildUserMessage(targetConversationId, "user-${++userMessageCounter}", trimmed, userMessageCounter.toLong())
                        .copy(attachedImageUris = attachedImageUris)
                )
            }
            messagesFlow(targetConversationId).value = updatedMessages
            draftFlow(targetConversationId).value = null
            selectedConversationIdFlow.value = targetConversationId
            refreshActiveGenerationFlow()
            return targetConversationId
        }

        override suspend fun selectConversation(conversationId: ConversationId?) {
            selectedConversationIdFlow.value = conversationId
        }

        override suspend fun renameConversation(conversationId: ConversationId, newTitle: String) {
            conversationsFlow.update { conversations ->
                conversations.map { if (it.id == conversationId) it.copy(title = newTitle.trim()) else it }
            }
        }

        override suspend fun deleteConversation(conversationId: ConversationId) {
            conversationsFlow.update { conversations -> conversations.filterNot { it.id == conversationId } }
            messageFlows.remove(conversationId)
            draftFlows.remove(conversationId)
            if (selectedConversationIdFlow.value == conversationId) {
                selectedConversationIdFlow.value = null
            }
            refreshActiveGenerationFlow()
        }

        override suspend fun regenerateLatestResponse(conversationId: ConversationId) {
            val remaining = messagesFlow(conversationId).value.toMutableList()
            val latestUserIndex = remaining.indexOfLast { it.author == MessageAuthor.USER }
            val assistantMessagesToRemove = remaining.drop(latestUserIndex + 1).filter { it.author == MessageAuthor.ASSISTANT }
            remaining.removeAll(assistantMessagesToRemove.toSet())
            messagesFlow(conversationId).value = remaining
            refreshActiveGenerationFlow()
        }

        override suspend fun stopGeneration(conversationId: ConversationId, messageId: MessageId) {
            val current = getMessage(conversationId, messageId)
            upsertAssistant(current.copy(status = MessageStatus.CANCELLED, updatedAtEpochMillis = current.updatedAtEpochMillis + 1))
        }

        override suspend fun saveDraft(conversationId: ConversationId, content: String) {
            savedDraftEvents += conversationId to content
            draftFlow(conversationId).value = Draft(conversationId, content, savedDraftEvents.size.toLong())
        }

        override suspend fun clearDraft(conversationId: ConversationId) {
            draftFlow(conversationId).value = null
        }

        fun messagesFlow(conversationId: ConversationId): MutableStateFlow<List<Message>> {
            return messageFlows.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        }

        fun upsertAssistant(message: Message) {
            val updated = messagesFlow(message.conversationId).value.toMutableList()
            val existingIndex = updated.indexOfFirst { it.id == message.id }
            if (existingIndex >= 0) {
                updated[existingIndex] = message
            } else {
                updated += message
            }
            messagesFlow(message.conversationId).value = updated
            refreshActiveGenerationFlow()
        }

        fun getMessage(conversationId: ConversationId, messageId: MessageId): Message {
            return messagesFlow(conversationId).value.first { it.id == messageId }
        }

        private val activeGenerationFlow = MutableStateFlow(hasActiveGeneration())

        private fun draftFlow(conversationId: ConversationId): MutableStateFlow<Draft?> {
            return draftFlows.getOrPut(conversationId) { MutableStateFlow<Draft?>(null) }
        }

        private fun hasActiveGeneration(): Boolean {
            return messageFlows.values.any { flow -> flow.value.any { it.status == MessageStatus.STREAMING } }
        }

        private fun refreshActiveGenerationFlow() {
            activeGenerationFlow.value = hasActiveGeneration()
        }

        private fun buildUserMessage(
            conversationId: ConversationId,
            id: String,
            content: String,
            timestamp: Long,
        ) = Message(
            id = MessageId(id),
            conversationId = conversationId,
            author = MessageAuthor.USER,
            content = content,
            status = MessageStatus.COMPLETE,
            createdAtEpochMillis = timestamp,
            updatedAtEpochMillis = timestamp,
        )
    }

    private fun userMessage(
        conversationId: ConversationId,
        id: String,
        content: String,
        timestamp: Long,
    ) = Message(
        id = MessageId(id),
        conversationId = conversationId,
        author = MessageAuthor.USER,
        content = content,
        status = MessageStatus.COMPLETE,
        createdAtEpochMillis = timestamp,
        updatedAtEpochMillis = timestamp,
    )

    private fun assistantMessage(
        conversationId: ConversationId,
        id: String,
        content: String,
        status: MessageStatus,
        timestamp: Long,
    ) = Message(
        id = MessageId(id),
        conversationId = conversationId,
        author = MessageAuthor.ASSISTANT,
        content = content,
        status = status,
        createdAtEpochMillis = timestamp,
        updatedAtEpochMillis = timestamp,
    )
}
