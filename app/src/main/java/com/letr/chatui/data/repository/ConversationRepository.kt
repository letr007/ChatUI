package com.letr.chatui.data.repository

import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageId
import kotlinx.coroutines.flow.Flow

/**
 * Contract for local conversation state and lifecycle operations.
 *
 * Required V1 persistence rules:
 * - conversations are created only by the first successful send flow
 * - empty conversations are never persisted
 * - the initial title defaults to a truncated first user message
 * - drafts are stored per conversation
 * - only one generation may be active globally at a time
 * - once assistant streaming starts, one assistant message row is created and
 *   updated in place until terminal status (complete, failed, or cancelled)
 * - regenerate replaces the latest assistant response for the latest user turn;
 *   V1 does not support branching histories
 */
interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeMessages(conversationId: ConversationId): Flow<List<Message>>

    suspend fun getMessages(conversationId: ConversationId): List<Message>

    fun observeDraft(conversationId: ConversationId): Flow<Draft?>

    fun observeSelectedConversationId(): Flow<ConversationId?>

    fun observeHasActiveGeneration(): Flow<Boolean>

    /**
     * Creates a conversation shell from the first user message only.
     *
     * Implementations must reject blank input so callers cannot persist an empty
     * conversation before the user actually sends content.
     */
    suspend fun createConversation(firstUserMessage: String): Conversation

    /**
     * Sends a user message into an existing conversation or starts a new one.
     *
     * When [conversationId] is null, this call is responsible for creating the
     * conversation from the first user message before persisting the message.
     */
    suspend fun sendMessage(
        conversationId: ConversationId?,
        content: String,
        attachedImageUris: List<String> = emptyList(),
    ): ConversationId

    suspend fun selectConversation(conversationId: ConversationId?)

    suspend fun renameConversation(conversationId: ConversationId, newTitle: String)

    suspend fun deleteConversation(conversationId: ConversationId)

    /**
     * Removes the latest assistant response tied to the latest user turn so the
     * caller can restart generation in the same linear thread.
     */
    suspend fun regenerateLatestResponse(conversationId: ConversationId)

    /**
     * Stops the currently streaming assistant response and stores any partial
     * content with terminal status [com.letr.chatui.data.model.MessageStatus.CANCELLED].
     */
    suspend fun stopGeneration(conversationId: ConversationId, messageId: MessageId)

    suspend fun saveDraft(conversationId: ConversationId, content: String)

    suspend fun clearDraft(conversationId: ConversationId)
}

interface AssistantStreamingRepository {
    suspend fun startAssistantStreaming(conversationId: ConversationId): MessageId

    suspend fun appendAssistantDelta(conversationId: ConversationId, messageId: MessageId, delta: String)

    suspend fun completeAssistantMessage(conversationId: ConversationId, messageId: MessageId)

    suspend fun failAssistantMessage(conversationId: ConversationId, messageId: MessageId, failureReason: String?)

    suspend fun stopGeneration(conversationId: ConversationId, messageId: MessageId)
}
