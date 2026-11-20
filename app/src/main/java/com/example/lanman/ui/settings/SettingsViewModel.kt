package com.example.lanman.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lanman.data.engine.RadarService
import com.example.lanman.data.notification.NotificationPreferences
import com.example.lanman.data.security.BlockedContactsStore
import com.example.lanman.domain.model.ContactPresence
import com.example.lanman.domain.model.Profile
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

/** What the diagnostics section shows about this device on the LAN. */
data class NetworkDiagnostics(
    val addresses: List<String> = emptyList(),
    val contactCount: Int = 0,
    val onlineCount: Int = 0,
    val serviceRunning: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val clearLocalDataUseCase: ClearLocalDataUseCase,
    private val notificationPreferences: NotificationPreferences,
    private val blockedContactsStore: BlockedContactsStore,
    private val presenceTracker: PresenceTracker
) : ViewModel() {

    private val _receivedFilesSize = MutableStateFlow(0L)
    val receivedFilesSize: StateFlow<Long> = _receivedFilesSize.asStateFlow()

    private fun receivedFilesDir() = File(context.filesDir, "received_files")

    val profile: StateFlow<Profile?> = profileRepository.getProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Notification switches. The master switch is ours; the permission itself is Android's.
    private val _notificationsEnabled = MutableStateFlow(notificationPreferences.enabled)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _messagesEnabled = MutableStateFlow(notificationPreferences.messagesEnabled)
    val messagesEnabled: StateFlow<Boolean> = _messagesEnabled.asStateFlow()

    private val _requestsEnabled = MutableStateFlow(notificationPreferences.requestsEnabled)
    val requestsEnabled: StateFlow<Boolean> = _requestsEnabled.asStateFlow()

    private val _blockedCount = MutableStateFlow(blockedContactsStore.blockedIds().size)
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    private val _diagnostics = MutableStateFlow(NetworkDiagnostics())
    val diagnostics: StateFlow<NetworkDiagnostics> = _diagnostics.asStateFlow()

    val discoveryPort: Int = NetworkConstants.UDP_DISCOVERY_PORT

    private val presence: StateFlow<List<ContactPresence>> = presenceTracker.contactPresence
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Presence changes every second; the address list does not, so it is refreshed on demand.
        viewModelScope.launch {
            presence.collect { presences ->
                _diagnostics.value = _diagnostics.value.copy(
                    contactCount = presences.size,
                    onlineCount = presences.count { it.isOnline }
                )
            }
        }
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

    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                profileRepository.updateDisplayName(newName.trim())
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

    /** Re-reads the things that can change while the user is away from this screen. */
    fun refreshDiagnostics() {
        viewModelScope.launch {
            val addresses = withContext(Dispatchers.IO) { localAddresses() }
            _blockedCount.value = blockedContactsStore.blockedIds().size
            _diagnostics.value = _diagnostics.value.copy(
                addresses = addresses,
                serviceRunning = isRadarServiceRunning()
            )
        }
    }

    private fun localAddresses(): List<String> {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nic -> nic.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .mapNotNull { it.hostAddress }
                .filterNot { it.startsWith("169.254.") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun isRadarServiceRunning(): Boolean {
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            manager.getRunningServices(Int.MAX_VALUE).any {
                it.service.className == RadarService::class.java.name
            }
        } catch (e: Exception) {
            false
        }
    }

    fun clearReceivedFiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                receivedFilesDir().deleteRecursively()
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
}
