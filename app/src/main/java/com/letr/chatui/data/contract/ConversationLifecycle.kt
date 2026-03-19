package com.letr.chatui.data.contract

import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus

/**
 * Pure lifecycle rules for conversation persistence and assistant generation.
 *
 * These helpers encode the locked V1 semantics before any Room or network
 * implementation exists:
 * - a conversation is created only on the first send action
 * - empty conversations are never persisted
 * - a default title comes from the first user message and is truncated
 * - exactly one assistant message row exists for an in-flight reply and is
 *   updated in place until it reaches a terminal state
 * - partial assistant content is preserved when generation fails or is cancelled
 */
object ConversationLifecycle {
    const val DEFAULT_TITLE_MAX_LENGTH: Int = 60

    fun shouldPersistConversation(messages: List<Message>): Boolean {
        return messages.any { message ->
            message.author == MessageAuthor.USER && message.content.isNotBlank()
        }
    }

    fun defaultTitleFromFirstUserMessage(
        firstUserMessage: String,
        maxLength: Int = DEFAULT_TITLE_MAX_LENGTH,
    ): String {
        require(maxLength > 0) { "maxLength must be greater than zero." }

        val normalized = firstUserMessage.trim().replace(Regex("\\s+"), " ")
        return normalized.take(maxLength)
    }

    fun createConversationOnFirstSend(
        conversationId: ConversationId,
        firstUserMessage: String,
        createdAtEpochMillis: Long,
        maxTitleLength: Int = DEFAULT_TITLE_MAX_LENGTH,
    ): Conversation {
        require(firstUserMessage.isNotBlank()) {
            "The first send must contain content before a conversation can be created."
        }

        val title = defaultTitleFromFirstUserMessage(
            firstUserMessage = firstUserMessage,
            maxLength = maxTitleLength,
        )

        return Conversation(
            id = conversationId,
            title = title,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = createdAtEpochMillis,
        )
    }

    fun startAssistantMessage(
        conversationId: ConversationId,
        messageId: MessageId,
        createdAtEpochMillis: Long,
    ): Message {
        return Message(
            id = messageId,
            conversationId = conversationId,
            author = MessageAuthor.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = createdAtEpochMillis,
        )
    }

    fun appendAssistantDelta(
        message: Message,
        delta: String,
        updatedAtEpochMillis: Long,
    ): Message {
        require(message.author == MessageAuthor.ASSISTANT) {
            "Only assistant messages can receive generated deltas."
        }
        require(message.status == MessageStatus.STREAMING) {
            "Only a streaming assistant message can be updated in place."
        }

        return message.copy(
            content = message.content + delta,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }

    fun completeAssistantMessage(message: Message, updatedAtEpochMillis: Long): Message {
        require(message.author == MessageAuthor.ASSISTANT) {
            "Only assistant messages can complete generation."
        }

        return message.copy(
            status = MessageStatus.COMPLETE,
            updatedAtEpochMillis = updatedAtEpochMillis,
            failureReason = null,
        )
    }

    fun failAssistantMessage(
        message: Message,
        failureReason: String?,
        updatedAtEpochMillis: Long,
    ): Message {
        require(message.author == MessageAuthor.ASSISTANT) {
            "Only assistant messages can fail generation."
        }

        return message.copy(
            status = MessageStatus.FAILED,
            updatedAtEpochMillis = updatedAtEpochMillis,
            failureReason = failureReason,
        )
    }

    fun cancelAssistantMessage(message: Message, updatedAtEpochMillis: Long): Message {
        require(message.author == MessageAuthor.ASSISTANT) {
            "Only assistant messages can be cancelled."
        }

        return message.copy(
            status = MessageStatus.CANCELLED,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
    }
}
