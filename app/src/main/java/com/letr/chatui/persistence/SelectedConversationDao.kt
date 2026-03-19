package com.letr.chatui.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SelectedConversationDao {
    @Query(
        """
        SELECT * FROM selected_conversation_state
        WHERE state_id = ${SelectedConversationEntity.SINGLETON_STATE_ID}
        LIMIT 1
        """
    )
    fun observeSelectedConversation(): Flow<SelectedConversationEntity?>

    @Query(
        """
        SELECT * FROM selected_conversation_state
        WHERE state_id = ${SelectedConversationEntity.SINGLETON_STATE_ID}
        LIMIT 1
        """
    )
    suspend fun getSelectedConversation(): SelectedConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSelectedConversation(selectedConversation: SelectedConversationEntity)
}
