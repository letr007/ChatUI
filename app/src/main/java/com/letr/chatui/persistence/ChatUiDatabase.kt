package com.letr.chatui.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        DraftEntity::class,
        SelectedConversationEntity::class,
    ],
    version = 2,
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

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE messages ADD COLUMN attached_image_uris TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun create(context: Context): ChatUiDatabase {
            return Room.databaseBuilder(
                context = context.applicationContext,
                klass = ChatUiDatabase::class.java,
                name = DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2).build()
        }
    }
}
