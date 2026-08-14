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
                    val file = File(uiModel.fileTransfer.localUri)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, uiModel.fileTransfer.mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open File"))
                    }
                },
                onLongPress = {
                    val file = File(uiModel.fileTransfer.localUri)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = uiModel.fileTransfer.mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share File"))
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
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp)
            ) {
                // Content
                if (message.type == MessageType.FILE) {
                    val fileTransfer = uiModel.fileTransfer
                    if (fileTransfer != null) {
                        val isImageOrVideo = fileTransfer.mimeType.startsWith("image/") || fileTransfer.mimeType.startsWith("video/")
                        
                        Box(contentAlignment = Alignment.Center) {
                            if (isImageOrVideo) {
                                val context = androidx.compose.ui.platform.LocalContext.current
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
                                        if (fileTransfer.mimeType.startsWith("video/")) {
                                            decoderFactory(coil.decode.VideoFrameDecoder.Factory())
                                        }
                                    }
                                    .crossfade(true)
                                    .build()

                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = fileTransfer.fileName,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info, 
                                        contentDescription = "File", 
                                        tint = textColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = fileTransfer.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${String.format("%.1f", fileTransfer.fileSizeBytes / (1024f * 1024f))} MB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            
                            // Progress Overlay
                            if (uiModel.progress != null && !isConfirmed) {
                                Box(
                                    modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        progress = { uiModel.progress },
                                        color = ElectricCyan,
                                        trackColor = Color.White.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Loading file info...", color = textColor)
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
