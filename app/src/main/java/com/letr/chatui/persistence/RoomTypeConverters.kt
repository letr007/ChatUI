package com.letr.chatui.persistence

import androidx.room.TypeConverter
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageStatus

class RoomTypeConverters {
    @TypeConverter
    fun fromMessageAuthor(author: MessageAuthor): String = author.name

    @TypeConverter
    fun toMessageAuthor(author: String): MessageAuthor = MessageAuthor.valueOf(author)

    @TypeConverter
    fun fromMessageStatus(status: MessageStatus): String = status.name

    @TypeConverter
    fun toMessageStatus(status: String): MessageStatus = MessageStatus.valueOf(status)

    @TypeConverter
    fun fromStringList(values: List<String>): String = values.joinToString(separator = "\n")

    @TypeConverter
    fun toStringList(values: String): List<String> = if (values.isBlank()) emptyList() else values.split('\n')
}
