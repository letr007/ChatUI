package com.letr.chatui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberAppShellController(): AppShellController {
    return remember {
        AppShellController()
    }
}
