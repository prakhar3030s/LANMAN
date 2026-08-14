package com.example.lanman.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.Black
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val presence = contacts.find { it.contact.id == contactId }
    val hazeState = remember { HazeState() }
    
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Contact") },
            text = { Text("Are you sure you want to remove ${presence?.contact?.displayName}? This will permanently delete all message history and close active connections.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        viewModel.removeContact(contactId)
                        onNavigateBack()
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().haze(hazeState),
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("Contact Info", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        if (presence != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = presence.contact.displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = presence.contact.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (presence.isOnline) ElectricCyan else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (presence.isOnline) "Online" else "Offline",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (presence.isOnline) ElectricCyan else TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                GlassSurface(

                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Paired Since", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        Text(
                            text = dateFormat.format(Date(presence.contact.addedAt)),
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = { showRemoveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Contact", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}
