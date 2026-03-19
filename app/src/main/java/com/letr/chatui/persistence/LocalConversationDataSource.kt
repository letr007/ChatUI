package com.letr.chatui.persistence

import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

interface LocalConversationDataSource {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(conversationId: ConversationId): Flow<List<Message>>

    fun observeDraft(conversationId: ConversationId): Flow<Draft?>

    fun observeSelectedConversationId(): Flow<ConversationId?>

    fun observeHasMessageWithStatus(status: MessageStatus): Flow<Boolean>

    suspend fun getConversation(conversationId: ConversationId): Conversation?

    suspend fun getMessages(conversationId: ConversationId): List<Message>

    suspend fun getMessage(messageId: MessageId): Message?

    suspend fun getSelectedConversationId(): ConversationId?

    suspend fun hasMessageWithStatus(status: MessageStatus): Boolean

    suspend fun normalizeInterruptedStreamingMessages(recoveredAtEpochMillis: Long): Int

    suspend fun insertConversation(conversation: Conversation)

    suspend fun updateConversation(conversation: Conversation)

    suspend fun deleteConversation(conversationId: ConversationId)

    suspend fun insertMessage(message: Message)

    suspend fun updateMessage(message: Message)

    suspend fun deleteMessage(messageId: MessageId)

    suspend fun deleteMessagesAfter(conversationId: ConversationId, createdAfterEpochMillis: Long)

    suspend fun saveDraft(draft: Draft)

    suspend fun clearDraft(conversationId: ConversationId)

    suspend fun selectConversation(conversationId: ConversationId?)

    suspend fun <T> inTransaction(block: suspend () -> T): T
}
