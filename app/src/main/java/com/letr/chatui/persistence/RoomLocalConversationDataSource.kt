package com.letr.chatui.persistence

import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageId
import com.letr.chatui.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

class RoomLocalConversationDataSource(
    private val database: ChatUiDatabase,
) : LocalConversationDataSource {
    override fun observeConversations(): Flow<List<Conversation>> {
        return database.conversationDao().observeConversations().map { entities ->
            entities.map(ConversationEntity::toDomain)
        }
    }

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> {
        return database.messageDao().observeMessages(conversationId.value).map { entities ->
            entities.map(MessageEntity::toDomain)
        }
    }

    override fun observeDraft(conversationId: ConversationId): Flow<Draft?> {
        return database.draftDao().observeDraft(conversationId.value).map { it?.toDomain() }
    }

    override fun observeSelectedConversationId(): Flow<ConversationId?> {
        return database.selectedConversationDao().observeSelectedConversation().map { entity ->
            entity?.conversationId?.let(::ConversationId)
        }
    }

    override fun observeHasMessageWithStatus(status: MessageStatus): Flow<Boolean> {
        return database.messageDao().observeHasMessageWithStatus(status)
    }

    override suspend fun getConversation(conversationId: ConversationId): Conversation? {
        return database.conversationDao().getConversation(conversationId.value)?.toDomain()
    }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> {
        return database.messageDao().getMessages(conversationId.value).map(MessageEntity::toDomain)
    }

    override suspend fun getMessage(messageId: MessageId): Message? {
        return database.messageDao().getMessage(messageId.value)?.toDomain()
    }

    override suspend fun getSelectedConversationId(): ConversationId? {
        return database.selectedConversationDao().getSelectedConversation()?.conversationId?.let(::ConversationId)
    }

    override suspend fun hasMessageWithStatus(status: MessageStatus): Boolean {
        return database.messageDao().hasMessageWithStatus(status)
    }

    override suspend fun normalizeInterruptedStreamingMessages(recoveredAtEpochMillis: Long): Int {
        return database.messageDao().normalizeInterruptedStreamingMessages(
            activeStatus = MessageStatus.STREAMING,
            terminalStatus = MessageStatus.CANCELLED,
            recoveredAtEpochMillis = recoveredAtEpochMillis,
        )
    }

    override suspend fun insertConversation(conversation: Conversation) {
        database.conversationDao().insertConversation(conversation.toEntity())
    }

    override suspend fun updateConversation(conversation: Conversation) {
        database.conversationDao().updateConversation(conversation.toEntity())
    }

    override suspend fun deleteConversation(conversationId: ConversationId) {
        database.conversationDao().deleteConversation(conversationId.value)
    }

    override suspend fun insertMessage(message: Message) {
        database.messageDao().insertMessage(message.toEntity())
    }

    override suspend fun updateMessage(message: Message) {
        database.messageDao().updateMessage(message.toEntity())
    }

    override suspend fun deleteMessage(messageId: MessageId) {
        database.messageDao().deleteMessage(messageId.value)
    }

    override suspend fun deleteMessagesAfter(conversationId: ConversationId, createdAfterEpochMillis: Long) {
        database.messageDao().deleteMessagesAfter(conversationId.value, createdAfterEpochMillis)
    }

    override suspend fun saveDraft(draft: Draft) {
        database.draftDao().upsertDraft(draft.toEntity())
    }

    override suspend fun clearDraft(conversationId: ConversationId) {
        database.draftDao().deleteDraft(conversationId.value)
    }

    override suspend fun selectConversation(conversationId: ConversationId?) {
        database.selectedConversationDao().upsertSelectedConversation(
            SelectedConversationEntity(conversationId = conversationId?.value)
        )
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        return database.withTransaction(block)
    }
}
