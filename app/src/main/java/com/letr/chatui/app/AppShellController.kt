package com.letr.chatui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.letr.chatui.data.model.ConversationId

class AppShellController(
    initialDestination: AppDestination = AppDestination.CHAT,
    initialSelectedConversationId: ConversationId? = null,
) {
    var currentDestination by mutableStateOf(initialDestination)
        private set

    var selectedConversationId by mutableStateOf(initialSelectedConversationId)
        private set

    var isHistoryDrawerOpen by mutableStateOf(false)
        private set

    fun openHistoryDrawer() {
        isHistoryDrawerOpen = true
    }

    fun closeHistoryDrawer() {
        isHistoryDrawerOpen = false
    }

    fun navigateToChat() {
        currentDestination = AppDestination.CHAT
    }

    fun navigateToSettings() {
        closeHistoryDrawer()
        currentDestination = AppDestination.SETTINGS
    }

    fun selectConversation(conversationId: ConversationId) {
        selectedConversationId = conversationId
        currentDestination = AppDestination.CHAT
        closeHistoryDrawer()
    }

    fun syncSelectedConversation(conversationId: ConversationId?) {
        selectedConversationId = conversationId
    }
}
