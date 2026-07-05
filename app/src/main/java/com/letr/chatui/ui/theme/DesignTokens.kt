package com.letr.chatui.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ChatUiSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val xLarge: Dp = 24.dp,
    val xxLarge: Dp = 32.dp,
)

@Immutable
data class ChatUiShellDimensions(
    val topBarMinHeight: Dp = 72.dp,
    val drawerWidth: Dp = 320.dp,
    val sectionCardMinHeight: Dp = 160.dp,
    val transcriptMaxWidth: Dp = 860.dp,
    val composerMaxWidth: Dp = 820.dp,
    val settingsMaxWidth: Dp = 760.dp,
    val touchTargetMin: Dp = 44.dp,
)

@Immutable
data class ChatUiCorners(
    val medium: RoundedCornerShape = RoundedCornerShape(20.dp),
    val large: RoundedCornerShape = RoundedCornerShape(28.dp),
)

val LocalChatUiSpacing = ChatUiSpacing()
val LocalChatUiShellDimensions = ChatUiShellDimensions()
val LocalChatUiCorners = ChatUiCorners()
