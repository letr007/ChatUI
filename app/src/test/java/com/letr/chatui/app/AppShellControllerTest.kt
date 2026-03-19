package com.letr.chatui.app

import com.letr.chatui.data.model.ConversationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellControllerTest {
    @Test
    fun startsOnChatWithClosedHistoryDrawer() {
        val controller = AppShellController()

        assertEquals(AppDestination.CHAT, controller.currentDestination)
        assertFalse(controller.isHistoryDrawerOpen)
        assertEquals(null, controller.selectedConversationId)
    }

    @Test
    fun navigateToSettingsClosesDrawerAndChangesDestination() {
        val controller = AppShellController()

        controller.openHistoryDrawer()
        controller.navigateToSettings()

        assertEquals(AppDestination.SETTINGS, controller.currentDestination)
        assertFalse(controller.isHistoryDrawerOpen)
    }

    @Test
    fun selectingConversationReturnsToChatAndClosesDrawer() {
        val controller = AppShellController(initialDestination = AppDestination.SETTINGS)
        val conversationId = ConversationId("conversation-123")

        controller.openHistoryDrawer()
        controller.selectConversation(conversationId)

        assertEquals(AppDestination.CHAT, controller.currentDestination)
        assertEquals(conversationId, controller.selectedConversationId)
        assertFalse(controller.isHistoryDrawerOpen)
    }

    @Test
    fun syncSelectedConversationUpdatesShellSelection() {
        val controller = AppShellController()
        val conversationId = ConversationId("conversation-456")

        controller.syncSelectedConversation(conversationId)

        assertTrue(controller.selectedConversationId == conversationId)
    }
}
