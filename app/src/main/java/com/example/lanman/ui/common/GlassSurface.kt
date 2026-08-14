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
 * A reusable frosted glass container.
 * 
 * Note: To prevent Haze crashes, hazeChild is no longer applied here.
 * The glass effect is achieved via the semi-transparent tint and border.
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
