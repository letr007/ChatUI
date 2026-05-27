package com.letr.chatui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.letr.chatui.data.model.ThemeColorOption

private data class ThemePalette(
    val light: ColorScheme,
    val dark: ColorScheme,
)

private val DefaultPalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF27403A),
        onPrimary = Color(0xFFF6F3EC),
        primaryContainer = Color(0xFFD8E5DE),
        onPrimaryContainer = Color(0xFF172A25),
        secondary = Color(0xFF7A5C38),
        onSecondary = Color(0xFFFDF7EF),
        secondaryContainer = Color(0xFFF2E3D1),
        onSecondaryContainer = Color(0xFF342414),
        background = Color(0xFFF3EEE5),
        onBackground = Color(0xFF1A1713),
        surface = Color(0xFFFFFBF4),
        onSurface = Color(0xFF1A1713),
        surfaceVariant = Color(0xFFE7DED0),
        onSurfaceVariant = Color(0xFF4D463C),
        outline = Color(0xFF82786D),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFAFCBBE),
        onPrimary = Color(0xFF173028),
        primaryContainer = Color(0xFF284038),
        onPrimaryContainer = Color(0xFFD8E5DE),
        secondary = Color(0xFFE2C39E),
        onSecondary = Color(0xFF432D12),
        secondaryContainer = Color(0xFF5B4327),
        onSecondaryContainer = Color(0xFFF2E3D1),
        background = Color(0xFF13120F),
        onBackground = Color(0xFFEBE3D8),
        surface = Color(0xFF1B1915),
        onSurface = Color(0xFFEBE3D8),
        surfaceVariant = Color(0xFF322D26),
        onSurfaceVariant = Color(0xFFD0C5B7),
        outline = Color(0xFF9A8F82),
    ),
)

private val BluePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF2057A6),
        onPrimary = Color(0xFFF7FAFF),
        primaryContainer = Color(0xFFD9E7FF),
        onPrimaryContainer = Color(0xFF0E2C57),
        secondary = Color(0xFF5D6B85),
        onSecondary = Color(0xFFF7F9FD),
        secondaryContainer = Color(0xFFDEE7F5),
        onSecondaryContainer = Color(0xFF1C2638),
        background = Color(0xFFF5F7FC),
        onBackground = Color(0xFF141A23),
        surface = Color(0xFFFBFCFF),
        onSurface = Color(0xFF141A23),
        surfaceVariant = Color(0xFFDFE3EC),
        onSurfaceVariant = Color(0xFF414B5A),
        outline = Color(0xFF717A89),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFACC7FF),
        onPrimary = Color(0xFF003062),
        primaryContainer = Color(0xFF004793),
        onPrimaryContainer = Color(0xFFD9E7FF),
        secondary = Color(0xFFC1CCE0),
        onSecondary = Color(0xFF2C3647),
        secondaryContainer = Color(0xFF434D5F),
        onSecondaryContainer = Color(0xFFDEE7F5),
        background = Color(0xFF10141C),
        onBackground = Color(0xFFE3E8F2),
        surface = Color(0xFF171B24),
        onSurface = Color(0xFFE3E8F2),
        surfaceVariant = Color(0xFF2A313D),
        onSurfaceVariant = Color(0xFFC1C7D3),
        outline = Color(0xFF8B93A2),
    ),
)

private val GreenPalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF256C44),
        onPrimary = Color(0xFFF4FBF6),
        primaryContainer = Color(0xFFD3EBDC),
        onPrimaryContainer = Color(0xFF103723),
        secondary = Color(0xFF5E715F),
        onSecondary = Color(0xFFF8FAF7),
        secondaryContainer = Color(0xFFDDE9DB),
        onSecondaryContainer = Color(0xFF1C2A1D),
        background = Color(0xFFF3F8F2),
        onBackground = Color(0xFF171D17),
        surface = Color(0xFFF9FEF8),
        onSurface = Color(0xFF171D17),
        surfaceVariant = Color(0xFFDEE5DB),
        onSurfaceVariant = Color(0xFF434B42),
        outline = Color(0xFF737B72),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFA7D7B7),
        onPrimary = Color(0xFF00391E),
        primaryContainer = Color(0xFF0D5330),
        onPrimaryContainer = Color(0xFFD3EBDC),
        secondary = Color(0xFFC1D0BE),
        onSecondary = Color(0xFF2F3E30),
        secondaryContainer = Color(0xFF465247),
        onSecondaryContainer = Color(0xFFDDE9DB),
        background = Color(0xFF101510),
        onBackground = Color(0xFFE0E5DD),
        surface = Color(0xFF171C17),
        onSurface = Color(0xFFE0E5DD),
        surfaceVariant = Color(0xFF2D332C),
        onSurfaceVariant = Color(0xFFC2C9BF),
        outline = Color(0xFF8C938A),
    ),
)

