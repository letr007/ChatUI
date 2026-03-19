package com.letr.chatui.data

import com.letr.chatui.data.contract.ConversationLifecycle
import com.letr.chatui.data.model.ChatSettings
import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.PersistedApiKeyState
import com.letr.chatui.data.repository.ConversationRepository
import com.letr.chatui.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryContractTest {
    @Test
    fun `repository creates a conversation only when the first message is sent`() {
        val repository = FakeConversationRepository()

        assertTrue(repository.observeConversations().value.isEmpty())

        val conversationId = runBlocking {
            repository.sendMessage(
                conversationId = null,
                content = "This first send should create the conversation",
            )
        }

        val storedConversation = repository.observeConversations().value.single()
        assertEquals(conversationId, storedConversation.id)
        assertEquals(
            "This first send should create the conversation",
            repository.observeMessages(conversationId).value.single().content,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `repository rejects blank first sends so empty conversations are never persisted`() {
        val repository = FakeConversationRepository()

        runBlocking {
            repository.sendMessage(conversationId = null, content = "   ")
        }
    }

    @Test
    fun `createConversation alone does not persist an empty conversation`() {
        val repository = FakeConversationRepository()

        runBlocking {
            val preparedConversation = repository.createConversation("Prepared first send")

            assertEquals("Prepared first send", preparedConversation.title)
            assertTrue(repository.observeConversations().value.isEmpty())
            assertTrue(repository.observeMessages(preparedConversation.id).value.isEmpty())
        }
    }

    @Test
    fun `drafts are stored per conversation`() {
        val repository = FakeConversationRepository()
        val firstConversationId = runBlocking { repository.sendMessage(null, "First chat") }
        val secondConversationId = runBlocking { repository.sendMessage(null, "Second chat") }

        runBlocking {
            repository.saveDraft(firstConversationId, "draft one")
            repository.saveDraft(secondConversationId, "draft two")
        }

        assertEquals("draft one", repository.observeDraft(firstConversationId).value?.content)
        assertEquals("draft two", repository.observeDraft(secondConversationId).value?.content)

        runBlocking {
            repository.clearDraft(firstConversationId)
        }

        assertNull(repository.observeDraft(firstConversationId).value)
        assertEquals("draft two", repository.observeDraft(secondConversationId).value?.content)
    }

    @Test
    fun `streaming updates a single assistant message row until terminal status`() {
        val repository = FakeConversationRepository()
        val conversationId = runBlocking { repository.sendMessage(null, "Prompt") }

        val assistantMessageId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, assistantMessageId, "Hel")
        repository.appendAssistantDelta(conversationId, assistantMessageId, "lo")
        repository.completeAssistantMessage(conversationId, assistantMessageId)

        val messages = repository.observeMessages(conversationId).value
        val assistantMessages = messages.filter { it.author == MessageAuthor.ASSISTANT }

        assertEquals(1, assistantMessages.size)
        assertEquals(assistantMessageId, assistantMessages.single().id)
        assertEquals("Hello", assistantMessages.single().content)
        assertEquals(MessageStatus.COMPLETE, assistantMessages.single().status)
        assertFalse(repository.observeHasActiveGeneration().value)
    }

    @Test
    fun `stop and regenerate follow latest reply semantics without branching`() {
        val repository = FakeConversationRepository()
        val conversationId = runBlocking { repository.sendMessage(null, "Prompt") }

        val firstAssistantId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, firstAssistantId, "Part")
        runBlocking {
            repository.stopGeneration(conversationId, firstAssistantId)
        }

        val cancelled = repository.observeMessages(conversationId).value.last()
        assertEquals(firstAssistantId, cancelled.id)
        assertEquals("Part", cancelled.content)
        assertEquals(MessageStatus.CANCELLED, cancelled.status)

        val regeneratedAssistantId = runBlocking {
            repository.regenerateLatestResponse(conversationId)
        }
        val messagesAfterRegenerate = repository.observeMessages(conversationId).value
        val assistantMessages = messagesAfterRegenerate.filter { it.author == MessageAuthor.ASSISTANT }

        assertEquals(1, assistantMessages.size)
        assertEquals(regeneratedAssistantId, assistantMessages.single().id)
        assertEquals(MessageStatus.STREAMING, assistantMessages.single().status)
        assertTrue(repository.observeHasActiveGeneration().value)
    }

    @Test
    fun `settings contract exposes an explicit snapshot for later layers`() {
        val settingsRepository = FakeSettingsRepository()

        val settings = runBlocking {
            settingsRepository.getChatSettings()
        }

        assertEquals("https://example.invalid/v1/", settings.apiBaseUrl)
        assertEquals("demo-model", settings.modelId)
        assertEquals(
            PersistedApiKeyState.Persisted(maskedValue = "••••cret"),
            settings.apiKeyState,
        )
    }
}

