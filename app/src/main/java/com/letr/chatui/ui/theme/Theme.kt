package com.letr.chatui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF27403A),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFF6F3EC),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD8E5DE),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF172A25),
    secondary = androidx.compose.ui.graphics.Color(0xFF7A5C38),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFDF7EF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFF2E3D1),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF342414),
    background = androidx.compose.ui.graphics.Color(0xFFF3EEE5),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A1713),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFBF4),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1713),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE7DED0),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4D463C),
    outline = androidx.compose.ui.graphics.Color(0xFF82786D),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFAFCBBE),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF173028),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF284038),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD8E5DE),
    secondary = androidx.compose.ui.graphics.Color(0xFFE2C39E),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF432D12),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF5B4327),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFF2E3D1),
    background = androidx.compose.ui.graphics.Color(0xFF13120F),
    onBackground = androidx.compose.ui.graphics.Color(0xFFEBE3D8),
    surface = androidx.compose.ui.graphics.Color(0xFF1B1915),
    onSurface = androidx.compose.ui.graphics.Color(0xFFEBE3D8),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF322D26),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD0C5B7),
    outline = androidx.compose.ui.graphics.Color(0xFF9A8F82),
)

private val ChatUiTypography = Typography()

@Composable
fun ChatUiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ChatUiTypography,
        content = content,
    )
}
