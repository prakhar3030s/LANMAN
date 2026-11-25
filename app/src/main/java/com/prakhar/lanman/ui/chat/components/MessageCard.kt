package com.prakhar.lanman.ui.chat.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.prakhar.lanman.domain.model.FileTransfer
import com.prakhar.lanman.domain.model.MessageDirection
import com.prakhar.lanman.domain.model.MessageStatus
import com.prakhar.lanman.domain.model.MessageType
import com.prakhar.lanman.ui.chat.MessageUiModel
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.common.GlassDialog
import com.prakhar.lanman.ui.common.GlassDialogText
import com.prakhar.lanman.ui.common.GlassSurface
import com.prakhar.lanman.ui.common.MediaActions
import com.prakhar.lanman.ui.common.deliverySweep
import com.prakhar.lanman.ui.common.pressable
import com.prakhar.lanman.ui.theme.LanmanTheme
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
    onPreviewImage: (FileTransfer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LanmanTheme.colors
    val message = uiModel.message
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val isConfirmed = message.status == MessageStatus.DELIVERED || message.status == MessageStatus.READ
    val isFailed = message.status == MessageStatus.FAILED

    val alignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val shape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val baseTint = if (isOutgoing) {
        if (isConfirmed) colors.bubbleOutgoingConfirmed else colors.bubbleOutgoingPending
    } else {
        if (isConfirmed) colors.bubbleIncomingConfirmed else colors.bubbleIncomingPending
    }

    val textColor = if (isConfirmed) colors.textPrimary else colors.textPrimary.copy(alpha = 0.7f)

    val context = LocalContext.current
    val fileTransfer = uiModel.fileTransfer
    var showFileActions by remember { mutableStateOf(false) }

    val hasActionableFile = isConfirmed && fileTransfer != null &&
        (message.type == MessageType.FILE || message.type == MessageType.VOICE)
    val resolvedMediaPath = remember(fileTransfer, isOutgoing) {
        if (fileTransfer == null) ""
        else if (message.type == MessageType.VOICE && isOutgoing && !fileTransfer.thumbnailUri.isNullOrBlank()) {
            fileTransfer.thumbnailUri
        } else {
            fileTransfer.localUri
        }
    }

    if (showFileActions && fileTransfer != null) {
        GlassDialog(
            onDismissRequest = { showFileActions = false },
            title = fileTransfer.fileName,
            confirmLabel = "Close",
            dismissLabel = null,
            onConfirm = { showFileActions = false }
        ) {
            Column {
                GlassDialogText("Choose what to do with this ${if (message.type == MessageType.VOICE) "voice note" else "file"}.")
                Spacer(modifier = Modifier.height(8.dp))
                DialogAction("Open") {
                    showFileActions = false
                    if (!MediaActions.openFile(context, resolvedMediaPath, fileTransfer.mimeType)) {
                        Toast.makeText(context, "Could not open this file", Toast.LENGTH_SHORT).show()
                    }
                }
                DialogAction("Share") {
                    showFileActions = false
                    if (!MediaActions.shareFile(context, resolvedMediaPath, fileTransfer.mimeType)) {
                        Toast.makeText(context, "Could not share this file", Toast.LENGTH_SHORT).show()
                    }
                }
                DialogAction("Save to Downloads") {
                    showFileActions = false
                    val downloadName = if (message.type == MessageType.VOICE && !fileTransfer.fileName.endsWith(".m4a")) {
                        "LANMAN_voice_${message.timestamp}.m4a"
                    } else {
                        fileTransfer.fileName
                    }
                    val saved = MediaActions.saveToDownloads(
                        context,
                        resolvedMediaPath,
                        downloadName,
                        if (message.type == MessageType.VOICE) "audio/mp4" else fileTransfer.mimeType
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        contentAlignment = alignment
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .deliverySweep(message.status)
                .pointerInput(message.id, hasActionableFile, resolvedMediaPath) {
                    detectTapGestures(
                        onTap = if (hasActionableFile && message.type == MessageType.FILE) {
                            {
                                if (fileTransfer.mimeType.startsWith("image/") && resolvedMediaPath.isNotBlank()) {
                                    onPreviewImage(fileTransfer)
                                } else if (!MediaActions.openFile(context, resolvedMediaPath, fileTransfer.mimeType)) {
                                    Toast.makeText(context, "Could not open this file", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else null,
                        onLongPress = {
                            if (hasActionableFile) {
                                showFileActions = true
                            } else {
                                onLongPress()
                            }
                        }
                    )
                },
            shape = shape,
            tint = baseTint,
            borderColor = if (isFailed) colors.error.copy(alpha = 0.45f) else colors.border
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 8.dp)
            ) {
                val isFile = message.type == MessageType.FILE
                val isVoice = message.type == MessageType.VOICE

                if (isFile || isVoice) {
                    if (fileTransfer != null) {
                        val isVideo = fileTransfer.mimeType.startsWith("video/")
                        val isImage = fileTransfer.mimeType.startsWith("image/")
                        val isMedia = isVideo || isImage

                        val metrics = uiModel.transferMetrics
                        val progressVal = (if (message.status == MessageStatus.SENT) 1f else uiModel.progress ?: 0f).coerceIn(0f, 1f)
                        val percent = (progressVal * 100).toInt()
                        val speedLabel = metrics?.bytesPerSecond?.takeIf { it > 0L }?.let { "${formatSize(it)}/s" }

                        if (isMedia) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceCard),
                                contentAlignment = Alignment.Center
                            ) {
                                val imageModel: Any? = if (!isConfirmed && fileTransfer.thumbnailUri == null) {
                                    null
                                } else if (fileTransfer.thumbnailUri != null) {
                                    if (fileTransfer.thumbnailUri.startsWith("content://")) {
                                        Uri.parse(fileTransfer.thumbnailUri)
                                    } else {
                                        File(fileTransfer.thumbnailUri)
                                    }
                                } else {
                                    File(fileTransfer.localUri)
                                }

                                val imageRequest = ImageRequest.Builder(context)
                                    .data(imageModel)
                                    .apply {
                                        if (isVideo) {
                                            decoderFactory(VideoFrameDecoder.Factory())
                                        }
                                    }
                                    .crossfade(true)
                                    .build()

                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = fileTransfer.fileName,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isFailed) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .background(Color.Black.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Transfer failed",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                } else if (!isConfirmed) {
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
                                                progress = { progressVal },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .semantics {
                                                        contentDescription = "Transfer $percent percent complete"
                                                    },
                                                color = colors.accent,
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

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (!isConfirmed && !isFailed) {
                                            listOfNotNull(
                                                "$percent%",
                                                formatSize(fileTransfer.fileSizeBytes),
                                                speedLabel
                                            ).joinToString(" \u00B7 ")
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(colors.error.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Transfer failed",
                                            tint = colors.error,
                                            modifier = Modifier.size(20.dp)
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { progressVal },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .semantics {
                                                    contentDescription = "Transfer $percent percent complete"
                                                },
                                            color = colors.accent,
                                            trackColor = colors.textTertiary.copy(alpha = 0.3f),
                                            strokeWidth = 3.dp
                                        )
                                        Icon(
                                            imageVector = AppIcons.Mic,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Voice message \u00B7 $percent%",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor
                                    )
                                }
                            } else {
                                val durationMs = String(message.content, StandardCharsets.UTF_8)
                                    .removePrefix("voice:")
                                    .toLongOrNull() ?: 0L
                                val audioFile = if (isOutgoing && !fileTransfer.thumbnailUri.isNullOrBlank()) {
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.accentTint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isFailed) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Transfer failed",
                                            tint = colors.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else if (!isConfirmed) {
                                        CircularProgressIndicator(
                                            progress = { progressVal },
                                            modifier = Modifier
                                                .size(34.dp)
                                                .semantics {
                                                    contentDescription = "Transfer $percent percent complete"
                                                },
                                            color = colors.accent,
                                            trackColor = colors.textTertiary.copy(alpha = 0.3f),
                                            strokeWidth = 3.dp
                                        )
                                        Icon(
                                            imageVector = AppIcons.InsertDriveFile,
                                            contentDescription = "Transferring",
                                            tint = colors.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = AppIcons.InsertDriveFile,
                                            contentDescription = "File",
                                            tint = colors.accent,
                                            modifier = Modifier.size(24.dp)
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
                                            !isConfirmed -> listOfNotNull(
                                                "$percent% of ${formatSize(fileTransfer.fileSizeBytes)}",
                                                speedLabel
                                            ).joinToString(" \u00B7 ")
                                            else -> formatSize(fileTransfer.fileSizeBytes)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textSecondary
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

                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isOutgoing && !isConfirmed && !isFailed && (isFile || isVoice)) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            modifier = Modifier
                                .clickable(onClick = onCancel)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .semantics { contentDescription = "Cancel transfer" }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(6.dp))
                        when {
                            isFailed -> Text(
                                text = "Failed \u00B7 Retry",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.error,
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
                                text = "Sending\u2026",
                                description = "Sending"
                            )
                            else -> {
                                Icon(
                                    imageVector = AppIcons.DoneAll,
                                    contentDescription = if (message.status == MessageStatus.READ) "Read" else "Delivered",
                                    tint = if (message.status == MessageStatus.READ) colors.accent else colors.textSecondary,
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
        Text(label, color = LanmanTheme.colors.textPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusBadge(text: String, description: String) {
    val color = LanmanTheme.colors.pending
    Icon(
        imageVector = AppIcons.Schedule,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(14.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.semantics { contentDescription = description }
    )
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun formatSize(bytes: Long): String {
    return when {
        bytes <= 0 -> "Unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}
