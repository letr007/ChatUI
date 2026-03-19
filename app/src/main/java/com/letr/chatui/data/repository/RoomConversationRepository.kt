package com.letr.chatui.data.repository

import com.letr.chatui.data.contract.ConversationLifecycle
import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.persistence.LocalConversationDataSource
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class RoomConversationRepository(
    private val localDataSource: LocalConversationDataSource,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val conversationIdFactory: () -> ConversationId = { ConversationId(UUID.randomUUID().toString()) },
    private val messageIdFactory: () -> MessageId = { MessageId(UUID.randomUUID().toString()) },
 ) : ConversationRepository, AssistantStreamingRepository {

    suspend fun recoverPersistedLaunchState() {
        localDataSource.inTransaction {
            val recoveredAtEpochMillis = nowEpochMillis()
            localDataSource.normalizeInterruptedStreamingMessages(recoveredAtEpochMillis)

            val selectedConversationId = localDataSource.getSelectedConversationId()
            if (selectedConversationId != null && localDataSource.getConversation(selectedConversationId) == null) {
                localDataSource.selectConversation(null)
            }
        }
    }

    override fun observeConversations(): Flow<List<Conversation>> = localDataSource.observeConversations()

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> {
        return localDataSource.observeMessages(conversationId)
    }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> {
        return localDataSource.getMessages(conversationId)
    }

    override fun observeDraft(conversationId: ConversationId): Flow<Draft?> {
        return localDataSource.observeDraft(conversationId)
    }

    override fun observeSelectedConversationId(): Flow<ConversationId?> {
        return localDataSource.observeSelectedConversationId()
    }

    override fun observeHasActiveGeneration(): Flow<Boolean> {
        return localDataSource.observeHasMessageWithStatus(MessageStatus.STREAMING)
    }

    override suspend fun createConversation(firstUserMessage: String): Conversation {
        require(firstUserMessage.isNotBlank()) {
            "The first send must contain content before a conversation can be created."
        }

        return buildConversation(firstUserMessage)
    }

    override suspend fun sendMessage(conversationId: ConversationId?, content: String): ConversationId {
        val trimmedContent = content.trim()
        require(trimmedContent.isNotBlank()) { "Blank sends must not create empty conversations." }

        return localDataSource.inTransaction {
            val targetConversation = if (conversationId != null) {
                requireConversation(conversationId)
            } else {
                val createdConversation = createConversation(trimmedContent)
                localDataSource.insertConversation(createdConversation)
                createdConversation
            }
            val timestamp = nowEpochMillis()
            val userMessage = Message(
                id = messageIdFactory(),
                conversationId = targetConversation.id,
                author = MessageAuthor.USER,
                content = trimmedContent,
                status = MessageStatus.COMPLETE,
                createdAtEpochMillis = timestamp,
                updatedAtEpochMillis = timestamp,
            )

            localDataSource.insertMessage(userMessage)
            localDataSource.updateConversation(targetConversation.copy(updatedAtEpochMillis = timestamp))
            localDataSource.clearDraft(targetConversation.id)
            localDataSource.selectConversation(targetConversation.id)
            targetConversation.id
        }
    }

    override suspend fun selectConversation(conversationId: ConversationId?) {
        localDataSource.selectConversation(conversationId)
    }

    override suspend fun renameConversation(conversationId: ConversationId, newTitle: String) {
        val existingConversation = requireConversation(conversationId)
        localDataSource.updateConversation(
            existingConversation.copy(
                title = newTitle.trim(),
                updatedAtEpochMillis = nowEpochMillis(),
            )
        )
    }

    override suspend fun deleteConversation(conversationId: ConversationId) {
        localDataSource.inTransaction {
            localDataSource.deleteConversation(conversationId)
            if (localDataSource.getSelectedConversationId() == conversationId) {
                localDataSource.selectConversation(null)
            }
        }
    }

    override suspend fun regenerateLatestResponse(conversationId: ConversationId): MessageId {
        return localDataSource.inTransaction {
            val messages = localDataSource.getMessages(conversationId)
            val latestUserIndex = messages.indexOfLast { it.author == MessageAuthor.USER }
            require(latestUserIndex >= 0) { "A user message is required before regeneration." }

            messages.drop(latestUserIndex + 1)
                .filter { it.author == MessageAuthor.ASSISTANT }
                .forEach { assistantMessage ->
                    localDataSource.deleteMessage(assistantMessage.id)
                }

            startAssistantStreaming(conversationId)
        }
    }

    override suspend fun stopGeneration(conversationId: ConversationId, messageId: MessageId) {
        localDataSource.inTransaction {
            val message = requireMessage(messageId)
            val cancelled = ConversationLifecycle.cancelAssistantMessage(
                message = message,
                updatedAtEpochMillis = nowEpochMillis(),
            )
            localDataSource.updateMessage(cancelled)
            touchConversation(conversationId, cancelled.updatedAtEpochMillis)
        }
    }

    override suspend fun saveDraft(conversationId: ConversationId, content: String) {
        localDataSource.saveDraft(
            Draft(
                conversationId = conversationId,
                content = content,
                updatedAtEpochMillis = nowEpochMillis(),
            )
        )
    }

    override suspend fun clearDraft(conversationId: ConversationId) {
        localDataSource.clearDraft(conversationId)
    }

    override suspend fun startAssistantStreaming(conversationId: ConversationId): MessageId {
        require(!hasAnyActiveGeneration()) { "Only one active generation is allowed globally." }

        val messageId = messageIdFactory()
        val started = ConversationLifecycle.startAssistantMessage(
            conversationId = conversationId,
            messageId = messageId,
            createdAtEpochMillis = nowEpochMillis(),
        )
        localDataSource.inTransaction {
            localDataSource.insertMessage(started)
            touchConversation(conversationId, started.updatedAtEpochMillis)
        }
        return messageId
    }

    override suspend fun appendAssistantDelta(conversationId: ConversationId, messageId: MessageId, delta: String) {
        localDataSource.inTransaction {
            val message = requireMessage(messageId)
            val updated = ConversationLifecycle.appendAssistantDelta(
                message = message,
                delta = delta,
                updatedAtEpochMillis = nowEpochMillis(),
            )
            localDataSource.updateMessage(updated)
            touchConversation(conversationId, updated.updatedAtEpochMillis)
        }
    }

    override suspend fun completeAssistantMessage(conversationId: ConversationId, messageId: MessageId) {
        localDataSource.inTransaction {
            val message = requireMessage(messageId)
            val completed = ConversationLifecycle.completeAssistantMessage(
                message = message,
                updatedAtEpochMillis = nowEpochMillis(),
            )
            localDataSource.updateMessage(completed)
            touchConversation(conversationId, completed.updatedAtEpochMillis)
        }
    }

    override suspend fun failAssistantMessage(conversationId: ConversationId, messageId: MessageId, failureReason: String?) {
        localDataSource.inTransaction {
            val message = requireMessage(messageId)
            val failed = ConversationLifecycle.failAssistantMessage(
                message = message,
                failureReason = failureReason,
                updatedAtEpochMillis = nowEpochMillis(),
            )
            localDataSource.updateMessage(failed)
            touchConversation(conversationId, failed.updatedAtEpochMillis)
        }
    }

    private suspend fun requireConversation(conversationId: ConversationId): Conversation {
        return checkNotNull(localDataSource.getConversation(conversationId)) {
            "Conversation ${conversationId.value} was not found."
        }
    }

    private fun buildConversation(firstUserMessage: String): Conversation {
        return ConversationLifecycle.createConversationOnFirstSend(
            conversationId = conversationIdFactory(),
            firstUserMessage = firstUserMessage,
            createdAtEpochMillis = nowEpochMillis(),
        )
    }

    private suspend fun requireMessage(messageId: MessageId): Message {
        return checkNotNull(localDataSource.getMessage(messageId)) {
            "Message ${messageId.value} was not found."
        }
    }

    private suspend fun hasAnyActiveGeneration(): Boolean {
        return localDataSource.hasMessageWithStatus(MessageStatus.STREAMING)
    }

    private suspend fun touchConversation(conversationId: ConversationId, updatedAtEpochMillis: Long) {
        val conversation = requireConversation(conversationId)
        localDataSource.updateConversation(conversation.copy(updatedAtEpochMillis = updatedAtEpochMillis))
    }
}
