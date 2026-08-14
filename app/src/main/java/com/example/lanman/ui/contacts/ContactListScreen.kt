package com.example.lanman.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.Black
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onNavigateToConversation: (String) -> Unit,
    onNavigateToContactDetail: (String) -> Unit,
    onNavigateToMyQr: () -> Unit,
    onNavigateToAddContact: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel(),
    hazeState: HazeState
) {
    val contacts by viewModel.contacts.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize().haze(hazeState),
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("Contacts", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary
                ),
                actions = {
                    IconButton(onClick = onNavigateToMyQr) {
                        Icon(imageVector = Icons.Default.Person, contentDescription = "My QR Code", tint = TextPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAddContact,
                containerColor = ElectricCyan,
                contentColor = Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (contacts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No contacts paired yet.\nTap + to add someone.",
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                items(contacts) { presence ->
                    ContactCard(
                        presence = presence,

                        onClick = { onNavigateToConversation(presence.contact.id) },
                        onViewDetails = { onNavigateToContactDetail(presence.contact.id) },
                        onRemove = { viewModel.removeContact(presence.contact.id) }
                    )
                }
            }
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
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Contact") },
            text = { Text("Are you sure you want to remove ${presence.contact.displayName}? This will delete all message history and close any active connection.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        onRemove()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = Black.copy(alpha = 0.5f)
        )
    }

    GlassSurface(

        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Presence indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (presence.isOnline) ElectricCyan else Color.Gray)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = presence.contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onClick) {
                Icon(Icons.Default.Email, contentDescription = "Message", tint = ElectricCyan)
            }
            
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
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
                        onClick = {
                            showMenu = false
                            showRemoveDialog = true
                        }
                    )
                }
            }
        }
    }
}
