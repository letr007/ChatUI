package com.letr.chatui.data.model

data class Draft(
    val conversationId: ConversationId,
    val content: String,
    val updatedAtEpochMillis: Long,
)
