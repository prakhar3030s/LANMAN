package com.example.lanman.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.lanman.ui.theme.GlassBorder
import com.example.lanman.ui.theme.GlassSurfaceTint

/**
 * A reusable frosted glass container: a low-alpha tint plus a hairline border.
 *
 * The effect is deliberately flat rather than a blurred backdrop — the haze blur pipeline was
 * dropped from the project, so this is the single glass surface every screen uses.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = GlassSurfaceTint,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .border(1.dp, GlassBorder, shape),
        contentAlignment = contentAlignment,
        content = content
    )
}
