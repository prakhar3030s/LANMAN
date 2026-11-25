package com.prakhar.lanman.ui.common

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
import com.prakhar.lanman.ui.theme.LanmanTheme

/**
 * Reusable Translucent Monolith container: subtle low-alpha tint in dark mode, clean card in
 * light mode, paired with a crisp hairline border.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tint: Color = LanmanTheme.colors.surfaceGlass,
    borderColor: Color = LanmanTheme.colors.border,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint)
            .border(1.dp, borderColor, shape),
        contentAlignment = contentAlignment,
        content = content
    )
}
