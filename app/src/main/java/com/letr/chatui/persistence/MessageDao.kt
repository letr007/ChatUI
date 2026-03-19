package com.letr.chatui.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.letr.chatui.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId
        ORDER BY created_at_epoch_millis ASC, message_id ASC
        """
    )
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId
        ORDER BY created_at_epoch_millis ASC, message_id ASC
        """
    )
    suspend fun getMessages(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE message_id = :messageId LIMIT 1")
    suspend fun getMessage(messageId: String): MessageEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE status = :status)")
    fun observeHasMessageWithStatus(status: MessageStatus): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE status = :status)")
    suspend fun hasMessageWithStatus(status: MessageStatus): Boolean

    @Query(
        """
        UPDATE messages
        SET status = :terminalStatus,
            updated_at_epoch_millis = MAX(updated_at_epoch_millis, :recoveredAtEpochMillis),
            failure_reason = NULL
        WHERE status = :activeStatus
        """
    )
    suspend fun normalizeInterruptedStreamingMessages(
        activeStatus: MessageStatus,
        terminalStatus: MessageStatus,
        recoveredAtEpochMillis: Long,
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: MessageEntity)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId AND created_at_epoch_millis > :createdAfterEpochMillis")
    suspend fun deleteMessagesAfter(conversationId: String, createdAfterEpochMillis: Long)

    @Query("DELETE FROM messages WHERE message_id = :messageId")
    suspend fun deleteMessage(messageId: String)
}
