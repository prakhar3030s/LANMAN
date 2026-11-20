package com.example.lanman.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.White

private val MonogramGradients = listOf(
    listOf(Color(0xFF00B8D4), Color(0xFF00697A)),
    listOf(Color(0xFF7C4DFF), Color(0xFF3F1FA8)),
    listOf(Color(0xFF00C853), Color(0xFF00602A)),
    listOf(Color(0xFFFF8A00), Color(0xFF8A3B00)),
    listOf(Color(0xFFFF5252), Color(0xFF8B1A1A)),
    listOf(Color(0xFF00E5FF), Color(0xFF00838F))
)

/**
 * A monogram avatar: gradient disc, initial, and an accent ring while the contact is online.
 * Replaces the bare grey circle (and the bare presence dot) everywhere a person is listed.
 */
@Composable
fun ContactAvatar(
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    showRing: Boolean = true,
    textSize: TextUnit = (size.value * 0.4f).sp
) {
    val gradient = MonogramGradients[
        (displayName.hashCode().let { if (it < 0) -it else it }) % MonogramGradients.size
    ]
    val initial = displayName.trim().take(1).uppercase().ifBlank { "?" }
    val ringWidth = if (size >= 40.dp) 2.dp else 1.5.dp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient))
            .then(
                if (showRing && isOnline) Modifier.border(ringWidth, ElectricCyan, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = White,
            fontSize = textSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** Small presence dot for places that keep the avatar ring subtle. */
@Composable
fun PresenceDot(isOnline: Boolean, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .padding(0.dp)
            .background(if (isOnline) ElectricCyan else White.copy(alpha = 0.25f))
    )
}
