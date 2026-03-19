package com.letr.chatui.persistence

import android.content.Context
import androidx.room.Room
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.data.repository.RoomConversationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomConversationRepositoryTest {
    private lateinit var database: ChatUiDatabase
    private lateinit var localDataSource: RoomLocalConversationDataSource
    private lateinit var repository: RoomConversationRepository
    private var clock = 1_000L
    private var conversationCounter = 0
    private var messageCounter = 0

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, ChatUiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        localDataSource = RoomLocalConversationDataSource(database)
        repository = RoomConversationRepository(
            localDataSource = localDataSource,
            nowEpochMillis = { clock++ },
            conversationIdFactory = { ConversationId("conversation-${++conversationCounter}") },
            messageIdFactory = { MessageId("message-${++messageCounter}") },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `send creates conversation only on first send and clears draft`() = runBlocking {
        assertTrue(repository.observeConversations().first().isEmpty())

        val conversationId = repository.sendMessage(null, "  First user prompt  ")

        repository.saveDraft(conversationId, "draft to clear")
        repository.sendMessage(conversationId, "Follow up")

        val conversations = repository.observeConversations().first()
        val messages = repository.observeMessages(conversationId).first()

        assertEquals(1, conversations.size)
        assertEquals(conversationId, conversations.single().id)
        assertEquals("First user prompt", conversations.single().title)
        assertEquals(2, messages.size)
        assertEquals(listOf("First user prompt", "Follow up"), messages.map { it.content })
        assertNull(repository.observeDraft(conversationId).first())
    }

    @Test
    fun `blank first send does not persist empty conversation`() = runBlocking {
        try {
            repository.sendMessage(null, "   ")
            throw AssertionError("Expected blank first send to be rejected.")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.observeConversations().first().isEmpty())
        }
    }

    @Test
    fun `createConversation prepares metadata without persisting an empty conversation`() = runBlocking {
        val preparedConversation = repository.createConversation("First prepared prompt")

        assertEquals("First prepared prompt", preparedConversation.title)
        assertTrue(repository.observeConversations().first().isEmpty())
        assertTrue(repository.observeMessages(preparedConversation.id).first().isEmpty())

        val persistedConversationId = repository.sendMessage(null, "First prepared prompt")
        val persistedMessages = repository.observeMessages(persistedConversationId).first()

        assertEquals(1, repository.observeConversations().first().size)
        assertEquals(1, persistedMessages.size)
        assertEquals("First prepared prompt", persistedMessages.single().content)
    }

    @Test
    fun `conversations are ordered by last updated timestamp`() = runBlocking {
        val olderConversationId = repository.sendMessage(null, "Older chat")
        val newerConversationId = repository.sendMessage(null, "Newer chat")

        repository.sendMessage(olderConversationId, "Make this most recent")

        val orderedIds = repository.observeConversations().first().map { it.id }

        assertEquals(listOf(olderConversationId, newerConversationId), orderedIds)
    }

    @Test
    fun `rename and delete conversation update persistence and selected state`() = runBlocking {
        val firstConversationId = repository.sendMessage(null, "First")
        val secondConversationId = repository.sendMessage(null, "Second")

        repository.renameConversation(firstConversationId, "  Renamed title  ")
        repository.selectConversation(secondConversationId)
        repository.deleteConversation(secondConversationId)

        val conversations = repository.observeConversations().first()

        assertEquals(1, conversations.size)
        assertEquals("Renamed title", conversations.single().title)
        assertEquals(firstConversationId, conversations.single().id)
        assertNull(repository.observeSelectedConversationId().first())
    }

    @Test
    fun `drafts persist per conversation`() = runBlocking {
        val firstConversationId = repository.sendMessage(null, "First chat")
        val secondConversationId = repository.sendMessage(null, "Second chat")

        repository.saveDraft(firstConversationId, "draft one")
        repository.saveDraft(secondConversationId, "draft two")

        assertEquals("draft one", repository.observeDraft(firstConversationId).first()?.content)
        assertEquals("draft two", repository.observeDraft(secondConversationId).first()?.content)

        repository.clearDraft(firstConversationId)

        assertNull(repository.observeDraft(firstConversationId).first())
        assertEquals("draft two", repository.observeDraft(secondConversationId).first()?.content)
    }

    @Test
    fun `assistant streaming updates one row in place and toggles active generation`() = runBlocking {
        val conversationId = repository.sendMessage(null, "Prompt")

        val assistantMessageId = repository.startAssistantStreaming(conversationId)
        assertTrue(repository.observeHasActiveGeneration().first())

        repository.appendAssistantDelta(conversationId, assistantMessageId, "Hel")
        repository.appendAssistantDelta(conversationId, assistantMessageId, "lo")
        repository.completeAssistantMessage(conversationId, assistantMessageId)

        val assistantMessages = repository.observeMessages(conversationId).first()
            .filter { it.author == MessageAuthor.ASSISTANT }

        assertEquals(1, assistantMessages.size)
        assertEquals(assistantMessageId, assistantMessages.single().id)
        assertEquals("Hello", assistantMessages.single().content)
        assertEquals(MessageStatus.COMPLETE, assistantMessages.single().status)
        assertFalse(repository.observeHasActiveGeneration().first())
    }

    @Test
    fun `stop and regenerate keep partial text and replace latest assistant row without branching`() = runBlocking {
        val conversationId = repository.sendMessage(null, "Prompt")

        val firstAssistantId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, firstAssistantId, "Part")
        repository.stopGeneration(conversationId, firstAssistantId)

        val cancelledAssistant = repository.observeMessages(conversationId).first().last()
        assertEquals(firstAssistantId, cancelledAssistant.id)
        assertEquals("Part", cancelledAssistant.content)
        assertEquals(MessageStatus.CANCELLED, cancelledAssistant.status)

        val regeneratedAssistantId = repository.regenerateLatestResponse(conversationId)
        val assistantMessages = repository.observeMessages(conversationId).first()
            .filter { it.author == MessageAuthor.ASSISTANT }

        assertEquals(1, assistantMessages.size)
        assertEquals(regeneratedAssistantId, assistantMessages.single().id)
        assertEquals(MessageStatus.STREAMING, assistantMessages.single().status)
        assertEquals("", assistantMessages.single().content)
        assertTrue(repository.observeHasActiveGeneration().first())
    }

    @Test
    fun `regenerate replaces only latest user turn assistant response and keeps prior turns linear`() = runBlocking {
        val conversationId = repository.sendMessage(null, "First prompt")
        val firstAssistantId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, firstAssistantId, "First reply")
        repository.completeAssistantMessage(conversationId, firstAssistantId)

        repository.sendMessage(conversationId, "Second prompt")
        val secondAssistantId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, secondAssistantId, "Second reply")
        repository.completeAssistantMessage(conversationId, secondAssistantId)

        val regeneratedAssistantId = repository.regenerateLatestResponse(conversationId)
        val messages = repository.observeMessages(conversationId).first()
        val assistantsAfterLatestUser = messages.dropWhile { it.content != "Second prompt" }
            .drop(1)
            .filter { it.author == MessageAuthor.ASSISTANT }

        assertEquals(listOf("First prompt", "First reply", "Second prompt", ""), messages.map { it.content })
        assertFalse(messages.any { it.content == "Second reply" })
        assertEquals(1, assistantsAfterLatestUser.size)
        assertEquals(regeneratedAssistantId, assistantsAfterLatestUser.single().id)
        assertEquals(MessageStatus.STREAMING, assistantsAfterLatestUser.single().status)
        assertEquals("", assistantsAfterLatestUser.single().content)
    }

    @Test
    fun `failed assistant generation preserves partial text and failure reason`() = runBlocking {
        val conversationId = repository.sendMessage(null, "Prompt")
        val assistantMessageId = repository.startAssistantStreaming(conversationId)

        repository.appendAssistantDelta(conversationId, assistantMessageId, "Almost there")
        repository.failAssistantMessage(conversationId, assistantMessageId, "network timeout")

        val assistantMessage = repository.observeMessages(conversationId).first().last()

        assertEquals("Almost there", assistantMessage.content)
        assertEquals(MessageStatus.FAILED, assistantMessage.status)
        assertEquals("network timeout", assistantMessage.failureReason)
        assertFalse(repository.observeHasActiveGeneration().first())
    }

    @Test
    fun `restart restores selected conversation transcript and draft`() = runBlocking {
        val conversationId = repository.sendMessage(null, "Restore me")
        val assistantMessageId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, assistantMessageId, "Saved")
        repository.completeAssistantMessage(conversationId, assistantMessageId)
        repository.saveDraft(conversationId, "Draft before restart")
        repository.selectConversation(conversationId)

        recreateRepositoryForRestart().recoverPersistedLaunchState()

        assertEquals(conversationId, repository.observeSelectedConversationId().first())
        assertEquals(
            listOf("Restore me", "Saved"),
            repository.observeMessages(conversationId).first().map { it.content },
        )
        assertEquals("Draft before restart", repository.observeDraft(conversationId).first()?.content)
        assertFalse(repository.observeHasActiveGeneration().first())
    }

    @Test
    fun `restart normalizes interrupted streaming message to cancelled terminal state`() = runBlocking {
        val conversationId = repository.sendMessage(null, "Interrupted")
        val assistantMessageId = repository.startAssistantStreaming(conversationId)
        repository.appendAssistantDelta(conversationId, assistantMessageId, "Partial")

        recreateRepositoryForRestart().recoverPersistedLaunchState()

        val assistantMessage = repository.observeMessages(conversationId).first().last { it.author == MessageAuthor.ASSISTANT }
        assertEquals("Partial", assistantMessage.content)
        assertEquals(MessageStatus.CANCELLED, assistantMessage.status)
        assertFalse(repository.observeHasActiveGeneration().first())
    }

    @Test
    fun `restart does not recreate a deleted selected conversation`() = runBlocking {
        val conversationId = repository.sendMessage(null, "Delete me")
        repository.selectConversation(conversationId)
        repository.deleteConversation(conversationId)

        recreateRepositoryForRestart().recoverPersistedLaunchState()

        assertTrue(repository.observeConversations().first().isEmpty())
        assertNull(repository.observeSelectedConversationId().first())
    }

    @Test
    fun `restart with no chats keeps launch state empty`() = runBlocking {
        recreateRepositoryForRestart().recoverPersistedLaunchState()

        assertTrue(repository.observeConversations().first().isEmpty())
        assertNull(repository.observeSelectedConversationId().first())
        assertFalse(repository.observeHasActiveGeneration().first())
    }

    private fun recreateRepositoryForRestart(): RoomConversationRepository {
        localDataSource = RoomLocalConversationDataSource(database)
        repository = RoomConversationRepository(
            localDataSource = localDataSource,
            nowEpochMillis = { clock++ },
            conversationIdFactory = { ConversationId("conversation-${++conversationCounter}") },
            messageIdFactory = { MessageId("message-${++messageCounter}") },
        )
        return repository
    }
}
