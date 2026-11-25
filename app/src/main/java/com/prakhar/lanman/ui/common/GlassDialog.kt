package com.prakhar.lanman.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.prakhar.lanman.ui.theme.LanmanTheme

@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = "Cancel",
    onDismiss: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LanmanTheme.colors
    Dialog(onDismissRequest = onDismissRequest) {
        GlassSurface(
            modifier = modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tint = colors.dialogSurface
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!dismissLabel.isNullOrEmpty()) {
                        TextButton(onClick = {
                            onDismiss?.invoke()
                            onDismissRequest()
                        }) {
                            Text(dismissLabel, color = colors.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    GlassButton(
                        text = confirmLabel,
                        onClick = onConfirm,
                        isPrimary = !destructive,
                        destructive = destructive,
                        enabled = confirmEnabled,
                        modifier = Modifier.widthIn(min = 112.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GlassDialogText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LanmanTheme.colors.textSecondary
    )
}
