package com.letr.chatui.data.model

data class Conversation(
    val id: ConversationId,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
