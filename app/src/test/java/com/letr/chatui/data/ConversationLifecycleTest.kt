package com.letr.chatui.data

import com.letr.chatui.data.contract.ConversationLifecycle
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationLifecycleTest {
    @Test
    fun `default title is normalized and truncated from first user message`() {
        val title = ConversationLifecycle.defaultTitleFromFirstUserMessage(
            firstUserMessage = "   Hello   there from   the first user message   ",
            maxLength = 12,
        )

        assertEquals("Hello there ", title)
    }

    @Test
    fun `conversation is created only on first send with a derived title`() {
        val conversation = ConversationLifecycle.createConversationOnFirstSend(
            conversationId = ConversationId("conversation-1"),
            firstUserMessage = "First prompt starts this conversation",
            createdAtEpochMillis = 1_000L,
            maxTitleLength = 10,
        )

        assertEquals("conversation-1", conversation.id.value)
        assertEquals("First prom", conversation.title)
        assertEquals(1_000L, conversation.createdAtEpochMillis)
        assertEquals(1_000L, conversation.updatedAtEpochMillis)
    }

    @Test
    fun `empty conversations are not eligible for persistence`() {
        assertFalse(ConversationLifecycle.shouldPersistConversation(emptyList()))

        val assistantOnly = ConversationLifecycle.startAssistantMessage(
            conversationId = ConversationId("conversation-1"),
            messageId = MessageId("assistant-1"),
            createdAtEpochMillis = 2_000L,
        )

        assertFalse(ConversationLifecycle.shouldPersistConversation(listOf(assistantOnly)))
    }

    @Test
    fun `one assistant message row is updated in place until complete`() {
        val started = ConversationLifecycle.startAssistantMessage(
            conversationId = ConversationId("conversation-1"),
            messageId = MessageId("assistant-1"),
            createdAtEpochMillis = 10L,
        )

        val afterFirstDelta = ConversationLifecycle.appendAssistantDelta(
            message = started,
            delta = "Hel",
            updatedAtEpochMillis = 11L,
        )
        val afterSecondDelta = ConversationLifecycle.appendAssistantDelta(
            message = afterFirstDelta,
            delta = "lo",
            updatedAtEpochMillis = 12L,
        )
        val completed = ConversationLifecycle.completeAssistantMessage(
            message = afterSecondDelta,
            updatedAtEpochMillis = 13L,
        )

        assertEquals(started.id, completed.id)
        assertEquals(MessageAuthor.ASSISTANT, completed.author)
        assertEquals("Hello", completed.content)
        assertEquals(MessageStatus.COMPLETE, completed.status)
        assertNull(completed.failureReason)
    }

    @Test
    fun `partial assistant content is preserved when cancelled or failed`() {
        val streamingMessage = ConversationLifecycle.appendAssistantDelta(
            message = ConversationLifecycle.startAssistantMessage(
                conversationId = ConversationId("conversation-1"),
                messageId = MessageId("assistant-1"),
                createdAtEpochMillis = 100L,
            ),
            delta = "Partial answer",
            updatedAtEpochMillis = 101L,
        )

        val cancelled = ConversationLifecycle.cancelAssistantMessage(
            message = streamingMessage,
            updatedAtEpochMillis = 102L,
        )
        val failed = ConversationLifecycle.failAssistantMessage(
            message = streamingMessage,
            failureReason = "network timeout",
            updatedAtEpochMillis = 103L,
        )

        assertEquals("Partial answer", cancelled.content)
        assertEquals(MessageStatus.CANCELLED, cancelled.status)
        assertEquals("Partial answer", failed.content)
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals("network timeout", failed.failureReason)
        assertTrue(failed.updatedAtEpochMillis > streamingMessage.createdAtEpochMillis)
    }
}
