package com.letr.chatui.persistence

import com.letr.chatui.data.model.Conversation
import com.letr.chatui.data.model.ConversationId
import com.letr.chatui.data.model.Draft
import com.letr.chatui.data.model.Message
import com.letr.chatui.data.model.MessageId

internal fun ConversationEntity.toDomain(): Conversation {
    return Conversation(
        id = ConversationId(conversationId),
        title = title,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

internal fun MessageEntity.toDomain(): Message {
    return Message(
        id = MessageId(messageId),
        conversationId = ConversationId(conversationId),
        author = author,
        content = content,
        status = status,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        failureReason = failureReason,
    )
}

internal fun DraftEntity.toDomain(): Draft {
    return Draft(
        conversationId = ConversationId(conversationId),
        content = content,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

internal fun Conversation.toEntity(): ConversationEntity {
    return ConversationEntity(
        conversationId = id.value,
        title = title,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

internal fun Message.toEntity(): MessageEntity {
    return MessageEntity(
        messageId = id.value,
        conversationId = conversationId.value,
        author = author,
        content = content,
        status = status,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        failureReason = failureReason,
    )
}

internal fun Draft.toEntity(): DraftEntity {
    return DraftEntity(
        conversationId = conversationId.value,
        content = content,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
