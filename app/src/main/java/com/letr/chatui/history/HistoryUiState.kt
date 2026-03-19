package com.letr.chatui.history

import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class HistoryRenameTarget(
    val conversationId: com.letr.chatui.data.model.ConversationId,
    val originalTitle: String,
)

fun normalizeHistoryConversationTitle(input: String): String {
    return input.trim()
}

fun historyConversationTimestampLabel(
    updatedAtEpochMillis: Long,
    formatter: (Long) -> String = ::defaultHistoryTimestampFormatter,
): String {
    return formatter(updatedAtEpochMillis)
}

private fun defaultHistoryTimestampFormatter(updatedAtEpochMillis: Long): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault(),
    ).format(Date(updatedAtEpochMillis))
}
