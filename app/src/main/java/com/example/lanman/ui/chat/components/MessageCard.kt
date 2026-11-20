package com.example.lanman.ui.chat.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.lanman.domain.model.FileTransfer
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.ui.chat.MessageUiModel
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.MediaActions
import com.example.lanman.ui.common.deliverySweep
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.StatusDelivered
import com.example.lanman.ui.theme.StatusPending
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageCard(
    uiModel: MessageUiModel,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val message = uiModel.message
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val isConfirmed = message.status == MessageStatus.DELIVERED || message.status == MessageStatus.READ
    val isFailed = message.status == MessageStatus.FAILED

    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    // Two visual tiers: Unconfirmed (matte/desaturated) vs Confirmed (glowing frosted glass)
    val baseTint = if (isOutgoing) {
        ElectricCyan.copy(alpha = if (isConfirmed) 0.15f else 0.05f)
    } else {
        Color.White.copy(alpha = if (isConfirmed) 0.10f else 0.03f)
    }

    val textColor = if (isConfirmed) TextPrimary else TextPrimary.copy(alpha = 0.6f)

    val context = LocalContext.current
    val fileTransfer = uiModel.fileTransfer
    var showFileActions by remember { mutableStateOf(false) }

    // Only openable once the transfer is complete; a pending file has no finished content yet.
    val fileTapModifier = if (isConfirmed && message.type == MessageType.FILE && fileTransfer != null) {
        Modifier.pointerInput(fileTransfer.localUri) {
            detectTapGestures(
                onTap = {
                    if (!MediaActions.openFile(context, fileTransfer.localUri, fileTransfer.mimeType)) {
                        Toast.makeText(context, "Could not open this file", Toast.LENGTH_SHORT).show()
                    }
                },
                onLongPress = { showFileActions = true }
            )
        }
    } else Modifier

    if (showFileActions && fileTransfer != null) {
        GlassDialog(
            onDismissRequest = { showFileActions = false },
            title = fileTransfer.fileName,
            confirmLabel = "Close",
            onConfirm = { showFileActions = false }
        ) {
            Column {
                GlassDialogText("Choose what to do with this file.")
                Spacer(modifier = Modifier.height(8.dp))
                DialogAction("Open") {
                    showFileActions = false
                    MediaActions.openFile(context, fileTransfer.localUri, fileTransfer.mimeType)
                }
                DialogAction("Share") {
                    showFileActions = false
                    MediaActions.shareFile(context, fileTransfer.localUri, fileTransfer.mimeType)
                }
                DialogAction("Save to Downloads") {
                    showFileActions = false
                    val saved = MediaActions.saveToDownloads(
                        context,
                        fileTransfer.localUri,
                        fileTransfer.fileName,
                        fileTransfer.mimeType
                    )
                    Toast.makeText(
                        context,
                        if (saved) "Saved to Downloads/LANMAN" else "Could not save this file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        // Flush edge-to-edge message card (per brief, no tails)
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .deliverySweep(message.status)
                // Long-press anywhere on the bubble offers the same actions, whatever its type.
                .pointerInput(message.id) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
                .then(fileTapModifier),
            shape = shape,
            tint = baseTint
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
            ) {
                val isFile = message.type == MessageType.FILE
                val isVoice = message.type == MessageType.VOICE

                if (isFile || isVoice) {
                    if (fileTransfer != null) {
                        val isVideo = fileTransfer.mimeType.startsWith("video/")
                        val isImage = fileTransfer.mimeType.startsWith("image/")
                        val isMedia = isVideo || isImage

                        val progressVal = (if (message.status == MessageStatus.SENT) 1f else uiModel.progress ?: 0f).coerceIn(0f, 1f)
                        val percent = (progressVal * 100).toInt()

                        if (isMedia) {
                            // Telegram-style Media Card (Video / Image)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val imageModel: Any? = if (!isConfirmed && fileTransfer.thumbnailUri == null) null else if (fileTransfer.thumbnailUri != null) {
                                    if (fileTransfer.thumbnailUri.startsWith("content://")) {
                                        Uri.parse(fileTransfer.thumbnailUri)
                                    } else {
                                        File(fileTransfer.thumbnailUri)
                                    }
                                } else {
                                    File(fileTransfer.localUri)
                                }

                                val imageRequest = coil.request.ImageRequest.Builder(context)
                                    .data(imageModel)
                                    .apply {
                                        if (isVideo) {
                                            decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                        }
                                    }
                                    .crossfade(true)
                                    .build()

                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = fileTransfer.fileName,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )

                                if (isFailed) {
                                    // Terminal failure: no progress ring, this file can never arrive
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Transfer failed",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                } else if (!isConfirmed) {
                                    // Progress Ring Overlay (While uploading or downloading)
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(Color.Black.copy(alpha = 0.65f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = progressVal,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .semantics {
                                                        contentDescription = "Transfer $percent percent complete"
                                                    },
                                                color = ElectricCyan,
                                                trackColor = Color.White.copy(alpha = 0.25f),
                                                strokeWidth = 3.5.dp
                                            )
                                            if (isVideo) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Video",
                                                    tint = Color.White.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                } else if (isVideo) {
                                    // Translucent Play Button overlay for Completed Video
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play video",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                // Bottom-left size pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (!isConfirmed && !isFailed) {
                                            "$percent% · ${formatSize(fileTransfer.fileSizeBytes)}"
                                        } else {
                                            formatSize(fileTransfer.fileSizeBytes)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        } else if (isVoice) {
                            if (isFailed) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Transfer failed",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Voice message failed",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                }
                            } else if (!isConfirmed) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(ElectricCyan.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = progressVal,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .semantics {
                                                    contentDescription = "Transfer $percent percent complete"
                                                },
                                            color = ElectricCyan,
                                            trackColor = Color.White.copy(alpha = 0.2f),
                                            strokeWidth = 3.dp
                                        )
                                        Icon(
                                            imageVector = AppIcons.Mic,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Voice message · $percent%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                }
                            } else {
                                val durationMs = String(message.content, StandardCharsets.UTF_8).removePrefix("voice:").toLongOrNull() ?: 0L
                                val audioFile = if (isOutgoing && fileTransfer.thumbnailUri != null) {
                                    File(fileTransfer.thumbnailUri)
                                } else {
                                    File(fileTransfer.localUri)
                                }
                                VoicePlayer(
                                    audioFile = audioFile,
                                    durationMs = durationMs,
                                    textColor = textColor
                                )
                            }
                        } else {
                            // Telegram-style Document / Generic File Card
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ElectricCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFailed) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Transfer failed",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    } else if (!isConfirmed) {
                                        CircularProgressIndicator(
                                            progress = progressVal,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .semantics {
                                                    contentDescription = "Transfer $percent percent complete"
                                                },
                                            color = ElectricCyan,
                                            trackColor = Color.White.copy(alpha = 0.2f),
                                            strokeWidth = 3.dp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Transferring",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "File",
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileTransfer.fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = textColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = when {
                                            isFailed -> "Transfer failed"
                                            !isConfirmed -> "$percent% of ${formatSize(fileTransfer.fileSizeBytes)}"
                                            else -> formatSize(fileTransfer.fileSizeBytes)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Loading transfer info...", color = textColor)
                    }
                } else {
                    Text(
                        text = String(message.content, StandardCharsets.UTF_8),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor
                    )
                }

                // Status affordance: time, delivery state, and the actions that make sense while
                // the message is still in flight (cancel) or dead (retry).
                if (isOutgoing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isConfirmed && !isFailed && (isFile || isVoice)) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                modifier = Modifier
                                    .clickable(onClick = onCancel)
                                    .padding(4.dp)
                                    .semantics { contentDescription = "Cancel transfer" }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = formatTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        when {
                            isFailed -> Text(
                                text = "Failed · Retry",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clickable(onClick = onRetry)
                                    .padding(vertical = 2.dp)
                                    .semantics { contentDescription = "Message failed, tap to retry" }
                            )
                            message.status == MessageStatus.PENDING -> StatusBadge(
                                text = "Queued",
                                description = "Queued, waiting to send"
                            )
                            message.status == MessageStatus.SENT -> StatusBadge(
                                text = "Sending…",
                                description = "Sending"
                            )
                            else -> {
                                // Double check for delivered/read, matching the clock above for queued.
                                Icon(
                                    imageVector = AppIcons.DoneAll,
                                    contentDescription = if (message.status == MessageStatus.READ) "Read" else "Delivered",
                                    tint = if (message.status == MessageStatus.READ) ElectricCyan else StatusDelivered.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Clock plus label, the shared visual for a message that is not on the wire yet. */
@Composable
private fun StatusBadge(text: String, description: String) {
    Icon(
        imageVector = AppIcons.Schedule,
        contentDescription = null,
        tint = StatusPending,
        modifier = Modifier.size(14.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = StatusPending,
        modifier = Modifier.semantics { contentDescription = description }
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "Unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}
