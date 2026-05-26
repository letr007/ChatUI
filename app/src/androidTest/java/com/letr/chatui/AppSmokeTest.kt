package com.letr.chatui

import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
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
import com.letr.chatui.settings.ApiKeyMaskingPolicy
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
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

    @Test
    fun coldRelaunch_restoresSelectedConversationDraftAndNormalizesInterruptedReplyWithoutDuplicates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)
        seedConversationState(
            context = context,
            conversationTitle = "Relaunch conversation",
            userText = "Relaunch prompt",
            assistantText = "Interrupted relaunch reply",
            assistantStatus = MessageStatus.STREAMING,
            draftText = "Draft before cold relaunch",
        )

        composeRule.activityRule.scenario.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()

            composeRule.onNodeWithText(stringRes(R.string.chat_header_selected_title)).assertIsDisplayed()
            composeRule.onNodeWithText("Relaunch prompt").assertIsDisplayed()
            composeRule.onAllNodesWithText("Interrupted relaunch reply").assertCountEquals(1)
            composeRule.onNodeWithText(stringRes(R.string.assistant_status_stopped)).assertIsDisplayed()
            composeRule.onNode(hasSetTextAction()).assertTextContains("Draft before cold relaunch")
        }
    }

    @Test
    fun restartRendersSeededFencedCodeBlockAfterIntroTextWithCopyAffordance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)
        seedConversationState(
            context = context,
            conversationTitle = "Markdown conversation",
            userText = "Show me the snippet",
            assistantText = """
                Here's the implementation:

                ```kotlin
                val answer = 42
                println(answer)
                ```
            """.trimIndent(),
            assistantStatus = MessageStatus.COMPLETE,
            draftText = null,
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Here's the implementation:").assertIsDisplayed()
        composeRule.onNodeWithText("kotlin").assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.copy_code)).assertIsDisplayed()
        composeRule.onNodeWithText("val answer = 42\nprintln(answer)").assertIsDisplayed()
    }

    @Test
    fun recreateAndColdRelaunch_keepSeededUserAttachmentStripVisibleInTranscript() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)
        seedConversationState(
            context = context,
            conversationTitle = "Attachment conversation",
            userText = "Prompt with image",
            assistantText = "Attachment acknowledged",
            assistantStatus = MessageStatus.COMPLETE,
            draftText = null,
            attachedImageUris = listOf(createSeedAttachmentUri(context)),
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        assertAttachmentTranscriptVisible()

        composeRule.activityRule.scenario.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForIdle()
            assertAttachmentTranscriptVisible()
        }
    }

    @Test
    fun attachmentChooser_showsDisabledCameraOptionWhileGalleryRemainsEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(stringRes(R.string.attach_image)).performClick()

        composeRule.onNodeWithText(stringRes(R.string.attachment_menu_camera)).assertIsDisplayed()
        composeRule
            .onNodeWithText(stringRes(R.string.attachment_menu_camera), useUnmergedTree = true)
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(stringRes(R.string.attachment_menu_gallery), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun deletingSelectedConversationFallsBackToRemainingConversationAfterRecreate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)
        seedMultipleConversationState(
            context = context,
            conversations = listOf(
                SeedConversation(
                    id = "conversation-older",
                    title = "Older conversation",
                    userText = "Older prompt",
                    assistantText = "Older reply",
                    assistantStatus = MessageStatus.COMPLETE,
                    draftText = null,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                ),
                SeedConversation(
                    id = "conversation-selected",
                    title = "Selected conversation",
                    userText = "Selected prompt",
                    assistantText = "Selected reply",
                    assistantStatus = MessageStatus.COMPLETE,
                    draftText = null,
                    createdAtEpochMillis = 3L,
                    updatedAtEpochMillis = 4L,
                ),
            ),
            selectedConversationId = "conversation-selected",
        )

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Selected prompt").assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.history_button)).performClick()
        composeRule.onNodeWithText("Selected conversation").performTouchInput { longClick() }
        composeRule.onNodeWithText(stringRes(R.string.delete)).performClick()
        composeRule.onNodeWithText(stringRes(R.string.delete)).performClick()
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Older prompt").assertIsDisplayed()
        composeRule.onNodeWithText("Older reply").assertIsDisplayed()
        composeRule.onAllNodesWithText("Selected prompt").assertCountEquals(0)
    }

    @Test
    fun sendWithValidSettings_showsUserPromptImmediatelyInTranscript() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        seedValidSettings(context)

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val prompt = "Immediate prompt visibility"

        composeRule.onNode(hasSetTextAction()).performTextInput(prompt)
        composeRule.onNodeWithContentDescription(stringRes(R.string.send_prompt)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(prompt).assertIsDisplayed()
    }

    @Test
    fun missingConfigState_opensSettingsFromChatHomeRecoveryAction() {
        composeRule.onNodeWithText(stringRes(R.string.empty_state_setup_title)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(stringRes(R.string.open_settings)).performClick()

        composeRule.onNodeWithText(stringRes(R.string.settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(stringRes(R.string.settings_save)).assertIsDisplayed()
    }

    @Test
    fun settingsSave_withValidInput_showsSuccessAndMaskedPersistedKeyState() {
        composeRule.onNodeWithText(stringRes(R.string.settings_button)).performClick()

        replaceSettingsField(
            label = stringRes(R.string.settings_base_url_label),
            text = "https://api.example.com/v1",
        )
        replaceSettingsField(
            label = stringRes(R.string.settings_model_id_label),
            text = "gpt-4o-mini",
        )
        replaceSettingsField(
            label = stringRes(R.string.settings_api_key_label),
            text = "sk-live-1234",
        )

        composeRule.onNodeWithText(stringRes(R.string.settings_save)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(stringRes(R.string.settings_saved_feedback)).assertIsDisplayed()
        composeRule.onNodeWithText(
            stringRes(
                R.string.settings_api_key_stored_supporting,
                ApiKeyMaskingPolicy.mask("sk-live-1234"),
            )
        ).assertIsDisplayed()
    }

    @Test
    fun settingsSave_withInvalidBaseUrl_showsDeterministicValidationFeedback() {
        composeRule.onNodeWithText(stringRes(R.string.settings_button)).performClick()

        replaceSettingsField(
            label = stringRes(R.string.settings_base_url_label),
            text = "not-a-valid-url",
        )
        replaceSettingsField(
            label = stringRes(R.string.settings_model_id_label),
            text = "gpt-4o-mini",
        )
        replaceSettingsField(
            label = stringRes(R.string.settings_api_key_label),
            text = "sk-live-1234",
        )

        composeRule.onNodeWithText(stringRes(R.string.settings_save)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(stringRes(R.string.settings_feedback_invalid_base_url)).assertIsDisplayed()
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
        attachedImageUris: List<String> = emptyList(),
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
                    attachedImageUris = attachedImageUris,
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

    private fun createSeedAttachmentUri(context: android.content.Context): String {
        return android.net.Uri.Builder()
            .scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(context.resources.getResourceTypeName(R.mipmap.ic_launcher))
            .appendPath(context.resources.getResourceEntryName(R.mipmap.ic_launcher))
            .build()
            .toString()
    }

    private fun assertAttachmentTranscriptVisible() {
        composeRule.onNodeWithText("Prompt with image").assertIsDisplayed()
        composeRule.onNodeWithText("Attachment acknowledged").assertIsDisplayed()
        composeRule
            .onAllNodes(
                hasScrollAction() and hasAnySibling(hasText("Prompt with image")),
                useUnmergedTree = true,
            )
            .assertCountEquals(1)

        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val attachmentUri = createSeedAttachmentUri(context)
        assertThat(attachmentUri, containsString(context.packageName))
    }

    private fun seedMultipleConversationState(
        context: android.content.Context,
        conversations: List<SeedConversation>,
        selectedConversationId: String,
    ) = runBlocking {
        val database = ChatUiDatabase.create(context)
        try {
            conversations.forEachIndexed { index, conversation ->
                database.conversationDao().insertConversation(
                    ConversationEntity(
                        conversationId = conversation.id,
                        title = conversation.title,
                        createdAtEpochMillis = conversation.createdAtEpochMillis,
                        updatedAtEpochMillis = conversation.updatedAtEpochMillis,
                    )
                )
                database.messageDao().insertMessage(
                    MessageEntity(
                        messageId = "message-user-${index + 1}",
                        conversationId = conversation.id,
                        author = MessageAuthor.USER,
                        content = conversation.userText,
                        status = MessageStatus.COMPLETE,
                        createdAtEpochMillis = conversation.createdAtEpochMillis,
                        updatedAtEpochMillis = conversation.createdAtEpochMillis,
                    )
                )
                database.messageDao().insertMessage(
                    MessageEntity(
                        messageId = "message-assistant-${index + 1}",
                        conversationId = conversation.id,
                        author = MessageAuthor.ASSISTANT,
                        content = conversation.assistantText,
                        status = conversation.assistantStatus,
                        createdAtEpochMillis = conversation.createdAtEpochMillis + 1L,
                        updatedAtEpochMillis = conversation.createdAtEpochMillis + 1L,
                    )
                )
                if (conversation.draftText != null) {
                    database.draftDao().upsertDraft(
                        DraftEntity(
                            conversationId = conversation.id,
                            content = conversation.draftText,
                            updatedAtEpochMillis = conversation.updatedAtEpochMillis,
                        )
                    )
                }
            }

            database.selectedConversationDao().upsertSelectedConversation(
                SelectedConversationEntity(conversationId = selectedConversationId)
            )
        } finally {
            database.close()
        }
    }

    private data class SeedConversation(
        val id: String,
        val title: String,
        val userText: String,
        val assistantText: String,
        val assistantStatus: MessageStatus,
        val draftText: String?,
        val createdAtEpochMillis: Long,
        val updatedAtEpochMillis: Long,
    )

    private fun replaceSettingsField(label: String, text: String) {
        composeRule
            .onNode(
                hasSetTextAction() and hasText(label),
                useUnmergedTree = true,
            )
            .performTextClearance()
        composeRule
            .onNode(
                hasSetTextAction() and hasText(label),
                useUnmergedTree = true,
            )
            .performTextInput(text)
    }
}
