package com.prakhar.lanman.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The single click affordance used across cards, rows and glass surfaces: the theme ripple plus a
 * tactile tick, so a glass card feels the same as an IconButton.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    combinedClickable(
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        enabled = enabled,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        onLongClick = onLongClick?.let {
            {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                it()
            }
        }
    )
}

/** Caps content width on large screens instead of letting it stretch edge to edge. */
fun Modifier.contentWidth(maxWidth: Dp = 600.dp): Modifier = widthIn(max = maxWidth).fillMaxWidth()

/** Width shared by multi-column button rows so paired actions line up on every screen. */
val ActionButtonHeight: Dp = 48.dp
