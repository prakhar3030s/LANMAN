package com.example.lanman.ui.theme

import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

// Primary Accent
val ElectricCyan = Color(0xFF00E5FF)

// Text Colors
val TextPrimary = White
val TextSecondary = White.copy(alpha = 0.7f)
val TextTertiary = White.copy(alpha = 0.4f)

// Glass Overlays (to be used over Black with Haze blur)
val GlassSurfaceTint = White.copy(alpha = 0.04f)
val GlassCardTint = White.copy(alpha = 0.08f)
val GlassButtonTint = White.copy(alpha = 0.12f)
val GlassButtonHover = White.copy(alpha = 0.16f)

// Inner borders
val GlassBorder = White.copy(alpha = 0.08f)
val GlassBorderActive = White.copy(alpha = 0.16f)

// Semantic colors (Phase 19 but defined now)
val StatusPending = Color(0xFF888888)
val StatusDelivered = ElectricCyan
