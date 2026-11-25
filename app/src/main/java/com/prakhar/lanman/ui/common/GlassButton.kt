package com.prakhar.lanman.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prakhar.lanman.ui.theme.LanmanTheme
import com.prakhar.lanman.ui.theme.defaultMonolithSpring

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true
) {
    val colors = LanmanTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = defaultMonolithSpring(),
        label = "buttonScale"
    )

    val tint = when {
        !enabled -> colors.surfaceButton.copy(alpha = 0.45f)
        destructive -> colors.error.copy(alpha = 0.16f)
        isPrimary -> colors.accent
        else -> colors.surfaceButton
    }
    val textColor = when {
        !enabled -> colors.textTertiary
        destructive -> colors.error
        isPrimary -> colors.onAccent
        else -> colors.textPrimary
    }
    val borderColor = when {
        destructive && enabled -> colors.error.copy(alpha = 0.35f)
        isPrimary && enabled -> colors.accent
        else -> colors.border
    }

    GlassSurface(
        modifier = modifier
            .scale(scale)
            .height(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        tint = tint,
        borderColor = borderColor,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(PaddingValues(vertical = 12.dp, horizontal = 20.dp))
        )
    }
}
