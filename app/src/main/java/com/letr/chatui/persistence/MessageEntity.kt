package com.letr.chatui.persistence

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageStatus

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["conversation_id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["conversation_id", "created_at_epoch_millis"]),
        Index(value = ["conversation_id", "updated_at_epoch_millis"]),
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    @ColumnInfo(name = "author")
    val author: MessageAuthor,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "attached_image_uris")
    val attachedImageUris: List<String> = emptyList(),
    @ColumnInfo(name = "status")
    val status: MessageStatus,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "failure_reason")
    val failureReason: String? = null,
)
