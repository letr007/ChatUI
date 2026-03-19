package com.letr.chatui.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE conversation_id = :conversationId LIMIT 1")
    fun observeDraft(conversationId: String): Flow<DraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE conversation_id = :conversationId")
    suspend fun deleteDraft(conversationId: String)
}
