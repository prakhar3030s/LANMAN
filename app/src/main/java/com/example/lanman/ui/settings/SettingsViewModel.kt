package com.example.lanman.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.data.engine.RadarService
import com.example.lanman.data.notification.AppThemeMode
import com.example.lanman.data.notification.NotificationPreferences
import com.example.lanman.data.security.BlockedContactsStore
import com.example.lanman.domain.engine.MessagingEngine
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.model.Profile
import com.example.lanman.domain.network.LanNetworkState
import com.example.lanman.domain.network.NetworkDiscoveryManager
import com.example.lanman.domain.network.PresenceTracker
import com.example.lanman.domain.repository.ProfileRepository
import com.example.lanman.domain.usecase.ClearLocalDataUseCase
import com.example.lanman.util.network.NetworkConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class NetworkDiagnostics(
    val addresses: List<String> = emptyList(),
    val contactCount: Int = 0,
    val onlineCount: Int = 0,
    val serviceRunning: Boolean = false,
    val isWifiConnected: Boolean = false,
    val lastError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val clearLocalDataUseCase: ClearLocalDataUseCase,
    private val notificationPreferences: NotificationPreferences,
    private val blockedContactsStore: BlockedContactsStore,
    private val presenceTracker: PresenceTracker,
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val messagingEngine: MessagingEngine
) : ViewModel() {

    private val _receivedFilesSize = MutableStateFlow(0L)
    val receivedFilesSize: StateFlow<Long> = _receivedFilesSize.asStateFlow()

    private fun receivedFilesDir() = File(context.filesDir, "received_files")
    private fun thumbnailsDir() = File(context.filesDir, "thumbnails")

    val profile: StateFlow<Profile?> = profileRepository.getProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _notificationsEnabled = MutableStateFlow(notificationPreferences.enabled)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _messagesEnabled = MutableStateFlow(notificationPreferences.messagesEnabled)
    val messagesEnabled: StateFlow<Boolean> = _messagesEnabled.asStateFlow()

    private val _requestsEnabled = MutableStateFlow(notificationPreferences.requestsEnabled)
    val requestsEnabled: StateFlow<Boolean> = _requestsEnabled.asStateFlow()

    private val _readReceiptsEnabled = MutableStateFlow(notificationPreferences.readReceiptsEnabled)
    val readReceiptsEnabled: StateFlow<Boolean> = _readReceiptsEnabled.asStateFlow()

    private val _discoveryEnabled = MutableStateFlow(notificationPreferences.discoveryEnabled)
    val discoveryEnabled: StateFlow<Boolean> = _discoveryEnabled.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = notificationPreferences.themeModeFlow

    private val _blockedCount = MutableStateFlow(blockedContactsStore.blockedIds().size)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    val discoveryPort: Int = NetworkConstants.UDP_DISCOVERY_PORT
    val controlPort: Int = NetworkConstants.TCP_MESSAGE_PORT
    val dataPort: Int = NetworkConstants.TCP_DATA_PORT

    val appVersionLabel: String = resolveVersionLabel()

    val diagnostics: StateFlow<NetworkDiagnostics> = combine(
        presenceTracker.contactPresence,
        networkDiscoveryManager.networkState,
        messagingEngine.isRunning
    ) { presences: List<ContactPresence>, lanState: LanNetworkState, running: Boolean ->
        NetworkDiagnostics(
            addresses = lanState.localAddresses,
            contactCount = presences.size,
            onlineCount = presences.count { it.isOnline },
            serviceRunning = running,
            isWifiConnected = lanState.isWifiConnected,
            lastError = lanState.lastError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NetworkDiagnostics()
    )

    fun setThemeMode(mode: AppThemeMode) {
        notificationPreferences.themeMode = mode
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationPreferences.enabled = enabled
        _notificationsEnabled.value = enabled
    }

    fun setMessagesEnabled(enabled: Boolean) {
        notificationPreferences.messagesEnabled = enabled
        _messagesEnabled.value = enabled
    }

    fun setRequestsEnabled(enabled: Boolean) {
        notificationPreferences.requestsEnabled = enabled
        _requestsEnabled.value = enabled
    }

    fun setReadReceiptsEnabled(enabled: Boolean) {
        notificationPreferences.readReceiptsEnabled = enabled
        _readReceiptsEnabled.value = enabled
    }

    fun setDiscoveryEnabled(enabled: Boolean) {
        notificationPreferences.discoveryEnabled = enabled
        _discoveryEnabled.value = enabled
        val intent = Intent(context, RadarService::class.java)
        if (enabled) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            runCatching { context.stopService(intent) }
            messagingEngine.stop()
        }
    }

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            val trimmed = newName.trim().take(30)
            if (trimmed.isNotBlank()) {
                profileRepository.updateDisplayName(trimmed)
            }
        }
    }

    fun refreshReceivedFilesSize() {
        viewModelScope.launch {
            _receivedFilesSize.value = withContext(Dispatchers.IO) {
                receivedFilesDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
    }

    fun refreshDiagnostics() {
        _blockedCount.value = blockedContactsStore.blockedIds().size
        networkDiscoveryManager.triggerRefreshBurst()
    }

    fun clearReceivedFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                receivedFilesDir().deleteRecursively()
                thumbnailsDir().deleteRecursively()
            }
            _receivedFilesSize.value = 0L
        }
    }

    fun clearLocalData(onCleared: () -> Unit) {
        viewModelScope.launch {
            clearLocalDataUseCase()
            onCleared()
        }
    }

    private fun resolveVersionLabel(): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "${info.versionName ?: "1.0.0"} (Build $code)"
        }.getOrDefault("1.0.0")
    }
}
