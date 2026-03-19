package com.letr.chatui.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        DraftEntity::class,
        SelectedConversationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomTypeConverters::class)
abstract class ChatUiDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun draftDao(): DraftDao

    abstract fun selectedConversationDao(): SelectedConversationDao

    companion object {
        const val DATABASE_NAME: String = "chatui.db"

        fun create(context: Context): ChatUiDatabase {
            return Room.databaseBuilder(
                context = context.applicationContext,
                klass = ChatUiDatabase::class.java,
                name = DATABASE_NAME,
            ).build()
        }
    }
}
