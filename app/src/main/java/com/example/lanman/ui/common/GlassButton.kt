package com.example.lanman.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.GlassButtonTint
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import com.example.lanman.ui.theme.Black

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val tint = if (isPrimary) ElectricCyan.copy(alpha = 0.2f) else GlassButtonTint
    val textColor = if (enabled) {
        if (isPrimary) Black else TextPrimary
    } else {
        TextSecondary.copy(alpha = 0.5f)
    }

    GlassSurface(
        modifier = modifier
            .height(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tint = if (enabled) tint else GlassButtonTint.copy(alpha = 0.5f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) textColor else TextPrimary.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(PaddingValues(vertical = 14.dp, horizontal = 24.dp))
        )
    }
}