private val PurplePalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF6A46B9),
        onPrimary = Color(0xFFFBF8FF),
        primaryContainer = Color(0xFFE8DDFF),
        onPrimaryContainer = Color(0xFF2F1661),
        secondary = Color(0xFF725D85),
        onSecondary = Color(0xFFFBF8FD),
        secondaryContainer = Color(0xFFE8DEF2),
        onSecondaryContainer = Color(0xFF2A2033),
        background = Color(0xFFF8F4FB),
        onBackground = Color(0xFF1B1720),
        surface = Color(0xFFFEF9FF),
        onSurface = Color(0xFF1B1720),
        surfaceVariant = Color(0xFFE5DDEC),
        onSurfaceVariant = Color(0xFF4A4452),
        outline = Color(0xFF7C7485),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        onPrimary = Color(0xFF381E72),
        primaryContainer = Color(0xFF51389B),
        onPrimaryContainer = Color(0xFFE8DDFF),
        secondary = Color(0xFFD5C1E6),
        onSecondary = Color(0xFF3A2F48),
        secondaryContainer = Color(0xFF51455F),
        onSecondaryContainer = Color(0xFFE8DEF2),
        background = Color(0xFF141218),
        onBackground = Color(0xFFE9E0EC),
        surface = Color(0xFF1C1A20),
        onSurface = Color(0xFFE9E0EC),
        surfaceVariant = Color(0xFF302B37),
        onSurfaceVariant = Color(0xFFCBC3D2),
        outline = Color(0xFF958E9D),
    ),
)

private val AmberPalette = ThemePalette(
    light = lightColorScheme(
        primary = Color(0xFF8A5200),
        onPrimary = Color(0xFFFFF8F2),
        primaryContainer = Color(0xFFFFDDB8),
        onPrimaryContainer = Color(0xFF2C1600),
        secondary = Color(0xFF73624A),
        onSecondary = Color(0xFFFFF9F2),
        secondaryContainer = Color(0xFFFDE3BF),
        onSecondaryContainer = Color(0xFF2A1D0A),
        background = Color(0xFFFFF7EF),
        onBackground = Color(0xFF1E160D),
        surface = Color(0xFFFFFBF7),
        onSurface = Color(0xFF1E160D),
        surfaceVariant = Color(0xFFF1DECB),
        onSurfaceVariant = Color(0xFF514437),
        outline = Color(0xFF837466),
    ),
    dark = darkColorScheme(
        primary = Color(0xFFFFB95C),
        onPrimary = Color(0xFF492900),
        primaryContainer = Color(0xFF693D00),
        onPrimaryContainer = Color(0xFFFFDDB8),
        secondary = Color(0xFFE3C7A4),
        onSecondary = Color(0xFF41311D),
        secondaryContainer = Color(0xFF594833),
        onSecondaryContainer = Color(0xFFFDE3BF),
        background = Color(0xFF17120D),
        onBackground = Color(0xFFEEE1D3),
        surface = Color(0xFF1F1A15),
        onSurface = Color(0xFFEEE1D3),
        surfaceVariant = Color(0xFF362F27),
        onSurfaceVariant = Color(0xFFD4C3B4),
        outline = Color(0xFF9E8D7E),
    ),
)

private val ChatUiTypography = Typography()

private fun paletteFor(themeColor: ThemeColorOption): ThemePalette {
    return when (themeColor) {
        ThemeColorOption.DEFAULT -> DefaultPalette
        ThemeColorOption.BLUE -> BluePalette
        ThemeColorOption.GREEN -> GreenPalette
        ThemeColorOption.PURPLE -> PurplePalette
        ThemeColorOption.AMBER -> AmberPalette
    }
}

@Composable
fun ChatUiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeColor: ThemeColorOption = ThemeColorOption.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeColor)
    MaterialTheme(
        colorScheme = if (darkTheme) palette.dark else palette.light,
        typography = ChatUiTypography,
        content = content,
    )
}
