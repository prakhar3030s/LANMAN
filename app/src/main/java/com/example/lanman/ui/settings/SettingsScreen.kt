package com.example.lanman.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onNavigateToMyQr: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToBlockedContacts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val receivedFilesSize by viewModel.receivedFilesSize.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val messagesEnabled by viewModel.messagesEnabled.collectAsStateWithLifecycle()
    val requestsEnabled by viewModel.requestsEnabled.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showClearFilesDialog by remember { mutableStateOf(false) }
    var wipeConfirmation by remember { mutableStateOf("") }

    fun notificationPermissionGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    var hasNotificationPermission by remember { mutableStateOf(notificationPermissionGranted()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    fun openSystemNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshReceivedFilesSize()
        viewModel.refreshDiagnostics()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = notificationPermissionGranted()
                viewModel.refreshReceivedFilesSize()
                viewModel.refreshDiagnostics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showEditNameDialog) {
        GlassDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = "Edit display name",
            confirmLabel = "Save",
            confirmEnabled = editNameText.isNotBlank(),
            onConfirm = {
                viewModel.updateDisplayName(editNameText)
                showEditNameDialog = false
            }
        ) {
            GlassDialogText("This is the name your contacts see when they pair with you.")
            Spacer(modifier = Modifier.height(12.dp))
            GlassTextField(
                value = editNameText,
                onValueChange = { editNameText = it },
                placeholder = "Your name"
            )
        }
    }

    if (showClearFilesDialog) {
        GlassDialog(
            onDismissRequest = { showClearFilesDialog = false },
            title = "Clear received files",
            confirmLabel = "Delete files",
            destructive = true,
            onConfirm = {
                showClearFilesDialog = false
                viewModel.clearReceivedFiles()
            }
        ) {
            GlassDialogText(
                "This deletes every file and voice message you have received. Messages stay in your " +
                    "chats but their attachments can no longer be opened."
            )
        }
    }

    if (showClearDataDialog) {
        GlassDialog(
            onDismissRequest = {
                showClearDataDialog = false
                wipeConfirmation = ""
            },
            title = "Clear local data",
            confirmLabel = "Wipe data",
            destructive = true,
            confirmEnabled = wipeConfirmation.equals("DELETE", ignoreCase = true),
            onConfirm = {
                showClearDataDialog = false
                wipeConfirmation = ""
                viewModel.clearLocalData { onNavigateToOnboarding() }
            }
        ) {
            GlassDialogText(
                "This permanently wipes your keys, profile and message history. The app returns to its " +
                    "first-launch state and this cannot be undone.\n\nType DELETE to confirm."
            )
            Spacer(modifier = Modifier.height(12.dp))
            GlassTextField(
                value = wipeConfirmation,
                onValueChange = { wipeConfirmation = it },
                placeholder = "DELETE"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LanmanTopBar(title = "Settings")

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                // Profile
                SettingsSectionTitle("Profile")
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Display name",
                        subtitle = profile?.displayName ?: "Loading...",
                        onClick = {
                            editNameText = profile?.displayName ?: ""
                            showEditNameDialog = true
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = "My QR code",
                        subtitle = "Show your code to connect",
                        onClick = onNavigateToMyQr
                    )
                }

                // Notifications
                SettingsSectionTitle("Notifications")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = if (hasNotificationPermission) {
                            "Allow LANMAN to alert you"
                        } else {
                            "Blocked by Android \u2014 tap to allow"
                        },
                        checked = notificationsEnabled && hasNotificationPermission,
                        onCheckedChange = { enabled ->
                            if (!hasNotificationPermission) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (enabled) viewModel.setNotificationsEnabled(true)
                            } else {
                                viewModel.setNotificationsEnabled(enabled)
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "New messages",
                        subtitle = "Alert for incoming messages",
                        checked = messagesEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = viewModel::setMessagesEnabled
                    )
                    SettingsDivider()
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "Message requests",
                        subtitle = "Alert when a stranger writes to you",
                        checked = requestsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = viewModel::setRequestsEnabled
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "System settings",
                        subtitle = "Channels, sound and vibration",
                        onClick = { openSystemNotificationSettings() }
                    )
                }

                // Privacy
                SettingsSectionTitle("Privacy")
                SettingsGroup {
                    SettingsItem(
                        icon = AppIcons.Block,
                        title = "Blocked contacts",
                        subtitle = when {
                            blockedCount == 0 -> "Nobody is blocked"
                            blockedCount == 1 -> "1 contact blocked"
                            else -> "$blockedCount contacts blocked"
                        },
                        onClick = onNavigateToBlockedContacts
                    )
                }

                // Network diagnostics
                SettingsSectionTitle("Network")
                SettingsGroup {
                    InfoRow(
                        icon = AppIcons.Wifi,
                        title = "This device",
                        subtitle = diagnostics.addresses.joinToString(", ")
                            .ifBlank { "No network address" }
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = AppIcons.Wifi,
                        title = "Discovery port",
                        subtitle = "UDP ${viewModel.discoveryPort}"
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = Icons.Default.Person,
                        title = "Devices",
                        subtitle = "${diagnostics.onlineCount} online of ${diagnostics.contactCount} paired"
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = Icons.Default.Info,
                        title = "Radar service",
                        subtitle = if (diagnostics.serviceRunning) "Running" else "Stopped"
                    )
                }

                // Data
                SettingsSectionTitle("Data")
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Default.Delete,
                        title = "Received files",
                        subtitle = if (receivedFilesSize > 0L) "${formatSize(receivedFilesSize)} \u00B7 Tap to clear" else "Nothing downloaded yet",
                        titleColor = if (receivedFilesSize > 0L) MaterialTheme.colorScheme.error else TextPrimary,
                        onClick = { if (receivedFilesSize > 0L) showClearFilesDialog = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Warning,
                        title = "Clear local data",
                        subtitle = "Wipe keys and history",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showClearDataDialog = true }
                    )
                }

                // About
                SettingsSectionTitle("About")
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = "1.0.0 (LANMAN Build)",
                        onClick = {}
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Zero Cloud Footprint. Your data never leaves your device.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = ElectricCyan,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDivider() {
    Divider(color = Color.White.copy(alpha = 0.08f))
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = titleColor)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) TextPrimary else TextSecondary
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = ElectricCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.20f)
            )
        )
    }
}

/** Read-only value row for diagnostics. */
@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
