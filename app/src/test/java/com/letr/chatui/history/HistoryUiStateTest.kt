package com.letr.chatui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryUiStateTest {
    @Test
    fun `normalizeHistoryConversationTitle trims surrounding whitespace`() {
        assertEquals("Project kickoff", normalizeHistoryConversationTitle("  Project kickoff  "))
    }

    @Test
    fun `historyConversationTimestampLabel uses formatter output consistently`() {
        val label = historyConversationTimestampLabel(1_234L) { "Jan 2, 3:04 PM" }

        assertEquals("Jan 2, 3:04 PM", label)
    }
}
