package com.example.lanman.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
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
import com.example.lanman.ui.common.GlassSurface
import com.example.lanman.ui.theme.ElectricCyan
import com.example.lanman.ui.theme.Black
import com.example.lanman.ui.theme.TextPrimary
import com.example.lanman.ui.theme.TextSecondary
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToMyQr: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    hazeState: HazeState
) {
    val profile by viewModel.profile.collectAsState()
    val context = LocalContext.current
    
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameText by remember { mutableStateOf("") }
    
    var showClearDataDialog by remember { mutableStateOf(false) }
    
    // Check notification permission
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name") },
            text = {
                OutlinedTextField(
                    value = editNameText,
                    onValueChange = { editNameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricCyan,
                        cursorColor = ElectricCyan
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateDisplayName(editNameText)
                    showEditNameDialog = false
                }) {
                    Text("Save", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Local Data") },
            text = { Text("This will permanently wipe your keys, profile, and all message history. The app will return to its first-launch state. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDataDialog = false
                    viewModel.clearLocalData {
                        onNavigateToOnboarding()
                    }
                }) {
                    Text("Wipe Data", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
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
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Section
            SettingsSectionTitle("Profile")
            GlassSurface(

                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Person,
                        title = "Display Name",
                        subtitle = profile?.displayName ?: "Loading...",
                        onClick = {
                            editNameText = profile?.displayName ?: ""
                            showEditNameDialog = true
                        }
                    )
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    SettingsItem(
                        icon = Icons.Default.Share,
                        title = "My QR Code",
                        subtitle = "Show your QR code to connect",
                        onClick = onNavigateToMyQr
                    )
                }
            }

            // App Section
            SettingsSectionTitle("App")
            GlassSurface(

                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = "Notifications",
                        subtitle = if (hasNotificationPermission) "Enabled" else "Tap to enable in System Settings",
                        onClick = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    )
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "Version",
                        subtitle = "1.0.0 (LocalLink Build)",
                        onClick = {}
                    )
                }
            }

            // Data Section
            SettingsSectionTitle("Data")
            GlassSurface(

                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SettingsItem(
                    icon = Icons.Default.Warning,
                    title = "Clear local data",
                    subtitle = "Wipe keys and history",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showClearDataDialog = true }
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

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = ElectricCyan,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp)
    )
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
            .clickable(onClick = onClick)
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
