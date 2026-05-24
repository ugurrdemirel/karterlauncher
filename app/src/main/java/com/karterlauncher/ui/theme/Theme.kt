package com.karterlauncher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF000000)
private val Surface = Color(0xFF1C1C1E)
private val SurfaceVariant = Color(0xFF2C2C2E)
/** Left pinned rail — slightly lifted from pure black like in-car stacks */
private val SidebarSurface = Color(0xFF121212)
/** Main content panel over dashboard */
private val MainPanel = Color(0xFF1A1A1C)
private val Accent = Color(0xFF34C759)
private val OnDark = Color(0xFFF2F2F7)
private val OnMuted = Color(0xFF8E8E93)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    background = Background,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnMuted,
    outline = Color(0xFF3A3A3C),
    surfaceContainerLow = MainPanel,
    surfaceContainer = SidebarSurface,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF248A3D),
    onPrimary = Color.White,
    background = Color(0xFFE8E8ED),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    outline = Color(0xFFD1D1D6),
    surfaceContainerLow = Color(0xFFF5F5F7),
    surfaceContainer = Color(0xFFFFFFFF),
)

@Composable
fun LauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LauncherTypography,
        content = content,
    )
}
