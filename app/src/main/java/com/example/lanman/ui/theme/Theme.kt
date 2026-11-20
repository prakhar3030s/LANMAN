package com.example.lanman.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    background = Black,
    surface = Black,
    onPrimary = Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    // Material defaults (surfaceVariant, containers) were the last non-glass surfaces in the app;
    // keep any stray Material component on the same dark tint as everything else.
    surfaceVariant = Color(0xFF10181B),
    onSurfaceVariant = TextPrimary,
    surfaceContainer = Color(0xFF0D1418),
    surfaceContainerHigh = Color(0xFF131C20),
    surfaceContainerHighest = Color(0xFF162124),
    primaryContainer = ElectricCyan.copy(alpha = 0.16f),
    onPrimaryContainer = TextPrimary,
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFFB4AB)
)

@Composable
fun LANMANTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
