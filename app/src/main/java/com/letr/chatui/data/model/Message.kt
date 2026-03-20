package com.letr.chatui.data.model

data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val author: MessageAuthor,
    val content: String,
    val attachedImageUris: List<String> = emptyList(),
    val status: MessageStatus,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val failureReason: String? = null,
)

enum class MessageAuthor {
    USER,
    ASSISTANT,
}

enum class MessageStatus {
    PENDING,
    STREAMING,
    COMPLETE,
    FAILED,
    CANCELLED,
}
