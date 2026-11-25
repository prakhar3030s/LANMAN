package com.prakhar.lanman.ui.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prakhar.lanman.data.engine.RadarService
import com.prakhar.lanman.domain.model.ChatListContact
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.common.ContactAvatar
import com.prakhar.lanman.ui.common.EmptyState
import com.prakhar.lanman.ui.common.GlassDialog
import com.prakhar.lanman.ui.common.GlassDialogText
import com.prakhar.lanman.ui.common.GlassSurface
import com.prakhar.lanman.ui.common.GlassTextField
import com.prakhar.lanman.ui.common.LanmanTopBar
import com.prakhar.lanman.ui.common.QrCodeIcon
import com.prakhar.lanman.ui.common.contentWidth
import com.prakhar.lanman.ui.common.pressable
import com.prakhar.lanman.ui.theme.LanmanTheme
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onNavigateToMyQr: () -> Unit,
    onNavigateToScanQr: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    onNavigateToMessageRequests: () -> Unit,
    onNavigateToNewChat: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val chatList by viewModel.chatList.collectAsStateWithLifecycle()
    val requestCount by viewModel.messageRequestCount.collectAsStateWithLifecycle()
    val mutedIds by viewModel.mutedIds.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val isEngineRunning by viewModel.isEngineRunning.collectAsStateWithLifecycle()

    val colors = LanmanTheme.colors
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<ChatListContact?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatListContact?>(null) }

    val filtered = remember(chatList, query) {
        if (query.isBlank()) chatList
        else chatList.filter { item ->
            item.contact.displayName.contains(query, ignoreCase = true) ||
                previewTextOf(item).contains(query, ignoreCase = true)
        }
    }

    val onlineCount = remember(chatList) { chatList.count { it.isOnline } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LanmanTopBar(
                title = "Messages",
                subtitle = when {
                    !isEngineRunning -> "Local discovery paused"
                    !networkState.isWifiConnected -> "No local Wi-Fi connection"
                    onlineCount > 0 -> "$onlineCount nearby on Wi-Fi"
                    else -> "Listening on local Wi-Fi"
                },
                subtitleColor = when {
                    !networkState.isWifiConnected || !isEngineRunning -> colors.warning
                    onlineCount > 0 -> colors.accent
                    else -> colors.textSecondary
                },
                actions = {
                    IconButton(onClick = onNavigateToMyQr) {
                        QrCodeIcon(
                            contentDescription = "Show my QR code",
                            tint = colors.textPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colors.textPrimary
                        )
                    }
                }
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.contentWidth().fillMaxHeight()) {
                    if (!networkState.isWifiConnected || !isEngineRunning) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .then(
                                    if (!isEngineRunning) {
                                        Modifier.pressable {
                                            runCatching {
                                                ContextCompat.startForegroundService(
                                                    context,
                                                    Intent(context, RadarService::class.java)
                                                )
                                            }
                                        }
                                    } else {
                                        Modifier.pressable { viewModel.refreshDiscovery() }
                                    }
                                ),
                            shape = RoundedCornerShape(14.dp),
                            tint = colors.warning.copy(alpha = 0.12f),
                            borderColor = colors.warning.copy(alpha = 0.35f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = colors.warning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (!isEngineRunning) {
                                        "Local discovery is paused. Tap to resume."
                                    } else {
                                        "Connect to a local Wi-Fi network to send and discover peers."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    GlassTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search conversations",
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    if (requestCount > 0) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .pressable { onNavigateToMessageRequests() },
                            shape = RoundedCornerShape(16.dp),
                            tint = colors.accentTint,
                            borderColor = colors.borderActive
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = "$requestCount Message Request${if (requestCount > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.accent
                                )
                            }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (chatList.isEmpty()) {
                                EmptyState(
                                    icon = AppIcons.Chat,
                                    title = "No conversations yet",
                                    message = "Pair with someone on the same Wi-Fi by scanning their QR code.",
                                    actionLabel = "Scan QR Code",
                                    onAction = onNavigateToScanQr
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Default.Search,
                                    title = "No matches",
                                    message = "No conversation matches \u201C$query\u201D."
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filtered, key = { it.contact.id }) { item ->
                                ChatListRow(
                                    item = item,
                                    isMuted = item.contact.id in mutedIds,
                                    onClick = { onNavigateToConversation(item.contact.id) },
                                    onLongClick = { actionTarget = item },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onNavigateToNewChat,
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New chat")
                }
            }
        }
    }

    actionTarget?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = colors.surfaceElevated
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = target.contact.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                SheetAction(
                    icon = AppIcons.MarkChatRead,
                    label = "Mark as read",
                    onClick = {
                        viewModel.markRead(target.contact.id)
                        actionTarget = null
                    }
                )
                SheetAction(
                    icon = if (target.contact.id in mutedIds) AppIcons.VolumeUp else AppIcons.VolumeOff,
                    label = if (target.contact.id in mutedIds) "Unmute notifications" else "Mute notifications",
                    onClick = {
                        viewModel.toggleMute(target.contact.id)
                        actionTarget = null
                    }
                )
                SheetAction(
                    icon = Icons.Default.Delete,
                    label = "Delete chat",
                    tint = colors.error,
                    onClick = {
                        deleteTarget = target
                        actionTarget = null
                    }
                )
            }
        }
    }

    deleteTarget?.let { target ->
        GlassDialog(
            onDismissRequest = { deleteTarget = null },
            title = "Delete chat?",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteChat(target.contact.id)
                deleteTarget = null
            }
        ) {
            GlassDialogText(
                "Every message with ${target.contact.displayName} will be removed from this device. " +
                    "The contact stays paired."
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = LanmanTheme.colors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
fun ChatListRow(
    item: ChatListContact,
    isMuted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LanmanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            displayName = item.contact.displayName,
            size = 52.dp,
            isOnline = item.isOnline,
            modifier = Modifier.semantics {
                contentDescription = if (item.isOnline) "Online" else "Offline"
            }
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.contact.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = previewTextOf(item),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Top) {
            Text(
                text = formatTimestamp(item.lastMessageTimestamp ?: item.contact.addedAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isMuted) {
                    Icon(
                        imageVector = AppIcons.VolumeOff,
                        contentDescription = "Muted",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (item.unreadCount > 0) {
                    if (isMuted) Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(22.dp)
                            .widthIn(min = 22.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                            .padding(horizontal = 6.dp)
                            .semantics {
                                contentDescription = "${item.unreadCount} unread messages"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onAccent
                        )
                    }
                }
            }
        }
    }
}

private fun previewTextOf(item: ChatListContact): String {
    return item.lastMessageContent?.let { bytes ->
        val raw = String(bytes, StandardCharsets.UTF_8)
        when {
            raw.startsWith("voice:") -> "Voice message"
            raw.startsWith("File: ") -> "File \u00B7 ${raw.removePrefix("File: ")}"
            else -> raw
        }
    } ?: "Tap to start messaging"
}

private fun formatTimestamp(timestamp: Long): String {
    val zone = ZoneId.systemDefault()
    val dateTime = Instant.ofEpochMilli(timestamp).atZone(zone)
    val today = LocalDate.now(zone)
    return if (dateTime.toLocalDate() == today) {
        dateTime.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    } else {
        dateTime.format(DateTimeFormatter.ofPattern("MMM dd", Locale.getDefault()))
    }
}
