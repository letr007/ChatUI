package com.letr.chatui.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query(
        """
        SELECT * FROM conversations
        ORDER BY updated_at_epoch_millis DESC, created_at_epoch_millis DESC
        """
    )
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversation_id = :conversationId LIMIT 1")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE conversation_id = :conversationId")
    suspend fun deleteConversation(conversationId: String)
}
