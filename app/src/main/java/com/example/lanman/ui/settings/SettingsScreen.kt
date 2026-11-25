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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.example.lanman.data.notification.AppThemeMode
import com.example.lanman.ui.common.AppIcons
import com.example.lanman.ui.common.GlassDialog
import com.example.lanman.ui.common.GlassDialogText
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.common.GlassTextField
import com.example.lanman.ui.common.LanmanTopBar
import com.example.lanman.ui.common.contentWidth
import com.example.lanman.ui.common.pressable
import com.example.lanman.ui.theme.LanmanTheme
import java.util.Locale

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
    val readReceiptsEnabled by viewModel.readReceiptsEnabled.collectAsStateWithLifecycle()
    val discoveryEnabled by viewModel.discoveryEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val blockedCount by viewModel.blockedCount.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    val colors = LanmanTheme.colors
    val context = LocalContext.current

    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showTroubleshootingDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showClearFilesDialog by remember { mutableStateOf(false) }
    var wipeConfirmation by remember { mutableStateOf("") }

    fun notificationPermissionGranted(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
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
            confirmEnabled = editNameText.isNotBlank() && editNameText.trim().length <= 30,
            onConfirm = {
                viewModel.updateDisplayName(editNameText)
                showEditNameDialog = false
            }
        ) {
            GlassDialogText("This is the name shown on your QR code and initial pairing handshakes.")
            Spacer(modifier = Modifier.height(12.dp))
            GlassTextField(
                value = editNameText,
                onValueChange = { editNameText = it },
                placeholder = "Your name"
            )
        }
    }

    if (showThemeDialog) {
        GlassDialog(
            onDismissRequest = { showThemeDialog = false },
            title = "Appearance",
            confirmLabel = "Done",
            dismissLabel = null,
            onConfirm = { showThemeDialog = false }
        ) {
            AppThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    AppThemeMode.DARK -> "Dark (Translucent Monolith)"
                    AppThemeMode.LIGHT -> "Light"
                    AppThemeMode.SYSTEM -> "Follow system"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable {
                            viewModel.setThemeMode(mode)
                            showThemeDialog = false
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == mode,
                        onClick = {
                            viewModel.setThemeMode(mode)
                            showThemeDialog = false
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                }
            }
        }
    }

    if (showTroubleshootingDialog) {
        GlassDialog(
            onDismissRequest = { showTroubleshootingDialog = false },
            title = "Local Network Diagnostics",
            confirmLabel = "Refresh",
            dismissLabel = "Close",
            onConfirm = { viewModel.refreshDiagnostics() }
        ) {
            GlassDialogText(
                "LANMAN discovers peers using UDP broadcast (port ${viewModel.discoveryPort}) and mDNS, " +
                    "then connects directly over TCP (ports ${viewModel.controlPort} & ${viewModel.dataPort}).\n\n" +
                    "If nearby paired devices appear offline:\n" +
                    "1. Confirm both devices are connected to the exact same Wi-Fi SSID/subnet.\n" +
                    "2. Disable any active VPN on either phone (VPNs hijack local routes).\n" +
                    "3. Guest/public Wi-Fi routers often enable AP/Client Isolation, which blocks device-to-device packets.\n" +
                    "4. Check that battery optimization isn't killing LANMAN in the background."
            )
        }
    }

    if (showPrivacyDialog) {
        GlassDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = "Privacy & Security Model",
            confirmLabel = "Got it",
            dismissLabel = null,
            onConfirm = { showPrivacyDialog = false }
        ) {
            GlassDialogText(
                "Zero Cloud Footprint:\n" +
                    "\u2022 LANMAN connects to no servers, cloud databases, or analytics.\n" +
                    "\u2022 Android Cloud Backup is disabled so your messages and keys are never uploaded.\n" +
                    "\u2022 Messages are end-to-end encrypted with the Signal Protocol (PQXDH + Double Ratchet) and files with AES-256-GCM.\n" +
                    "\u2022 Your private keys and file transfer keys are protected in Android Keystore-backed EncryptedSharedPreferences.\n" +
                    "\u2022 Only your device ID (UUID) is announced over local Wi-Fi so paired peers can locate your IP address."
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
            .background(colors.background)
    ) {
        LanmanTopBar(
            title = "Settings",
            actions = {
                IconButton(onClick = { viewModel.refreshDiagnostics() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh diagnostics",
                        tint = colors.textPrimary
                    )
                }
            }
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .contentWidth()
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
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
                        subtitle = "Show your code & verification key to pair",
                        onClick = onNavigateToMyQr
                    )
                }

                SettingsSectionTitle("Appearance")
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Theme",
                        subtitle = when (themeMode) {
                            AppThemeMode.DARK -> "Dark"
                            AppThemeMode.LIGHT -> "Light"
                            AppThemeMode.SYSTEM -> "System default"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }

                SettingsSectionTitle("Privacy")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = AppIcons.DoneAll,
                        title = "Read receipts",
                        subtitle = "Let paired contacts know when you've read messages",
                        checked = readReceiptsEnabled,
                        onCheckedChange = viewModel::setReadReceiptsEnabled
                    )
                    SettingsDivider()
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
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Privacy & security model",
                        subtitle = "What stays on your device vs. local network",
                        onClick = { showPrivacyDialog = true }
                    )
                }

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
                        subtitle = "Alert when an unpaired peer writes to you",
                        checked = requestsEnabled,
                        enabled = notificationsEnabled,
                        onCheckedChange = viewModel::setRequestsEnabled
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "System notification settings",
                        subtitle = "Channels, lockscreen preview, sound",
                        onClick = { openSystemNotificationSettings() }
                    )
                }

                SettingsSectionTitle("Network")
                SettingsGroup {
                    SettingsSwitchItem(
                        icon = AppIcons.Wifi,
                        title = "Background local discovery",
                        subtitle = if (discoveryEnabled) "Listening on Wi-Fi in background" else "Paused (battery saver)",
                        checked = discoveryEnabled,
                        onCheckedChange = viewModel::setDiscoveryEnabled
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = AppIcons.Wifi,
                        title = "This device",
                        subtitle = diagnostics.addresses.joinToString(", ")
                            .ifBlank { if (diagnostics.isWifiConnected) "Connected (no IPv4)" else "No Wi-Fi connection" }
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = AppIcons.Wifi,
                        title = "Local ports",
                        subtitle = "UDP ${viewModel.discoveryPort} (discovery) \u00B7 TCP ${viewModel.controlPort}/${viewModel.dataPort}"
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = Icons.Default.Person,
                        title = "Paired devices",
                        subtitle = "${diagnostics.onlineCount} online of ${diagnostics.contactCount} paired"
                    )
                    SettingsDivider()
                    InfoRow(
                        icon = Icons.Default.Info,
                        title = "Messaging engine",
                        subtitle = when {
                            diagnostics.lastError != null -> diagnostics.lastError!!
                            diagnostics.serviceRunning -> "Active"
                            else -> "Paused"
                        }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Troubleshoot discovery",
                        subtitle = "AP isolation, VPN, and router tips",
                        onClick = { showTroubleshootingDialog = true }
                    )
                }

                SettingsSectionTitle("Data")
                SettingsGroup {
                    SettingsItem(
                        icon = Icons.Default.Delete,
                        title = "Received files",
                        subtitle = if (receivedFilesSize > 0L) {
                            "${formatSize(receivedFilesSize)} \u00B7 Tap to clear"
                        } else {
                            "Nothing downloaded yet"
                        },
                        titleColor = if (receivedFilesSize > 0L) colors.error else colors.textPrimary,
                        onClick = { if (receivedFilesSize > 0L) showClearFilesDialog = true }
                    )
                    SettingsDivider()
                    SettingsItem(
                        icon = Icons.Default.Warning,
                        title = "Clear local data",
                        subtitle = "Wipe keys, contacts, and history",
                        titleColor = colors.error,
                        onClick = { showClearDataDialog = true }
                    )
                }

                SettingsSectionTitle("About")
                SettingsGroup {
                    InfoRow(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = viewModel.appVersionLabel
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Zero Cloud Footprint. Your data never leaves your local network.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
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
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = LanmanTheme.colors.accent,
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
    HorizontalDivider(color = LanmanTheme.colors.border)
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    titleColor: Color = LanmanTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    val colors = LanmanTheme.colors
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
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
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
    val colors = LanmanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) colors.textPrimary else colors.textTertiary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) colors.textPrimary else colors.textSecondary
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceCard,
                uncheckedBorderColor = colors.border
            )
        )
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val colors = LanmanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}
