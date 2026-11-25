package com.prakhar.lanman.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prakhar.lanman.domain.model.ContactPresence
import com.prakhar.lanman.domain.network.LanNetworkState
import com.prakhar.lanman.ui.common.AppIcons
import com.prakhar.lanman.ui.common.ContactAvatar
import com.prakhar.lanman.ui.common.EmptyState
import com.prakhar.lanman.ui.common.GlassDialog
import com.prakhar.lanman.ui.common.GlassDialogText
import com.prakhar.lanman.ui.common.GlassSurface
import com.prakhar.lanman.ui.common.GlassTextField
import com.prakhar.lanman.ui.common.LanmanTopBar
import com.prakhar.lanman.ui.common.PresenceDot
import com.prakhar.lanman.ui.common.QrCodeIcon
import com.prakhar.lanman.ui.common.contentWidth
import com.prakhar.lanman.ui.common.pressable
import com.prakhar.lanman.ui.theme.LanmanTheme

@Composable
fun ContactListScreen(
    onNavigateToConversation: (String) -> Unit,
    onNavigateToContactDetail: (String) -> Unit,
    onNavigateToMyQr: () -> Unit,
    onNavigateToAddContact: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val colors = LanmanTheme.colors

    var query by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<ContactPresence?>(null) }

    val filtered = remember(contacts, query) {
        if (query.isBlank()) contacts
        else contacts.filter { it.contact.displayName.contains(query, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LanmanTopBar(
                title = "Contacts",
                subtitle = "${contacts.count { it.isOnline }} online of ${contacts.size} paired",
                actions = {
                    IconButton(onClick = { viewModel.refreshDiscovery() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh local discovery",
                            tint = colors.textPrimary
                        )
                    }
                    IconButton(onClick = onNavigateToMyQr) {
                        QrCodeIcon(contentDescription = "Show my QR code", tint = colors.textPrimary)
                    }
                }
            )

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(modifier = Modifier.contentWidth().fillMaxHeight()) {
                    DiscoveryBanner(
                        networkState = networkState,
                        onlineCount = contacts.count { it.isOnline },
                        totalContacts = contacts.size,
                        onRefresh = { viewModel.refreshDiscovery() }
                    )

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
                                    title = "No paired contacts yet",
                                    message = "Scan a peer's QR code when on the same Wi-Fi network to start messaging securely.",
                                    actionLabel = "Scan QR Code",
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
                                    onRemove = { removeTarget = presence },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onNavigateToAddContact,
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Scan QR to Add Contact")
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
private fun DiscoveryBanner(
    networkState: LanNetworkState,
    onlineCount: Int,
    totalContacts: Int,
    onRefresh: () -> Unit
) {
    val colors = LanmanTheme.colors
    val statusText = when {
        !networkState.isWifiConnected -> "Wi-Fi disconnected · Connect to a local network"
        !networkState.isDiscoveryActive -> "Local discovery paused"
        networkState.lastError != null -> networkState.lastError
        totalContacts > 0 && onlineCount == 0 -> "Scanning LAN (${networkState.localAddresses.firstOrNull() ?: "Wi-Fi"}) · Ensure peers share Wi-Fi without client isolation"
        else -> "Scanning LAN · ${networkState.localAddresses.firstOrNull() ?: "Wi-Fi active"}"
    }

    val isWarning = !networkState.isWifiConnected || !networkState.isDiscoveryActive || networkState.lastError != null

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pressable(onClick = onRefresh),
        shape = RoundedCornerShape(12.dp),
        tint = if (isWarning) colors.warning.copy(alpha = 0.10f) else colors.surfaceGlass,
        borderColor = if (isWarning) colors.warning.copy(alpha = 0.3f) else colors.border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PresenceDot(
                isOnline = networkState.isWifiConnected && networkState.isDiscoveryActive,
                size = 8.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isWarning) colors.textPrimary else colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ContactCard(
    presence: ContactPresence,
    onClick: () -> Unit,
    onViewDetails: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LanmanTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = modifier
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
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (presence.isOnline) {
                        "Online · ${presence.lastKnownIp ?: "LAN"}"
                    } else {
                        "Offline"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (presence.isOnline) colors.accent else colors.textSecondary
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = AppIcons.Chat,
                    contentDescription = "Message",
                    tint = colors.accent
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.textSecondary)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(colors.surfaceElevated)
                ) {
                    DropdownMenuItem(
                        text = { Text("View details & safety code", color = colors.textPrimary) },
                        onClick = {
                            showMenu = false
                            onViewDetails()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove contact", color = colors.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = colors.error
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
