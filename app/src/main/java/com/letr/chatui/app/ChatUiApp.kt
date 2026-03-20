package com.letr.chatui.app

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letr.chatui.chat.ChatViewModel
import com.letr.chatui.data.remote.ConfigBackedOpenAiChatCompletionRemoteClient
import com.letr.chatui.data.repository.RealSettingsRepository
import com.letr.chatui.data.repository.RoomConversationRepository
import com.letr.chatui.network.chatcompletions.RealOpenAiChatCompletionProviderAdapterFactory
import com.letr.chatui.persistence.ChatUiDatabase
import com.letr.chatui.persistence.RoomLocalConversationDataSource
import com.letr.chatui.settings.DataStoreNonSensitiveSettingsLocalDataSource
import com.letr.chatui.settings.EncryptedSecretSettingsLocalDataSource
import com.letr.chatui.settings.SettingsViewModel
import com.letr.chatui.settings.SettingsStorageFactory
import com.letr.chatui.ui.RootScreen
import okhttp3.OkHttpClient

@Composable
fun ChatUiApp() {
    val context = LocalContext.current.applicationContext
    val localDataSource = remember(context) {
        RoomLocalConversationDataSource(
            database = ChatUiDatabase.create(context),
        )
    }
    val conversationRepository = remember(localDataSource) {
        RoomConversationRepository(localDataSource = localDataSource)
    }
    val settingsRepository = remember(context) {
        RealSettingsRepository(
            nonSensitiveLocalDataSource = DataStoreNonSensitiveSettingsLocalDataSource(
                dataStore = SettingsStorageFactory.createNonSensitiveSettingsDataStore(context),
            ),
            secretLocalDataSource = EncryptedSecretSettingsLocalDataSource(
                sharedPreferences = SettingsStorageFactory.createEncryptedSecretPreferences(context),
            ),
        )
    }
    val remoteClient = remember(settingsRepository) {
        ConfigBackedOpenAiChatCompletionRemoteClient(
            activeChatConfigSource = settingsRepository,
            requestFactory = com.letr.chatui.data.remote.OpenAiChatCompletionRequestFactory(
                contentResolver = context.contentResolver,
            ),
            adapterFactory = RealOpenAiChatCompletionProviderAdapterFactory(
                okHttpClient = OkHttpClient(),
            ),
        )
    }
    val chatViewModel: ChatViewModel = viewModel(
        factory = remember(conversationRepository, settingsRepository, remoteClient, context) {
            chatViewModelFactory(
                conversationRepository = conversationRepository,
                settingsRepository = settingsRepository,
                remoteClient = remoteClient,
                resources = context.resources,
            )
        }
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = remember(settingsRepository, context) {
            settingsViewModelFactory(settingsRepository, context.resources)
        }
    )
    val appShellController = rememberAppShellController()
    val conversations = conversationRepository.observeConversations().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedConversationId = conversationRepository.observeSelectedConversationId().collectAsStateWithLifecycle(initialValue = null)
    val chatUiState = chatViewModel.uiState.collectAsStateWithLifecycle()
    val settingsUiState = settingsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(selectedConversationId.value) {
        appShellController.syncSelectedConversation(selectedConversationId.value)
    }

    LaunchedEffect(conversationRepository) {
        conversationRepository.recoverPersistedLaunchState()
    }

    Surface {
        RootScreen(
            appShellController = appShellController,
            conversations = conversations.value,
            chatUiState = chatUiState.value,
            onConversationSelected = { conversationId ->
                appShellController.selectConversation(conversationId)
                chatViewModel.selectConversation(conversationId)
            },
            onConversationRenamed = chatViewModel::renameConversation,
            onConversationDeleted = chatViewModel::deleteConversation,
            onComposerTextChanged = chatViewModel::onComposerTextChanged,
            onSubmitPrompt = chatViewModel::submitPrompt,
            onAttachmentUrisSelected = chatViewModel::onAttachmentUrisSelected,
            onPendingAttachmentRemoved = chatViewModel::removePendingAttachment,
            onStartNewConversation = {
                appShellController.syncSelectedConversation(null)
                appShellController.navigateToChat()
                chatViewModel.selectConversation(null)
            },
            onStopGeneration = chatViewModel::stopGeneration,
            onRegenerateLatestResponse = chatViewModel::regenerateLatestResponse,
            settingsUiState = settingsUiState.value,
            onSettingsApiBaseUrlChanged = settingsViewModel::onApiBaseUrlChanged,
            onSettingsModelIdChanged = settingsViewModel::onModelIdChanged,
            onSettingsApiKeyChanged = settingsViewModel::onApiKeyInputChanged,
            onSaveSettings = settingsViewModel::saveSettings,
            onClearPersistedApiKey = settingsViewModel::clearPersistedApiKey,
        )
    }
}

private fun chatViewModelFactory(
    conversationRepository: RoomConversationRepository,
    settingsRepository: RealSettingsRepository,
    remoteClient: ConfigBackedOpenAiChatCompletionRemoteClient,
    resources: android.content.res.Resources,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }

            return ChatViewModel(
                conversationRepository = conversationRepository,
                streamingRepository = conversationRepository,
                activeChatConfigSource = settingsRepository,
                remoteClient = remoteClient,
                resources = resources,
            ) as T
        }
    }
}

private fun settingsViewModelFactory(
    settingsRepository: RealSettingsRepository,
    resources: android.content.res.Resources,
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }

            return SettingsViewModel(
                settingsRepository = settingsRepository,
                secretSettingsRepository = settingsRepository,
                resources = resources,
            ) as T
        }
    }
}
