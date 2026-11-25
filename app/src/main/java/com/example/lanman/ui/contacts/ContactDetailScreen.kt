package com.example.lanman.ui.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.domain.security.PeerAuth
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.ContactAvatar
import com.example.lanman.ui.common.GlassButton
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.LanmanTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ContactDetailScreen(
    contactId: String,
    onNavigateBack: () -> Unit,
    onNavigateToConversation: (String) -> Unit = {},
    onContactRemoved: () -> Unit = onNavigateBack,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val myIdentityKey by viewModel.myIdentityKeyBase64.collectAsStateWithLifecycle()
    val mutedIds by viewModel.mutedIds.collectAsStateWithLifecycle()
    val blockedIds by viewModel.blockedIds.collectAsStateWithLifecycle()

    val presence = contacts.find { it.contact.id == contactId }
    val stats by remember(contactId) { viewModel.stats(contactId) }
        .collectAsStateWithLifecycle(initialValue = 0 to 0)

    val colors = LanmanTheme.colors
    val context = LocalContext.current
    var showRemoveDialog by remember { mutableStateOf(false) }

    val peerIdentityKey = remember(presence?.contact?.publicKeyBundle) {
        PeerAuth.extractIdentityKeyBase64(presence?.contact?.publicKeyBundle)
    }
    val safetyCode = remember(myIdentityKey, peerIdentityKey) {
        PeerAuth.pairwiseSafetyCode(myIdentityKey, peerIdentityKey)
            ?: PeerAuth.keyFingerprint(peerIdentityKey)
    }

    if (showRemoveDialog) {
        GlassDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = "Remove contact?",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                showRemoveDialog = false
                viewModel.removeContact(contactId)
                onContactRemoved()
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
            .background(colors.background)
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
                    size = 96.dp,
                    isOnline = presence?.isOnline == true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = presence?.contact?.displayName ?: "Contact",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (presence?.isOnline == true) "Online on local Wi-Fi" else "Offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (presence?.isOnline == true) colors.accent else colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassButton(
                        text = "Message",
                        onClick = { onNavigateToConversation(contactId) },
                        isPrimary = true,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(
                        text = if (contactId in mutedIds) "Unmute" else "Mute",
                        onClick = { viewModel.toggleMute(contactId) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!safetyCode.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    GlassSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Safety Number", safetyCode))
                                Toast.makeText(context, "Safety number copied", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Safety Number",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = AppIcons.ContentCopy,
                                    contentDescription = "Copy safety number",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = safetyCode,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Compare this code with ${presence?.contact?.displayName ?: "your contact"} in person to confirm your chat is end-to-end encrypted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                            label = "Local address",
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

                Spacer(modifier = Modifier.height(20.dp))

                GlassButton(
                    text = if (contactId in blockedIds) "Unblock Contact" else "Block Contact",
                    onClick = { viewModel.toggleBlock(contactId) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                GlassButton(
                    text = "Remove Contact",
                    onClick = { showRemoveDialog = true },
                    destructive = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val colors = LanmanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun MetaDivider() {
    HorizontalDivider(color = LanmanTheme.colors.border)
}
