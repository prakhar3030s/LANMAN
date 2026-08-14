package com.example.lanman.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import com.example.lanman.domain.model.MessageStatus

/**
 * Modifier to apply the "Magic Moment" delivery sweep animation.
 * The animation triggers precisely once when the message status transitions from
 * Unconfirmed (PENDING/SENT) to Confirmed (DELIVERED/READ) while visible on screen.
 */
fun Modifier.deliverySweep(status: MessageStatus): Modifier = composed {
    var previousStatus by rememberSaveable { mutableStateOf(status) }
    var hasAnimated by rememberSaveable { mutableStateOf(false) }
    
    val isConfirmed = status == MessageStatus.DELIVERED || status == MessageStatus.READ
    val wasConfirmed = previousStatus == MessageStatus.DELIVERED || previousStatus == MessageStatus.READ

    // State 0f: No sweep. State 1f: Sweep completed (full clear glass)
    // If it started as Confirmed, snap immediately to 1f.
    val animatable = remember { Animatable(if (isConfirmed && !hasAnimated) 1f else 0f) }

    LaunchedEffect(status) {
        if (!wasConfirmed && isConfirmed && !hasAnimated) {
            // Live transition! Trigger the sweep
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = LinearEasing)
            )
            hasAnimated = true
        } else if (isConfirmed && hasAnimated) {
            // Already animated, ensure it's at 1f
            animatable.snapTo(1f)
        }
        previousStatus = status
    }

    this.drawWithContent {
        drawContent()
        
        val progress = animatable.value
        if (progress > 0f && progress < 1f) {
            // Draw the sweeping light gradient
            val width = size.width
            val height = size.height
            // Move from left to right, angled
            val offset = (width + height) * progress - height
            
            val sweepBrush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.4f),
                    Color.White.copy(alpha = 0.8f),
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                start = Offset(offset, 0f),
                end = Offset(offset + height, height)
            )
            
            clipRect {
                drawRect(brush = sweepBrush, size = size)
            }
        }
    }
}
