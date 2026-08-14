package com.example.lanman.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.domain.model.ChatListContact
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatListScreen(
    onNavigateToQr: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToConversation: (String) -> Unit,
    onNavigateToMessageRequests: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
    hazeState: HazeState
) {
    val chatList by viewModel.chatList.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .haze(hazeState)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Row {
                    IconButton(onClick = onNavigateToQr) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Scan QR",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            val requestCount by viewModel.messageRequestCount.collectAsStateWithLifecycle()
            if (requestCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .clickable { onNavigateToMessageRequests() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Requests",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$requestCount Message Request${if (requestCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Chat List
            if (chatList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No contacts yet.\nTap the QR icon to connect.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(chatList, key = { it.contact.id }) { item ->
                        ChatListRow(
                            item = item,
                            onClick = { onNavigateToConversation(item.contact.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListRow(
    item: ChatListContact,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Presence Dot
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (item.isOnline) ElectricCyan else Color.DarkGray)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Name & Message
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.contact.displayName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            val previewText = item.lastMessageContent?.let { String(it, StandardCharsets.UTF_8) } ?: "Say hello"
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Time & Unread
        Column(horizontalAlignment = Alignment.End) {
            val timestamp = item.lastMessageTimestamp ?: item.contact.addedAt
            Text(
                text = formatTimestamp(timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            
            if (item.unreadCount > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(ElectricCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    
    val format = if (date.date == now.date && date.month == now.month && date.year == now.year) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("MMM dd", Locale.getDefault())
    }
    
    return format.format(date)
}
