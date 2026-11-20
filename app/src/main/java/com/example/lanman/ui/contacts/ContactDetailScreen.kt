package com.example.lanman.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.ui.common.ContactAvatar
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactDetailScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val presence = contacts.find { it.contact.id == contactId }
    val stats by remember(contactId) { viewModel.stats(contactId) }
        .collectAsStateWithLifecycle(initialValue = 0 to 0)

    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        GlassDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = "Remove contact?",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                showRemoveDialog = false
                viewModel.removeContact(contactId)
                onNavigateBack()
            }
        ) {
            GlassDialogText(
                "${presence?.contact?.displayName ?: "This contact"} will be unpaired, its message " +
                    "history deleted and active connections closed."
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LanmanTopBar(
            title = "Contact Info",
            onBack = onNavigateBack
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                ContactAvatar(
                    displayName = presence?.contact?.displayName ?: "?",
                    size = 104.dp,
                    isOnline = presence?.isOnline == true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = presence?.contact?.displayName ?: "Contact",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (presence?.isOnline == true) "Online" else "Offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (presence?.isOnline == true) ElectricCyan else TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
                        val timeFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                        val contact = presence?.contact

                        MetaRow(
                            label = "Paired since",
                            value = contact?.let { dateFormat.format(Date(it.addedAt)) } ?: "\u2014"
                        )
                        MetaDivider()
                        MetaRow(
                            label = "Last seen",
                            value = contact?.lastSeenAt?.let { timeFormat.format(Date(it)) } ?: "Not seen yet"
                        )
                        MetaDivider()
                        MetaRow(
                            label = "Connected via",
                            value = contact?.lastKnownIp ?: "No address yet"
                        )
                        MetaDivider()
                        MetaRow(
                            label = "Messages",
                            value = stats.first.toString()
                        )
                        MetaDivider()
                        MetaRow(
                            label = "Files & voice notes",
                            value = stats.second.toString()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                GlassButton(
                    text = "Remove Contact",
                    onClick = { showRemoveDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun MetaDivider() {
    Divider(color = Color.White.copy(alpha = 0.08f))
}
