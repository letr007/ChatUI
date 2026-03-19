package com.letr.chatui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letr.chatui.R
import com.letr.chatui.data.model.NonSensitiveChatSettings
import com.letr.chatui.data.model.MessageAuthor
import com.letr.chatui.data.model.MessageStatus
import com.letr.chatui.persistence.ChatUiDatabase
import com.letr.chatui.persistence.ConversationEntity
import com.letr.chatui.persistence.DraftEntity
import com.letr.chatui.persistence.MessageEntity
import com.letr.chatui.persistence.SelectedConversationEntity
import com.letr.chatui.settings.DataStoreNonSensitiveSettingsLocalDataSource
import com.letr.chatui.settings.EncryptedSecretSettingsLocalDataSource
import com.letr.chatui.settings.SettingsStorageFactory
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetPersistentState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        clearPersistentState(context)
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @Test
    fun coldLaunch_showsChatFirstEmptyState() {
        composeRule.onNodeWithText(stringRes(R.string.empty_state_setup_title)).assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.open_settings)).assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.browse_history)).assertIsDisplayed()
    }

    @Test
    fun settingsRoundTrip_opensAndReturnsToChat() {
        composeRule.onNodeWithText(stringRes(R.string.settings_button)).performClick()
        composeRule.onNodeWithText(stringRes(R.string.settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.settings_save)).assertIsDisplayed()

        composeRule.onNodeWithText(stringRes(R.string.settings_return_to_chat)).performClick()
        composeRule.onNodeWithText(stringRes(R.string.open_settings)).assertIsDisplayed()
    }

    @Test
    fun historyDrawer_opensEmptyStateAndCloses() {
        composeRule.onNodeWithText(stringRes(R.string.history_button)).performClick()
        composeRule.onNodeWithText(stringRes(R.string.history_drawer_title)).assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.history_empty_state)).assertIsDisplayed()

        composeRule.onNodeWithText(stringRes(R.string.close)).performClick()
        composeRule.onNodeWithText(stringRes(R.string.open_settings)).assertIsDisplayed()
    }

    @Test
    fun restartRestoresPersistedConversationTranscriptAndDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)
        seedConversationState(
            context = context,
            conversationTitle = "Persisted conversation",
            userText = "Persisted prompt",
            assistantText = "Persisted reply",
            assistantStatus = MessageStatus.COMPLETE,
            draftText = "Draft before restart",
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(stringRes(R.string.chat_header_selected_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Persisted prompt").assertIsDisplayed()
        composeRule.onNodeWithText("Persisted reply").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Draft before restart")
    }

    @Test
    fun restartNormalizesInterruptedStreamingReplyToStoppedState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)
        seedConversationState(
            context = context,
            conversationTitle = "Interrupted conversation",
            userText = "Interrupted prompt",
            assistantText = "Partial reply",
            assistantStatus = MessageStatus.STREAMING,
            draftText = null,
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Partial reply").assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.assistant_status_stopped)).assertIsDisplayed()
    }

    private fun stringRes(resId: Int, vararg args: Any): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        return context.getString(resId, *args)
    }

    private fun clearPersistentState(context: android.content.Context) = runBlocking {
        val database = ChatUiDatabase.create(context)
        try {
            database.clearAllTables()
        } finally {
            database.close()
        }

        SettingsStorageFactory.createNonSensitiveSettingsDataStore(context).edit { preferences ->
            preferences.clear()
        }
        SettingsStorageFactory.createEncryptedSecretPreferences(context)
            .edit()
            .clear()
            .commit()
    }

    private fun seedValidSettings(context: android.content.Context) = runBlocking {
        DataStoreNonSensitiveSettingsLocalDataSource(
            dataStore = SettingsStorageFactory.createNonSensitiveSettingsDataStore(context),
        ).updateSettings(
            NonSensitiveChatSettings(
                apiBaseUrl = "https://api.example.com/v1",
                modelId = "gpt-4o-mini",
            )
        )
        EncryptedSecretSettingsLocalDataSource(
            sharedPreferences = SettingsStorageFactory.createEncryptedSecretPreferences(context),
        ).setApiKey("sk-test-key")
    }

    private fun seedConversationState(
        context: android.content.Context,
        conversationTitle: String,
        userText: String,
        assistantText: String,
        assistantStatus: MessageStatus,
        draftText: String?,
    ) = runBlocking {
        val database = ChatUiDatabase.create(context)
        try {
            val conversationId = "conversation-seeded"
            database.conversationDao().insertConversation(
                ConversationEntity(
                    conversationId = conversationId,
                    title = conversationTitle,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 3L,
                )
            )
            database.messageDao().insertMessage(
                MessageEntity(
                    messageId = "message-user-1",
                    conversationId = conversationId,
                    author = MessageAuthor.USER,
                    content = userText,
                    status = MessageStatus.COMPLETE,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                )
            )
            database.messageDao().insertMessage(
                MessageEntity(
                    messageId = "message-assistant-1",
                    conversationId = conversationId,
                    author = MessageAuthor.ASSISTANT,
                    content = assistantText,
                    status = assistantStatus,
                    createdAtEpochMillis = 2L,
                    updatedAtEpochMillis = 2L,
                )
            )
            if (draftText != null) {
                database.draftDao().upsertDraft(
                    DraftEntity(
                        conversationId = conversationId,
                        content = draftText,
                        updatedAtEpochMillis = 3L,
                    )
                )
            }
            database.selectedConversationDao().upsertSelectedConversation(
                SelectedConversationEntity(conversationId = conversationId)
            )
        } finally {
            database.close()
        }
    }
}
