package com.example.lanman.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary

private val DialogGlass = Color(0xFF0D1418).copy(alpha = 0.97f)

/**
 * The app-wide dialog surface. Material's AlertDialog used `surfaceVariant`, which read as a
 * different app; this keeps the glass tint and border every other surface uses.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
    onDismiss: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        GlassSurface(
            modifier = modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tint = DialogGlass
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onDismiss?.invoke()
                        onDismissRequest()
                    }) {
                        Text(dismissLabel, color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    GlassButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        isPrimary = !destructive,
                        enabled = confirmEnabled,
                        modifier = Modifier.widthIn(min = 120.dp)
                    )
                }
            }
        }
    }
}

/** Standard body copy inside [GlassDialog]. */
@Composable
fun GlassDialogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary
    )
}
