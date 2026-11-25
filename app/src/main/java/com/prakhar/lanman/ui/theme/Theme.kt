package com.prakhar.lanman.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.prakhar.lanman.data.notification.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = DarkLanmanColors.accent,
    onPrimary = DarkLanmanColors.onAccent,
    background = DarkLanmanColors.background,
    onBackground = DarkLanmanColors.textPrimary,
    surface = DarkLanmanColors.background,
    onSurface = DarkLanmanColors.textPrimary,
    surfaceVariant = Color(0xFF10181B),
    onSurfaceVariant = DarkLanmanColors.textSecondary,
    surfaceContainer = Color(0xFF0D1418),
    surfaceContainerHigh = Color(0xFF131C20),
    surfaceContainerHighest = Color(0xFF162124),
    primaryContainer = DarkLanmanColors.accentTint,
    onPrimaryContainer = DarkLanmanColors.textPrimary,
    error = DarkLanmanColors.error,
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = LightLanmanColors.accent,
    onPrimary = LightLanmanColors.onAccent,
    background = LightLanmanColors.background,
    onBackground = LightLanmanColors.textPrimary,
    surface = LightLanmanColors.surfaceGlass,
    onSurface = LightLanmanColors.textPrimary,
    surfaceVariant = Color(0xFFE8EEF4),
    onSurfaceVariant = LightLanmanColors.textSecondary,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF1F5F9),
    primaryContainer = LightLanmanColors.accentTint,
    onPrimaryContainer = LightLanmanColors.textPrimary,
    error = LightLanmanColors.error,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

val LocalLanmanColors = staticCompositionLocalOf { DarkLanmanColors }

object LanmanTheme {
    val colors: LanmanColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLanmanColors.current
}

@Composable
fun LANMANTheme(
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val lanmanColors = if (isDark) DarkLanmanColors else LightLanmanColors
    val materialScheme = if (isDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalLanmanColors provides lanmanColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = Typography,
            content = content
        )
    }
}
