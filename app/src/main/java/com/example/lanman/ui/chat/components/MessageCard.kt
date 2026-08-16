package com.example.lanman.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lanman.domain.model.Message
import com.example.lanman.domain.model.MessageDirection
import com.example.lanman.domain.model.MessageStatus
import com.example.lanman.domain.model.MessageType
import com.example.lanman.ui.chat.MessageUiModel
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.deliverySweep
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import java.io.File

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.text.font.FontWeight

@Composable
fun MessageCard(
    uiModel: MessageUiModel,
    modifier: Modifier = Modifier
) {
    val message = uiModel.message
    val isOutgoing = message.direction == MessageDirection.OUTGOING
    val isConfirmed = message.status == MessageStatus.DELIVERED || message.status == MessageStatus.READ
    
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
    
    // Tap handlers for files
    val clickModifier = if (message.type == MessageType.FILE && uiModel.fileTransfer != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    try {
                        val uri = if (uiModel.fileTransfer.localUri.startsWith("content://")) {
                            android.net.Uri.parse(uiModel.fileTransfer.localUri)
                        } else {
                            val file = File(uiModel.fileTransfer.localUri)
                            if (!file.exists()) return@detectTapGestures
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        }
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, uiModel.fileTransfer.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open File"))
                    } catch (e: Exception) {
                        android.util.Log.e("MessageCard", "Failed to open file", e)
                    }
                },
                onLongPress = {
                    try {
                        val uri = if (uiModel.fileTransfer.localUri.startsWith("content://")) {
                            android.net.Uri.parse(uiModel.fileTransfer.localUri)
                        } else {
                            val file = File(uiModel.fileTransfer.localUri)
                            if (!file.exists()) return@detectTapGestures
                            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = uiModel.fileTransfer.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share File"))
                    } catch (e: Exception) {
                        android.util.Log.e("MessageCard", "Failed to share file", e)
                    }
                }
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        contentAlignment = alignment
    ) {
        // Flush edge-to-edge message card (per brief, no tails)
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .deliverySweep(message.status)
                .then(clickModifier),
            shape = shape,
            tint = baseTint
        ) {
            Column(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
            ) {
                // Content
                val isFile = message.type == MessageType.FILE
                val isVoice = message.type == MessageType.VOICE

                if (isFile || isVoice) {
                    val fileTransfer = uiModel.fileTransfer
                    if (fileTransfer != null) {
                        val isVideo = fileTransfer.mimeType.startsWith("video/")
                        val isImage = fileTransfer.mimeType.startsWith("image/")
                        val isMedia = isVideo || isImage
                        
                        val progressVal = (if (message.status == MessageStatus.SENT) 1f else uiModel.progress ?: 0f).coerceIn(0f, 1f)

                        if (isMedia) {
                            // Telegram-style Media Card (Video / Image)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp, max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val imageModel: Any = if (fileTransfer.thumbnailUri != null) {
                                    if (fileTransfer.thumbnailUri.startsWith("content://")) {
                                        android.net.Uri.parse(fileTransfer.thumbnailUri)
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

                                if (!isConfirmed) {
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
                                                modifier = Modifier.size(44.dp),
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
                                            contentDescription = "Play Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(34.dp)
                                        )
                                    }
                                }

                                // Bottom-left file size pill
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${String.format("%.1f", fileTransfer.fileSizeBytes / (1024f * 1024f))} MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        } else if (isVoice) {
                            if (!isConfirmed) {
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
                                            modifier = Modifier.size(36.dp),
                                            color = ElectricCyan,
                                            trackColor = Color.White.copy(alpha = 0.2f),
                                            strokeWidth = 3.dp
                                        )
                                        Text("🎙", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Voice message...", style = MaterialTheme.typography.bodyMedium, color = textColor)
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
                                    if (!isConfirmed) {
                                        CircularProgressIndicator(
                                            progress = progressVal,
                                            modifier = Modifier.size(36.dp),
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
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${String.format("%.1f", fileTransfer.fileSizeBytes / (1024f * 1024f))} MB",
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

                // Status Affordance & Delivery indication
                if (isOutgoing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = if (message.status == MessageStatus.READ) Icons.Default.CheckCircle else Icons.Default.Check
                        val iconTint = if (message.status == MessageStatus.READ) ElectricCyan else TextSecondary.copy(alpha = 0.7f)
                        
                        if (message.status != MessageStatus.PENDING) {
                            Icon(
                                imageVector = icon,
                                contentDescription = message.status.name,
                                tint = iconTint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
