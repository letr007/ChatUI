package com.letr.chatui.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "selected_conversation_state")
data class SelectedConversationEntity(
    @PrimaryKey
    @ColumnInfo(name = "state_id")
    val stateId: Int = SINGLETON_STATE_ID,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String?,
) {
    companion object {
        const val SINGLETON_STATE_ID: Int = 1
    }
}