private class FakeConversationRepository : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val selectedConversationId = MutableStateFlow<ConversationId?>(null)
    private val hasActiveGeneration = MutableStateFlow(false)
    private val messagesByConversation = linkedMapOf<ConversationId, MutableStateFlow<List<Message>>>()
    private val draftsByConversation = linkedMapOf<ConversationId, MutableStateFlow<Draft?>>()
    private var conversationCounter = 0
    private var messageCounter = 0
    private var clock = 1_000L

    override fun observeConversations(): MutableStateFlow<List<Conversation>> = conversations

    override fun observeMessages(conversationId: ConversationId): MutableStateFlow<List<Message>> {
        return messageFlow(conversationId)
    }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> {
        return messageFlow(conversationId).value
    }

    override fun observeDraft(conversationId: ConversationId): MutableStateFlow<Draft?> {
        return draftFlow(conversationId)
    }

    override fun observeSelectedConversationId(): Flow<ConversationId?> = selectedConversationId

    override fun observeHasActiveGeneration(): MutableStateFlow<Boolean> = hasActiveGeneration

    override suspend fun createConversation(firstUserMessage: String): Conversation {
        return ConversationLifecycle.createConversationOnFirstSend(
            conversationId = ConversationId("conversation-${++conversationCounter}"),
            firstUserMessage = firstUserMessage,
            createdAtEpochMillis = nextTimestamp(),
        )
    }

    override suspend fun sendMessage(conversationId: ConversationId?, content: String): ConversationId {
        require(content.isNotBlank()) { "Blank sends must not create empty conversations." }

        val targetConversation = conversationId?.let(::existingConversation)
            ?: createConversation(content).also { createdConversation ->
                conversations.update { existing -> existing + createdConversation }
                messageFlow(createdConversation.id)
                draftFlow(createdConversation.id)
            }

        val now = nextTimestamp()
        val userMessage = Message(
            id = MessageId("message-${++messageCounter}"),
            conversationId = targetConversation.id,
            author = MessageAuthor.USER,
            content = content.trim(),
            status = MessageStatus.COMPLETE,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )

        messageFlow(targetConversation.id).update { existing -> existing + userMessage }
        updateConversationTimestamp(targetConversation.id, now)
        clearDraft(targetConversation.id)
        selectConversation(targetConversation.id)
        return targetConversation.id
    }

    override suspend fun selectConversation(conversationId: ConversationId?) {
        selectedConversationId.value = conversationId
    }

    override suspend fun renameConversation(conversationId: ConversationId, newTitle: String) {
        conversations.update { items ->
            items.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        title = newTitle.trim(),
                        updatedAtEpochMillis = nextTimestamp(),
                    )
                } else {
                    conversation
                }
            }
        }
    }

    override suspend fun deleteConversation(conversationId: ConversationId) {
        conversations.update { items -> items.filterNot { it.id == conversationId } }
        messagesByConversation.remove(conversationId)
        draftsByConversation.remove(conversationId)
        if (selectedConversationId.value == conversationId) {
            selectedConversationId.value = null
        }
        hasActiveGeneration.value = false
    }

    override suspend fun regenerateLatestResponse(conversationId: ConversationId): MessageId {
        val messages = messageFlow(conversationId).value
        val latestUserIndex = messages.indexOfLast { it.author == MessageAuthor.USER }
        require(latestUserIndex >= 0) { "A user message is required before regeneration." }

        messageFlow(conversationId).update { existing ->
            existing.filterIndexed { index, message ->
                index <= latestUserIndex || message.author != MessageAuthor.ASSISTANT
            }
        }

        return startAssistantStreaming(conversationId)
    }

    override suspend fun stopGeneration(conversationId: ConversationId, messageId: MessageId) {
        updateAssistantMessage(conversationId, messageId) { message ->
            ConversationLifecycle.cancelAssistantMessage(
                message = message,
                updatedAtEpochMillis = nextTimestamp(),
            )
        }
        hasActiveGeneration.value = false
    }

    override suspend fun saveDraft(conversationId: ConversationId, content: String) {
        draftFlow(conversationId).value = Draft(
            conversationId = conversationId,
            content = content,
            updatedAtEpochMillis = nextTimestamp(),
        )
    }

    override suspend fun clearDraft(conversationId: ConversationId) {
        draftFlow(conversationId).value = null
    }

    fun startAssistantStreaming(conversationId: ConversationId): MessageId {
        require(!hasActiveGeneration.value) { "Only one active generation is allowed globally." }

        val messageId = MessageId("message-${++messageCounter}")
        val started = ConversationLifecycle.startAssistantMessage(
            conversationId = conversationId,
            messageId = messageId,
            createdAtEpochMillis = nextTimestamp(),
        )

        messageFlow(conversationId).update { existing -> existing + started }
        hasActiveGeneration.value = true
        return messageId
    }

    fun appendAssistantDelta(conversationId: ConversationId, messageId: MessageId, delta: String) {
        updateAssistantMessage(conversationId, messageId) { message ->
            ConversationLifecycle.appendAssistantDelta(
                message = message,
                delta = delta,
                updatedAtEpochMillis = nextTimestamp(),
            )
        }
    }

    fun completeAssistantMessage(conversationId: ConversationId, messageId: MessageId) {
        updateAssistantMessage(conversationId, messageId) { message ->
            ConversationLifecycle.completeAssistantMessage(
                message = message,
                updatedAtEpochMillis = nextTimestamp(),
            )
        }
        hasActiveGeneration.value = false
    }

    private fun updateAssistantMessage(
        conversationId: ConversationId,
        messageId: MessageId,
        transform: (Message) -> Message,
    ) {
        messageFlow(conversationId).update { existing ->
            existing.map { message ->
                if (message.id == messageId) transform(message) else message
            }
        }
        updateConversationTimestamp(conversationId, nextTimestamp())
    }

    private fun existingConversation(conversationId: ConversationId): Conversation {
        return conversations.value.first { it.id == conversationId }
    }

    private fun updateConversationTimestamp(conversationId: ConversationId, updatedAtEpochMillis: Long) {
        conversations.update { items ->
            items.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(updatedAtEpochMillis = updatedAtEpochMillis)
                } else {
                    conversation
                }
            }
        }
    }

    private fun messageFlow(conversationId: ConversationId): MutableStateFlow<List<Message>> {
        return messagesByConversation.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
    }

    private fun draftFlow(conversationId: ConversationId): MutableStateFlow<Draft?> {
        return draftsByConversation.getOrPut(conversationId) { MutableStateFlow(null) }
    }

    private fun nextTimestamp(): Long = clock++
}

private class FakeSettingsRepository : SettingsRepository {
    private val settings = MutableStateFlow(
        ChatSettings(
            apiBaseUrl = "https://example.invalid/v1/",
            modelId = "demo-model",
            apiKeyState = PersistedApiKeyState.Persisted(maskedValue = "••••cret"),
        )
    )

    override fun observeChatSettings(): Flow<ChatSettings> = settings

    override suspend fun getChatSettings(): ChatSettings = settings.value

    override suspend fun updateNonSensitiveSettings(settings: NonSensitiveChatSettings) {
        this.settings.value = this.settings.value.copy(
            apiBaseUrl = settings.apiBaseUrl,
            modelId = settings.modelId,
        )
    }
}
