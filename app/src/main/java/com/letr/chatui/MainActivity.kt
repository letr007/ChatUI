package com.letr.chatui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.letr.chatui.app.ChatUiApp
import com.letr.chatui.ui.theme.ChatUiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChatUiTheme {
                ChatUiApp()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    ChatUiTheme {
        ChatUiApp()
    }
}
