package com.prakhar.lanman.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val Black = Color(0xFF050709)
val White = Color(0xFFFFFFFF)

// Primary Accent
val ElectricCyan = Color(0xFF00E5FF)

// Legacy aliases kept for backwards compatibility
val TextPrimary = White
val TextSecondary = White.copy(alpha = 0.7f)
val TextTertiary = White.copy(alpha = 0.42f)

val GlassSurfaceTint = White.copy(alpha = 0.045f)
val GlassCardTint = White.copy(alpha = 0.08f)
val GlassButtonTint = White.copy(alpha = 0.12f)
val GlassButtonHover = White.copy(alpha = 0.16f)

val GlassBorder = White.copy(alpha = 0.09f)
val GlassBorderActive = White.copy(alpha = 0.18f)

val StatusPending = Color(0xFF8C969E)
val StatusDelivered = ElectricCyan

@Immutable
data class LanmanColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceGlass: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val surfaceButton: Color,
    val dialogSurface: Color,
    val border: Color,
    val borderActive: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentTint: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val pending: Color,
    val bubbleOutgoingConfirmed: Color,
    val bubbleOutgoingPending: Color,
    val bubbleIncomingConfirmed: Color,
    val bubbleIncomingPending: Color
)

val DarkLanmanColors = LanmanColors(
    isDark = true,
    background = Color(0xFF050709),
    surfaceGlass = White.copy(alpha = 0.045f),
    surfaceCard = White.copy(alpha = 0.075f),
    surfaceElevated = Color(0xFF0E1419),
    surfaceButton = White.copy(alpha = 0.12f),
    dialogSurface = Color(0xFF0D1418).copy(alpha = 0.98f),
    border = White.copy(alpha = 0.09f),
    borderActive = ElectricCyan.copy(alpha = 0.35f),
    textPrimary = Color(0xFFF4F7F9),
    textSecondary = Color(0xFFF4F7F9).copy(alpha = 0.68f),
    textTertiary = Color(0xFFF4F7F9).copy(alpha = 0.42f),
    accent = ElectricCyan,
    onAccent = Color(0xFF031014),
    accentTint = ElectricCyan.copy(alpha = 0.13f),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    error = Color(0xFFFF6B6B),
    pending = Color(0xFF8C969E),
    bubbleOutgoingConfirmed = ElectricCyan.copy(alpha = 0.16f),
    bubbleOutgoingPending = ElectricCyan.copy(alpha = 0.07f),
    bubbleIncomingConfirmed = White.copy(alpha = 0.09f),
    bubbleIncomingPending = White.copy(alpha = 0.04f)
)

val LightLanmanColors = LanmanColors(
    isDark = false,
    background = Color(0xFFF5F7FA),
    surfaceGlass = Color(0xFFFFFFFF),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFFFFFFF),
    surfaceButton = Color(0xFFE2E8F0),
    dialogSurface = Color(0xFFFFFFFF),
    border = Color(0xFF0F172A).copy(alpha = 0.09f),
    borderActive = Color(0xFF007C91).copy(alpha = 0.35f),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textTertiary = Color(0xFF94A3B8),
    accent = Color(0xFF007C91),
    onAccent = Color(0xFFFFFFFF),
    accentTint = Color(0xFF007C91).copy(alpha = 0.10f),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    error = Color(0xFFDC2626),
    pending = Color(0xFF64748B),
    bubbleOutgoingConfirmed = Color(0xFF007C91).copy(alpha = 0.14f),
    bubbleOutgoingPending = Color(0xFF007C91).copy(alpha = 0.06f),
    bubbleIncomingConfirmed = Color(0xFFFFFFFF),
    bubbleIncomingPending = Color(0xFFE2E8F0).copy(alpha = 0.65f)
)
