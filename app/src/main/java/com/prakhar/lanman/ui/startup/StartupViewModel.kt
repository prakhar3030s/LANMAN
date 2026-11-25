package com.prakhar.lanman.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prakhar.lanman.data.notification.AppThemeMode
import com.prakhar.lanman.data.notification.NotificationPreferences
import com.prakhar.lanman.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StartupState {
    object Loading : StartupState()
    object NeedsOnboarding : StartupState()
    object Ready : StartupState()
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val notificationPreferences: NotificationPreferences
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    val themeMode: StateFlow<AppThemeMode> = notificationPreferences.themeModeFlow
    val isDiscoveryEnabled: Boolean
        get() = notificationPreferences.discoveryEnabled

    init {
        checkStartupState()
    }

    private fun checkStartupState() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = if (profileRepository.hasProfile()) {
                StartupState.Ready
            } else {
                StartupState.NeedsOnboarding
            }
        }
    }
}
