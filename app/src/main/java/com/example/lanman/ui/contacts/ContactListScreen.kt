package com.example.lanman.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.ContactAvatar
import com.example.lanman.ui.common.EmptyState
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.QrCodeIcon
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary

@Composable
fun ContactListScreen(
    onNavigateToConversation: (String) -> Unit,
    onNavigateToContactDetail: (String) -> Unit,
    onNavigateToMyQr: () -> Unit,
    onNavigateToAddContact: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<ContactPresence?>(null) }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.contact.displayName.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LanmanTopBar(
                title = "Contacts",
                actions = {
                    IconButton(onClick = onNavigateToMyQr) {
                        QrCodeIcon(contentDescription = "Show my QR code", tint = TextPrimary)
                    }
                }
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.contentWidth().fillMaxHeight()) {
                    GlassTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search contacts",
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (contacts.isEmpty()) {
                                EmptyState(
                                    icon = AppIcons.PersonSearch,
                                    title = "No contacts yet",
                                    message = "Pair with someone nearby and they will show up here.",
                                    actionLabel = "Add contact",
                                    onAction = onNavigateToAddContact
                                )
                            } else {
                                EmptyState(
                                    icon = Icons.Default.Search,
                                    title = "No matches",
                                    message = "No contact matches \u201C$query\u201D."
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered, key = { it.contact.id }) { presence ->
                                ContactCard(
                                    presence = presence,
                                    onClick = { onNavigateToConversation(presence.contact.id) },
                                    onViewDetails = { onNavigateToContactDetail(presence.contact.id) },
                                    onRemove = { removeTarget = presence }
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onNavigateToAddContact,
                    containerColor = ElectricCyan,
                    contentColor = Color.Black,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                }
            }
        }
    }

    removeTarget?.let { target ->
        GlassDialog(
            onDismissRequest = { removeTarget = null },
            title = "Remove contact?",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.removeContact(target.contact.id)
                removeTarget = null
            }
        ) {
            GlassDialogText(
                "${target.contact.displayName} will be unpaired, its message history deleted and any " +
                    "open connection closed."
            )
        }
    }
}

@Composable
fun ContactCard(
    presence: ContactPresence,
    onClick: () -> Unit,
    onViewDetails: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, onLongClick = { showMenu = true }),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(
                displayName = presence.contact.displayName,
                size = 46.dp,
                isOnline = presence.isOnline
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = presence.contact.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (presence.isOnline) "Online now" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (presence.isOnline) ElectricCyan else TextSecondary
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = AppIcons.Chat,
                    contentDescription = "Message",
                    tint = ElectricCyan
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    DropdownMenuItem(
                        text = { Text("View details", color = TextPrimary) },
                        onClick = {
                            showMenu = false
                            onViewDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove contact", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}
